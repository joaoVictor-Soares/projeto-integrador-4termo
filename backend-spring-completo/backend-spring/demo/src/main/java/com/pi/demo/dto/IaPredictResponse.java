package com.pi.demo.dto;

import java.util.Map;

/** Espelha PredictResponse de ia-modelo/main.py. confianca de 0 a 100. */
public record IaPredictResponse(
        String status,
        double confianca,
        Map<String, Double> probabilidades,
        String acao) {

    public boolean aprovada() {
        return "aprovadas".equalsIgnoreCase(status);
    }
}
