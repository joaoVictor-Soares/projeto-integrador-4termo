package com.pi.demo.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pi.demo.dto.PecaRecusadaDto;
import com.pi.demo.service.PecasRecusadasService;

@RestController
@RequestMapping("/api/pecas-recusadas")
public class PecasRecusadasController {

    private final PecasRecusadasService service;

    public PecasRecusadasController(PecasRecusadasService service) {
        this.service = service;
    }

    @GetMapping
    public List<PecaRecusadaDto> listar(@RequestParam(required = false) Integer esteiraId) {
        return service.listar(esteiraId).stream().map(PecaRecusadaDto::de).toList();
    }

    @GetMapping("/{id}")
    public PecaRecusadaDto buscar(@PathVariable Integer id) {
        return PecaRecusadaDto.de(service.buscar(id));
    }

    /** Foto da peca reprovada (para o App React Native). */
    @GetMapping("/{id}/imagem")
    public ResponseEntity<Resource> imagem(@PathVariable Integer id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(service.imagem(id));
    }
}
