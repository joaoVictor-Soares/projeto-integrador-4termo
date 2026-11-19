package com.pi.demo.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * ESP simulado em memoria (esp.simulado=true): permite rodar/testar o backend inteiro sem
 * hardware. Substitui o antigo ArduinoSimuladoGateway. O valor logico ja e o reportado por
 * lerBit/definirBit (a inversao baixo-ativa so existe no firmware real, nao faz sentido
 * simula-la aqui).
 */
@Service
@ConditionalOnProperty(prefix = "esp", name = "simulado", havingValue = "true")
public class EspSimuladoGateway implements EspGateway, EspSimuladoControl {

    private static final Logger log = LoggerFactory.getLogger(EspSimuladoGateway.class);

    private final ScheduledExecutorService agendador =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "esp-simulado");
                t.setDaemon(true);
                return t;
            });

    private final Map<Integer, Boolean> entradas = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> saidas = new ConcurrentHashMap<>();

    @Override
    public boolean conectado() {
        return true;
    }

    @Override
    public boolean simulado() {
        return true;
    }

    @Override
    public String porta() {
        return "SIMULADO";
    }

    @Override
    public boolean lerBit(int endereco) {
        return Boolean.TRUE.equals(entradas.get(endereco));
    }

    @Override
    public void escreverBit(int endereco, boolean ativar) {
        saidas.put(endereco, ativar);
        log.info("[SIMULADO] Saida bit {}: {}", endereco, ativar ? "ATIVA" : "INATIVA");
    }

    @Override
    public void definirBit(int endereco, boolean ativo) {
        entradas.put(endereco, ativo);
        log.info("[SIMULADO] Entrada bit {}: {}", endereco, ativo ? "ATIVA" : "INATIVA");
    }

    @Override
    public void pulsarBit(int endereco, Duration duracao) {
        definirBit(endereco, true);
        agendador.schedule(() -> definirBit(endereco, false), duracao.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void parar() {
        agendador.shutdownNow();
    }
}
