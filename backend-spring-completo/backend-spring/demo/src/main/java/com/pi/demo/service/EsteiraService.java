package com.pi.demo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pi.demo.config.InspecaoProperties;
import com.pi.demo.dto.EsteiraRequest;
import com.pi.demo.exception.ConflitoException;
import com.pi.demo.exception.OperacaoInvalidaException;
import com.pi.demo.exception.RecursoNaoEncontradoException;
import com.pi.demo.model.Esteira;
import com.pi.demo.repository.DadosProducaoRepository;
import com.pi.demo.repository.EsteiraRepository;
import com.pi.demo.repository.PecasRecusadasRepository;

import jakarta.annotation.PreDestroy;

@Service
public class EsteiraService {

    private static final Logger log = LoggerFactory.getLogger(EsteiraService.class);

    public static final String ATIVA = "ATIVA";
    public static final String PAUSADA = "PAUSADA";
    public static final String MANUTENCAO = "MANUTENCAO";
    private static final Set<String> STATUS_VALIDOS = Set.of(ATIVA, PAUSADA, MANUTENCAO);

    private final EsteiraRepository repository;
    private final DadosProducaoRepository producaoRepository;
    private final PecasRecusadasRepository pecasRepository;
    private final InspecaoProperties props;

    public EsteiraService(EsteiraRepository repository,
                          DadosProducaoRepository producaoRepository,
                          PecasRecusadasRepository pecasRepository,
                          InspecaoProperties props) {
        this.repository = repository;
        this.producaoRepository = producaoRepository;
        this.pecasRepository = pecasRepository;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<Esteira> listar() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Esteira buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esteira " + id + " nao encontrada."));
    }

    @Transactional
    public Esteira criar(EsteiraRequest req) {
        if (req.nomeIdentificador() == null || req.nomeIdentificador().isBlank()) {
            throw new OperacaoInvalidaException("nomeIdentificador e obrigatorio.");
        }
        Esteira e = new Esteira();
        e.setNomeIdentificador(req.nomeIdentificador());
        e.setVelocidadeAtual(req.velocidadeAtual() == null ? 0 : req.velocidadeAtual());
        e.setStatus(req.status() == null ? PAUSADA : validarStatus(req.status()));
        e.setDataAlteracao(LocalDateTime.now());
        return repository.save(e);
    }

    @Transactional
    public Esteira atualizar(Integer id, EsteiraRequest req) {
        Esteira e = buscar(id);
        if (req.nomeIdentificador() != null) {
            e.setNomeIdentificador(req.nomeIdentificador());
        }
        if (req.velocidadeAtual() != null) {
            e.setVelocidadeAtual(req.velocidadeAtual());
        }
        if (req.status() != null) {
            e.setStatus(validarStatus(req.status()));
        }
        e.setDataAlteracao(LocalDateTime.now());
        return repository.save(e);
    }

    @Transactional
    public void remover(Integer id) {
        Esteira e = buscar(id);
        if (producaoRepository.existsByEsteiraId(id) || pecasRepository.existsByEsteiraId(id)) {
            throw new ConflitoException("A esteira " + id + " possui historico de producao/pecas recusadas e nao pode ser removida. "
                    + "Coloque-a em MANUTENCAO.");
        }
        repository.delete(e);
    }

    /** Esteira configurada em inspecao.esteira-id; cria a linha padrao se o banco estiver vazio. */
    @Transactional
    public Esteira obterOuCriarPadrao() {
        return repository.findById(props.esteiraId()).orElseGet(() -> {
            Esteira e = new Esteira();
            e.setNomeIdentificador(props.nomeEsteiraPadrao());
            e.setVelocidadeAtual(0);
            e.setStatus(PAUSADA);
            e.setDataAlteracao(LocalDateTime.now());
            return repository.save(e);
        });
    }

    /** Sincroniza o status da esteira configurada com o estado fisico (liga/desliga). */
    @Transactional
    public void registrarStatusFisico(String status, Integer velocidade) {
        Esteira e = obterOuCriarPadrao();
        e.setStatus(validarStatus(status));
        if (velocidade != null) {
            e.setVelocidadeAtual(velocidade);
        }
        e.setDataAlteracao(LocalDateTime.now());
        repository.save(e);
    }

    /**
     * A esteira liga junto com a Raspberry Pi: assim que o backend sobe (contexto pronto),
     * marca a esteira padrao como ATIVA. Nao ha mais rele dedicado para a esteira - a
     * alimentacao fisica dela e ligada/desligada junto com a propria Raspberry Pi.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciarAplicacao() {
        try {
            registrarStatusFisico(ATIVA, null);
            log.info("Esteira marcada como ATIVA (subida da aplicacao).");
        } catch (RuntimeException e) {
            log.error("Nao foi possivel marcar a esteira como ATIVA na subida: {}", e.getMessage());
        }
    }

    /** A esteira desliga junto com a Raspberry Pi: ao encerrar a aplicacao, marca como PAUSADA. */
    @PreDestroy
    public void aoEncerrarAplicacao() {
        try {
            registrarStatusFisico(PAUSADA, null);
            log.info("Esteira marcada como PAUSADA (encerramento da aplicacao).");
        } catch (RuntimeException e) {
            log.error("Nao foi possivel marcar a esteira como PAUSADA no encerramento: {}", e.getMessage());
        }
    }

    private String validarStatus(String status) {
        String s = status.trim().toUpperCase();
        if (!STATUS_VALIDOS.contains(s)) {
            throw new OperacaoInvalidaException("Status invalido: " + status + " (use ATIVA, PAUSADA ou MANUTENCAO).");
        }
        return s;
    }
}
