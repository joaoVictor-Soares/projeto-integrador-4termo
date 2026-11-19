package com.pi.demo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ia")
public record IaProperties(
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("3s") Duration timeoutConexao,
        @DefaultValue("15s") Duration timeoutLeitura) {
}
