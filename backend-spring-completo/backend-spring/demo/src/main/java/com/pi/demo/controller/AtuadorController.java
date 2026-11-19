package com.pi.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pi.demo.dto.AtuadorConfigDto;
import com.pi.demo.dto.AtuadorStatusDto;
import com.pi.demo.exception.OperacaoInvalidaException;
import com.pi.demo.service.AtuadorService;

/**
 * Estado e comandos do ESP (sensor capacitivo + atuador de rejeicao via rele).
 * Substitui o antigo MesaController da epoca do CLP S7: nao ha mais esteira, LEDs, emergencia,
 * multiplos cilindros ou controle de velocidade — a bancada agora so tem essas duas pontas de I/O.
 */
@RestController
@RequestMapping("/api/atuador")
public class AtuadorController {

    /** Limites de sanidade para o tempo de acionamento (evita travar o atuador ligado por engano). */
    private static final long TEMPO_MIN_MS = 50;
    private static final long TEMPO_MAX_MS = 10_000;

    private final AtuadorService atuador;

    public AtuadorController(AtuadorService atuador) {
        this.atuador = atuador;
    }

    @GetMapping("/status")
    public AtuadorStatusDto status() {
        return atuador.status();
    }

    /**
     * Aciona o atuador manualmente (fora do fluxo de inspecao), usando o tempo configurado em
     * {@code tempoAcionamentoMs}. Sincrono: so retorna depois que o rele desliga de novo.
     */
    @PostMapping("/acionar")
    public AtuadorStatusDto acionar() {
        atuador.acionarManual();
        return atuador.status();
    }

    @GetMapping("/configuracao")
    public AtuadorConfigDto configuracao() {
        return new AtuadorConfigDto(atuador.tempoAcionamentoMs());
    }

    /**
     * Altera em tempo real a duracao do ciclo avanca+recua do atuador (a "variavel global" do
     * temporizador). Util para calibrar o curso do cilindro na bancada sem reiniciar o backend.
     */
    @PutMapping("/configuracao")
    public AtuadorConfigDto definirConfiguracao(@RequestBody AtuadorConfigDto req) {
        if (req.tempoAcionamentoMs() < TEMPO_MIN_MS || req.tempoAcionamentoMs() > TEMPO_MAX_MS) {
            throw new OperacaoInvalidaException(
                    "tempoAcionamentoMs deve estar entre " + TEMPO_MIN_MS + " e " + TEMPO_MAX_MS + " ms.");
        }
        atuador.definirTempoAcionamentoMs(req.tempoAcionamentoMs());
        return configuracao();
    }
}
