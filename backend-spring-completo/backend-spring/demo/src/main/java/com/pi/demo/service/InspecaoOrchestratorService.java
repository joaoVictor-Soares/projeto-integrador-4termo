package com.pi.demo.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi.demo.client.PythonMlClient;
import com.pi.demo.config.InspecaoProperties;
import com.pi.demo.dto.IaPredictResponse;
import com.pi.demo.dto.InspecaoStatusDto;
import com.pi.demo.dto.ResultadoInspecaoDto;
import com.pi.demo.exception.OperacaoInvalidaException;
import com.pi.demo.model.Esteira;
import com.pi.demo.model.PecasRecusadas;

/**
 * Orquestra o ciclo: ESP (sensor) -&gt; camera -&gt; IA (main.py) -&gt; banco -&gt; ESP (rejeita a peca).
 *
 * Como ha apenas UM atuador (o cilindro de rejeicao), so ha acao fisica quando a peca e
 * reprovada; uma peca aprovada simplesmente segue na esteira sem nenhum comando ao ESP.
 *
 * - Captura + inferencia rodam em fila FIFO de uma thread (inspecaoExecutor).
 * - O acionamento do cilindro e agendado para {@code inspecao.atraso-atuacao} DEPOIS DO GATILHO do sensor
 *   (o tempo gasto em camera + IA e descontado), em outra thread (atuacaoExecutor).
 * - Atuacoes sao serializadas pelo proprio AtuadorService (lock interno): duas pecas muito
 *   proximas esperam o ciclo anterior (avanco + retorno do cilindro) terminar.
 */
@Service
public class InspecaoOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(InspecaoOrchestratorService.class);

    private final CameraService camera;
    private final PythonMlClient ia;
    private final AtuadorService atuador;
    private final EsteiraService esteiraService;
    private final ProducaoService producaoService;
    private final PecasRecusadasService pecasService;
    private final EventoService eventos;
    private final InspecaoProperties props;
    private final ExecutorService inspecaoExecutor;
    private final ScheduledExecutorService atuacaoExecutor;

    private final AtomicInteger pendentes = new AtomicInteger();
    private final AtomicLong total = new AtomicLong();
    private final AtomicReference<ResultadoInspecaoDto> ultimo = new AtomicReference<>();

    public InspecaoOrchestratorService(CameraService camera,
                                       PythonMlClient ia,
                                       AtuadorService atuador,
                                       EsteiraService esteiraService,
                                       ProducaoService producaoService,
                                       PecasRecusadasService pecasService,
                                       EventoService eventos,
                                       InspecaoProperties props,
                                       ExecutorService inspecaoExecutor,
                                       ScheduledExecutorService atuacaoExecutor) {
        this.camera = camera;
        this.ia = ia;
        this.atuador = atuador;
        this.esteiraService = esteiraService;
        this.producaoService = producaoService;
        this.pecasService = pecasService;
        this.eventos = eventos;
        this.props = props;
        this.inspecaoExecutor = inspecaoExecutor;
        this.atuacaoExecutor = atuacaoExecutor;
    }

    // ------------------------------------------------------------------
    // Entradas do fluxo
    // ------------------------------------------------------------------

    /** Gatilho vindo do ESP (nao bloqueia o polling): enfileira a inspecao. */
    public void enfileirar(String origem) {
        final long gatilhoNs = System.nanoTime();
        if (pendentes.incrementAndGet() > props.filaMaxima()) {
            pendentes.decrementAndGet();
            String msg = "Fila de inspecao cheia (" + props.filaMaxima() + "): gatilho '" + origem + "' descartado.";
            log.warn(msg);
            eventos.publicar("FILA_CHEIA", EventoService.AVISO, msg);
            return;
        }
        try {
            inspecaoExecutor.submit(() -> {
                try {
                    inspecionar(origem, true, null, null, gatilhoNs);
                } catch (RuntimeException e) {
                    log.error("Inspecao '{}' falhou: {}", origem, e.getMessage(), e);
                } finally {
                    pendentes.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            pendentes.decrementAndGet();
            log.warn("Executor de inspecao encerrado; gatilho '{}' ignorado.", origem);
        }
    }

    /** Disparo manual (captura da camera). Sincrono: devolve o resultado. */
    public ResultadoInspecaoDto inspecionarAgora(boolean atuar, Integer operadorId) {
        return executarSincrono("MANUAL", atuar, operadorId, null);
    }

    /** Analisa uma imagem enviada (sem camera). Util para testes e para o app. */
    public ResultadoInspecaoDto inspecionarImagem(byte[] imagem, boolean atuar, Integer operadorId) {
        if (imagem == null || imagem.length == 0) {
            throw new OperacaoInvalidaException("Imagem vazia.");
        }
        return executarSincrono("UPLOAD", atuar, operadorId, imagem);
    }

    private ResultadoInspecaoDto executarSincrono(String origem, boolean atuar, Integer operadorId, byte[] imagem) {
        final long gatilhoNs = System.nanoTime();
        // passa pela mesma fila para nao concorrer com a webcam/gatilhos do sensor
        pendentes.incrementAndGet();
        try {
            return inspecaoExecutor.submit(() -> inspecionar(origem, atuar, operadorId, imagem, gatilhoNs)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OperacaoInvalidaException("Inspecao interrompida.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (RejectedExecutionException e) {
            throw new OperacaoInvalidaException("Servico de inspecao indisponivel (encerrando).");
        } finally {
            pendentes.decrementAndGet();
        }
    }

    // ------------------------------------------------------------------
    // Pipeline
    // ------------------------------------------------------------------

    private ResultadoInspecaoDto inspecionar(String origem, boolean atuar, Integer operadorId,
                                             byte[] imagemRecebida, long gatilhoNs) {
        LocalDateTime agora = LocalDateTime.now();
        byte[] imagem = imagemRecebida;
        IaPredictResponse previsao = null;
        String erro = null;

        try {
            if (imagem == null) {
                imagem = camera.capturar();
            }
            previsao = ia.prever(imagem);
        } catch (RuntimeException e) {
            erro = e.getMessage();
            log.error("Falha na captura/IA ({}): {}", origem, erro);
        }

        ResultadoInspecaoDto resultado = previsao != null
                ? concluir(origem, agora, previsao, imagem, atuar, operadorId, gatilhoNs)
                : tratarFalha(origem, agora, erro, imagem, atuar, operadorId, gatilhoNs);

        total.incrementAndGet();
        ultimo.set(resultado);
        publicarResultado(resultado);
        return resultado;
    }

    private ResultadoInspecaoDto concluir(String origem, LocalDateTime agora, IaPredictResponse previsao,
                                          byte[] imagem, boolean atuar, Integer operadorId, long gatilhoNs) {
        boolean aprovada = previsao.aprovada();
        Integer pecaId = null;
        String erro = null;

        if (atuar) {
            try {
                pecaId = persistir(previsao, imagem, operadorId);
            } catch (RuntimeException e) {
                // falha de banco nao pode impedir o desvio fisico da peca
                erro = "Falha ao registrar no banco: " + e.getMessage();
                log.error(erro, e);
            }
            if (!aprovada) {
                agendarRejeicao(gatilhoNs);
            }
        }

        return new ResultadoInspecaoDto(origem, agora, previsao.status(), previsao.confianca(),
                previsao.probabilidades(), previsao.acao(), atuar, pecaId, erro);
    }

    /**
     * Camera/IA indisponivel. Fail-safe (inspecao.falha-reprova): a peca nao verificada e desviada
     * como recusada, registrada e contada, para nunca liberar peca sem verificacao.
     */
    private ResultadoInspecaoDto tratarFalha(String origem, LocalDateTime agora, String erro, byte[] imagem,
                                             boolean atuar, Integer operadorId, long gatilhoNs) {
        boolean agendou = false;
        Integer pecaId = null;
        if (atuar && props.falhaReprova()) {
            try {
                Esteira esteira = esteiraService.obterOuCriarPadrao();
                producaoService.contabilizar(esteira, false);
                pecaId = pecasService.registrarFalha(esteira, "Falha na verificacao: " + erro, imagem, operadorId).getId();
            } catch (RuntimeException e) {
                log.error("Falha ao registrar peca nao verificada: {}", e.getMessage(), e);
            }
            agendarRejeicao(gatilhoNs);
            agendou = true;
        }
        return new ResultadoInspecaoDto(origem, agora, "erro", null, null, null, agendou, pecaId, erro);
    }

    private Integer persistir(IaPredictResponse previsao, byte[] imagem, Integer operadorId) {
        Esteira esteira = esteiraService.obterOuCriarPadrao();
        producaoService.contabilizar(esteira, previsao.aprovada());
        if (previsao.aprovada()) {
            return null;
        }
        PecasRecusadas peca = pecasService.registrar(esteira, previsao, imagem, operadorId);
        return peca.getId();
    }

    private void publicarResultado(ResultadoInspecaoDto r) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("origem", r.origem());
        dados.put("status", r.status());
        dados.put("confianca", r.confianca());
        dados.put("pecaRecusadaId", r.pecaRecusadaId());
        switch (r.status()) {
            case "aprovadas" -> eventos.publicar("PECA_APROVADA", EventoService.INFO,
                    "Peca aprovada (" + r.confianca() + "%).", dados);
            case "reprovadas" -> eventos.publicar("PECA_RECUSADA", EventoService.AVISO,
                    "Peca reprovada (" + r.confianca() + "%).", dados);
            default -> eventos.publicar("INSPECAO_ERRO", EventoService.ERRO,
                    "Falha na inspecao: " + r.erro(), dados);
        }
    }

    // ------------------------------------------------------------------
    // Atuacao no ESP (unico cilindro: rejeicao)
    // ------------------------------------------------------------------

    private void agendarRejeicao(long gatilhoNs) {
        long decorridoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - gatilhoNs);
        long atrasoMs = Math.max(0, props.atrasoAtuacao().toMillis() - decorridoMs);
        try {
            atuacaoExecutor.schedule(this::rejeitar, atrasoMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.warn("Executor de atuacao encerrado; rejeicao descartada.");
        }
    }

    private void rejeitar() {
        try {
            atuador.acionarManual();
        } catch (RuntimeException e) {
            log.error("Falha ao acionar o atuador para rejeitar a peca: {}", e.getMessage());
            eventos.publicar("FALHA_ATUACAO", EventoService.ERRO, "Falha ao acionar o atuador: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    public InspecaoStatusDto status() {
        return new InspecaoStatusDto(props.pollingHabilitado(), ia.saudavel(), pendentes.get(),
                total.get(), ultimo.get());
    }

    public ResultadoInspecaoDto ultimoResultado() {
        return ultimo.get();
    }
}
