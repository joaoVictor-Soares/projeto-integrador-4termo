package com.pi.demo.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pi.demo.config.EspProperties;
import com.pi.demo.service.EspSimuladoControl;

/**
 * Controle do ESP simulado (esp.simulado=true): so existe em desenvolvimento/testes, nunca
 * com o ESP fisico conectado. Permite acionar o sensor capacitivo (endereco de bit
 * esp.endereco-sensor-capacitivo) como se uma peca estivesse passando, para testar o
 * pipeline completo (sensor -> inspecao -> IA -> atuador) sem hardware.
 */
@RestController
@RequestMapping("/api/simulacao")
@ConditionalOnProperty(prefix = "esp", name = "simulado", havingValue = "true")
public class SimulacaoController {

    private final EspSimuladoControl simulado;
    private final int enderecoSensor;

    public SimulacaoController(EspSimuladoControl simulado, EspProperties props) {
        this.simulado = simulado;
        this.enderecoSensor = props.enderecoSensorCapacitivo();
    }

    /** Liga/desliga o sensor capacitivo "no bruto" (fica no estado ate ser alterado de novo). */
    @PostMapping("/sensor")
    public Map<String, Object> definirSensor(@RequestParam boolean ativo) {
        simulado.definirBit(enderecoSensor, ativo);
        return Map.of("sensorAtivo", ativo);
    }

    /** Liga o sensor por {@code ms} milissegundos e solta sozinho (simula uma peca passando). */
    @PostMapping("/sensor/pulso")
    public Map<String, Object> pulsarSensor(@RequestParam(defaultValue = "300") long ms) {
        long duracao = Math.min(Math.max(ms, 10), 10_000);
        simulado.pulsarBit(enderecoSensor, Duration.ofMillis(duracao));
        return Map.of("pulsoMs", duracao);
    }
}
