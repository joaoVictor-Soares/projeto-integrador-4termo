package com.pi.demo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracao da orquestracao ESP (sensor) -&gt; camera -&gt; IA -&gt; ESP (cilindro).
 * Campos de botoeira/rampa/segundo-cilindro foram removidos: o novo hardware tem apenas
 * um sensor (capacitivo) e um atuador (cilindro de rejeicao), cujo tempo de acionamento
 * e a "variavel global" controlada por AtuadorService (nao por este record).
 */
@ConfigurationProperties(prefix = "inspecao")
public record InspecaoProperties(
        @DefaultValue("true") boolean pollingHabilitado,
        @DefaultValue("PT0.1S") Duration intervaloPolling,
        @DefaultValue("500ms") Duration debounce,
        /** Tempo entre o SENSOR detectar a peca e o cilindro atuar (contado a partir do gatilho). */
        @DefaultValue("4s") Duration atrasoAtuacao,
        @DefaultValue("true") boolean falhaReprova,
        @DefaultValue("1") int esteiraId,
        @DefaultValue("Linha Principal 01") String nomeEsteiraPadrao,
        @DefaultValue("./storage/recusadas") String pastaImagens,
        @DefaultValue("10") int filaMaxima) {
}
