package com.pi.demo.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pi.demo.config.EspProperties;
import com.pi.demo.config.InspecaoProperties;

/**
 * Varre o sensor capacitivo (lido pelo ESP, endereco esp.endereco-sensor-capacitivo) em
 * intervalo fixo e, na borda de subida (livre -&gt; detectado), enfileira uma inspecao.
 *
 * Desative com inspecao.polling-habilitado=false.
 */
@Service
@ConditionalOnProperty(prefix = "inspecao", name = "polling-habilitado", havingValue = "true", matchIfMissing = true)
public class SensorPollingService {

    private static final Logger log = LoggerFactory.getLogger(SensorPollingService.class);
    private static final long LOG_FALHA_A_CADA_MS = 10_000;

    private final EspGateway gateway;
    private final InspecaoOrchestratorService orquestrador;
    private final EventoService eventos;
    private final InspecaoProperties props;
    private final int enderecoSensor;

    private boolean sensorAnterior;
    private Boolean conectadoAnterior; // null = ainda nao se sabe
    private long ultimoGatilhoNs;
    private long ultimoLogFalhaMs;

    public SensorPollingService(EspGateway gateway,
                                InspecaoOrchestratorService orquestrador,
                                EventoService eventos,
                                InspecaoProperties props,
                                EspProperties espProps) {
        this.gateway = gateway;
        this.orquestrador = orquestrador;
        this.eventos = eventos;
        this.props = props;
        this.enderecoSensor = espProps.enderecoSensorCapacitivo();
    }

    @Scheduled(fixedDelayString = "${inspecao.intervalo-polling:PT0.1S}")
    public void varrer() {
        if (!gateway.conectado()) {
            sensorAnterior = false; // apos reconectar, nao interpretar o 1o ciclo como borda
            if (!Boolean.FALSE.equals(conectadoAnterior)) {
                conectadoAnterior = false;
                eventos.publicar("ESP_OFFLINE", EventoService.ERRO, "ESP inacessivel (porta " + gateway.porta() + ").");
            }
            logFalhaLimitado();
            return;
        }
        if (Boolean.FALSE.equals(conectadoAnterior)) {
            eventos.publicar("ESP_ONLINE", EventoService.INFO, "Comunicacao com o ESP restabelecida.");
        }
        conectadoAnterior = true;

        boolean sensorAtual = gateway.lerBit(enderecoSensor);
        if (sensorAtual && !sensorAnterior && passouDebounce()) {
            log.info("Sensor capacitivo detectado: iniciando inspecao.");
            orquestrador.enfileirar("ESP:SENSOR_CAPACITIVO");
        }
        sensorAnterior = sensorAtual;
    }

    private boolean passouDebounce() {
        long agora = System.nanoTime();
        if (ultimoGatilhoNs != 0 && Duration.ofNanos(agora - ultimoGatilhoNs).compareTo(props.debounce()) < 0) {
            return false;
        }
        ultimoGatilhoNs = agora;
        return true;
    }

    private void logFalhaLimitado() {
        long agora = System.currentTimeMillis();
        if (agora - ultimoLogFalhaMs >= LOG_FALHA_A_CADA_MS) {
            ultimoLogFalhaMs = agora;
            log.warn("ESP indisponivel (porta {}).", gateway.porta());
        }
    }
}
