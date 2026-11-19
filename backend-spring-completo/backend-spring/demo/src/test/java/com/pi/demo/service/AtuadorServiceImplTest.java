package com.pi.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.pi.demo.TestSupport;
import com.pi.demo.config.EspProperties;
import com.pi.demo.dto.AtuadorStatusDto;
import com.pi.demo.exception.OperacaoInvalidaException;

/**
 * Testa o AtuadorServiceImpl (dono da "variavel global" tempoAcionamentoMs) contra um
 * EspSimuladoGateway, sem precisar de hardware nem de porta serial. Os valores trocados
 * com o gateway sao sempre LOGICOS (a inversao baixo-ativa e um detalhe do firmware real,
 * fora do escopo deste teste).
 */
class AtuadorServiceImplTest {

    private static final int ENDERECO_SENSOR = TestSupport.ENDERECO_SENSOR;
    private static final int ENDERECO_CILINDRO = TestSupport.ENDERECO_CILINDRO;

    private final EspProperties props = new EspProperties(true, "SIMULADO", 115200, Duration.ofMillis(50), 80,
            ENDERECO_SENSOR, ENDERECO_CILINDRO);
    private final TestSupport.RegistradorGateway gateway = new TestSupport.RegistradorGateway();
    private final AtuadorServiceImpl atuador = new AtuadorServiceImpl(gateway, new EventoService(), props);

    @Test
    void comecaComOTempoDoApplicationProperties() {
        assertEquals(80L, atuador.tempoAcionamentoMs());
    }

    @Test
    void acionarManualAtivaEDesativaOBitDoCilindro() {
        atuador.acionarManual();
        assertTrue(gateway.escreveu(ENDERECO_CILINDRO, true), "deveria ter ativado o bit do cilindro");
        assertTrue(gateway.escreveu(ENDERECO_CILINDRO, false), "deveria ter desativado o bit ao final");

        long ativou = gateway.primeiraEscrita(ENDERECO_CILINDRO, true);
        long desativou = gateway.primeiraEscrita(ENDERECO_CILINDRO, false);
        assertTrue(desativou > ativou, "a desativacao deve vir depois do acionamento");

        AtuadorStatusDto status = atuador.status();
        assertFalse(status.atuadorAcionado(), "ao final, o atuador nao deve mais estar acionado");
    }

    @Test
    void respeitaOTempoDeAcionamentoConfigurado() {
        atuador.definirTempoAcionamentoMs(150);
        long inicio = System.nanoTime();
        atuador.acionarManual();
        long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(decorridoMs >= 150, "acionamento terminou cedo demais: " + decorridoMs + "ms");
    }

    @Test
    void rejeitaTempoForaDaFaixaPermitida() {
        assertThrows(OperacaoInvalidaException.class, () -> atuador.definirTempoAcionamentoMs(10));
        assertThrows(OperacaoInvalidaException.class, () -> atuador.definirTempoAcionamentoMs(20_000));
    }

    @Test
    void statusRefleteEstadoDoGatewaySimulado() {
        AtuadorStatusDto status = atuador.status();
        assertTrue(status.conectado());
        assertTrue(status.simulado());
        assertEquals("SIMULADO", status.porta());
        assertFalse(status.sensorAtivo());
    }
}
