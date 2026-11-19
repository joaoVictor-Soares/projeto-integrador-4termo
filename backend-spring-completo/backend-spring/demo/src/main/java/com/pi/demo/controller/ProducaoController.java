package com.pi.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pi.demo.dto.DadosProducaoDto;
import com.pi.demo.service.ProducaoService;

@RestController
@RequestMapping("/api/producao")
public class ProducaoController {

    private final ProducaoService service;

    public ProducaoController(ProducaoService service) {
        this.service = service;
    }

    /** Historico de rodadas de producao da esteira (mais recente primeiro). */
    @GetMapping("/esteira/{esteiraId}")
    public List<DadosProducaoDto> historico(@PathVariable Integer esteiraId) {
        return service.historico(esteiraId).stream().map(DadosProducaoDto::de).toList();
    }

    /** Contadores da rodada corrente. */
    @GetMapping("/esteira/{esteiraId}/atual")
    public DadosProducaoDto atual(@PathVariable Integer esteiraId) {
        return DadosProducaoDto.de(service.atual(esteiraId));
    }

    /** Abre uma nova rodada com contadores zerados. */
    @PostMapping("/esteira/{esteiraId}/iniciar")
    @ResponseStatus(HttpStatus.CREATED)
    public DadosProducaoDto iniciar(@PathVariable Integer esteiraId) {
        return DadosProducaoDto.de(service.iniciarNovaRodada(esteiraId));
    }
}
