package com.pi.demo.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi.demo.config.EspProperties;
import com.pi.demo.dto.AtuadorStatusDto;
import com.pi.demo.exception.EspException;
import com.pi.demo.exception.OperacaoInvalidaException;

/**
 * Dono da "variavel global" de tempo de acionamento do cilindro (requisito do usuario:
 * acionamento e retorno controlados por temporizador no backend, facil de alterar).
 *
 * Fala com o ESP por enderecamento de bits (props.enderecoCilindroRejeicao() /
 * props.enderecoSensorCapacitivo()); o "ativar"/"desativar" aqui e sempre LOGICO - a
 * conversao para o nivel fisico baixo-ativo do ESP acontece dentro do proprio firmware.
 *
 * acionarManual() e SINCRONO: ativa a saida, aguarda tempoAcionamentoMs, desativa a saida
 * e so entao retorna. E chamado tanto pelo endpoint manual (POST /api/atuador/acionar) quanto
 * pela orquestracao automatica (InspecaoOrchestratorService), sempre dentro de uma thread de
 * fundo (atuacaoExecutor) para nao bloquear quem chamou.
 */
@Service
public class AtuadorServiceImpl implements AtuadorService {

    private static final Logger log = LoggerFactory.getLogger(AtuadorServiceImpl.class);

    public static final long TEMPO_MIN_MS = 50;
    public static final long TEMPO_MAX_MS = 10_000;

    private final EspGateway gateway;
    private final EventoService eventos;
    private final int enderecoSensor;
    private final int enderecoCilindro;

    /** Variavel global: tempo (ms) que o cilindro fica avancado antes do retorno automatico. */
    private volatile long tempoAcionamentoMs;

    private volatile boolean atuadorAcionado;
    private final Object acionamentoLock = new Object();

    public AtuadorServiceImpl(EspGateway gateway, EventoService eventos, EspProperties props) {
        this.gateway = gateway;
        this.eventos = eventos;
        this.enderecoSensor = props.enderecoSensorCapacitivo();
        this.enderecoCilindro = props.enderecoCilindroRejeicao();
        this.tempoAcionamentoMs = clamp(props.tempoAcionamentoInicialMs());
    }

    @Override
    public AtuadorStatusDto status() {
        return new AtuadorStatusDto(
                gateway.conectado(),
                gateway.simulado(),
                gateway.porta(),
                gateway.lerBit(enderecoSensor),
                atuadorAcionado,
                tempoAcionamentoMs,
                LocalDateTime.now());
    }

    @Override
    public void acionarManual() {
        synchronized (acionamentoLock) {
            try {
                atuadorAcionado = true;
                gateway.escreverBit(enderecoCilindro, true);
                eventos.publicar("ATUADOR_ACIONADO", EventoService.INFO,
                        "Cilindro acionado por " + tempoAcionamentoMs + "ms.");
                Thread.sleep(tempoAcionamentoMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Acionamento do atuador interrompido antes do tempo total.");
            } catch (EspException e) {
                log.error("Falha ao acionar o atuador: {}", e.getMessage());
                eventos.publicar("ATUADOR_FALHA", EventoService.ERRO, e.getMessage());
                throw e;
            } finally {
                try {
                    gateway.escreverBit(enderecoCilindro, false);
                } catch (EspException e) {
                    log.error("Falha ao desligar a saida do atuador: {}", e.getMessage());
                } finally {
                    atuadorAcionado = false;
                    eventos.publicar("ATUADOR_DESLIGADO", EventoService.INFO, "Cilindro retornado.");
                }
            }
        }
    }

    @Override
    public long tempoAcionamentoMs() {
        return tempoAcionamentoMs;
    }

    @Override
    public void definirTempoAcionamentoMs(long ms) {
        if (ms < TEMPO_MIN_MS || ms > TEMPO_MAX_MS) {
            throw new OperacaoInvalidaException(
                    "tempoAcionamentoMs deve estar entre " + TEMPO_MIN_MS + " e " + TEMPO_MAX_MS + "ms.");
        }
        this.tempoAcionamentoMs = ms;
        log.info("Tempo de acionamento do atuador alterado para {}ms.", ms);
        eventos.publicar("ATUADOR_CONFIG_ALTERADA", EventoService.INFO,
                "Novo tempo de acionamento: " + ms + "ms.");
    }

    private long clamp(long ms) {
        if (ms < TEMPO_MIN_MS || ms > TEMPO_MAX_MS) {
            log.warn("esp.tempo-acionamento-inicial-ms={} fora da faixa [{},{}]; usando o limite mais proximo.",
                    ms, TEMPO_MIN_MS, TEMPO_MAX_MS);
            return Math.max(TEMPO_MIN_MS, Math.min(TEMPO_MAX_MS, ms));
        }
        return ms;
    }
}
