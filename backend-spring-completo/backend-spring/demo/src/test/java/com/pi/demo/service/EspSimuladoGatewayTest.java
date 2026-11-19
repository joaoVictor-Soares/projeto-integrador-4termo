package com.pi.demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.pi.demo.TestSupport;

class EspSimuladoGatewayTest {

    private final EspSimuladoGateway gateway = new EspSimuladoGateway();

    @Test
    void comecaDesconectadoDaEntradaMasConectadoAoEsp() {
        assertTrue(gateway.conectado());
        assertTrue(gateway.simulado());
        assertFalse(gateway.lerBit(TestSupport.ENDERECO_SENSOR));
    }

    @Test
    void definirBitAtualizaOEstadoImediatamente() {
        gateway.definirBit(TestSupport.ENDERECO_SENSOR, true);
        assertTrue(gateway.lerBit(TestSupport.ENDERECO_SENSOR));
        gateway.definirBit(TestSupport.ENDERECO_SENSOR, false);
        assertFalse(gateway.lerBit(TestSupport.ENDERECO_SENSOR));
    }

    @Test
    void pulsarBitAtivaEDepoisDesativaSozinho() {
        gateway.pulsarBit(TestSupport.ENDERECO_SENSOR, Duration.ofMillis(60));
        assertTrue(gateway.lerBit(TestSupport.ENDERECO_SENSOR));
        assertTrue(TestSupport.aguardar(() -> !gateway.lerBit(TestSupport.ENDERECO_SENSOR), 1000));
    }

    @Test
    void enderecosDiferentesSaoIndependentes() {
        gateway.definirBit(0, true);
        gateway.definirBit(1, false);
        assertTrue(gateway.lerBit(0));
        assertFalse(gateway.lerBit(1));
    }
}
