package com.pi.demo.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * - inspecaoExecutor: captura + IA, uma peca por vez (FIFO).
 * - atuacaoExecutor: aciona cilindros/LEDs no CLP apos o atraso, serializado
 *   para que dois cilindros nunca sejam comandados ao mesmo tempo.
 */
@Configuration
@EnableScheduling
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService inspecaoExecutor() {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, "inspecao-worker"));
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService atuacaoExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "atuacao-worker"));
    }
}
