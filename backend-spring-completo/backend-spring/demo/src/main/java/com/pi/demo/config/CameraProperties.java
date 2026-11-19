package com.pi.demo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "camera")
public record CameraProperties(
        @DefaultValue("/dev/video0") String dispositivo,
        @DefaultValue("ffmpeg") String ffmpeg,
        @DefaultValue("640x480") String resolucao,
        @DefaultValue("5") int framesDescartados,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("") String imagemSimulada) {

    public boolean usaImagemSimulada() {
        return imagemSimulada != null && !imagemSimulada.isBlank();
    }
}
