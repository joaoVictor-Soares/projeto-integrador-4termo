package com.pi.demo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "seguranca")
public record SegurancaProperties(
        /** false = API aberta (somente para desenvolvimento em rede isolada). */
        @DefaultValue("true") boolean habilitada,
        /** Segredo HMAC dos tokens. Vazio = gerado a cada subida (tokens caem ao reiniciar). Minimo 32 caracteres. */
        @DefaultValue("") String segredo,
        @DefaultValue("8h") Duration validadeToken,
        @DefaultValue("admin@local") String adminEmail,
        /** Vazio = senha aleatoria gerada e escrita no log na primeira subida (so se nao existir nenhum usuario). */
        @DefaultValue("") String adminSenha,
        @DefaultValue("Administrador") String adminNome,
        @DefaultValue("5") int loginTentativasMax,
        @DefaultValue("5m") Duration loginJanela) {
}
