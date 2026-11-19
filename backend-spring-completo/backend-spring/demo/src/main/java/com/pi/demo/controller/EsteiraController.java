package com.pi.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pi.demo.dto.EsteiraDto;
import com.pi.demo.dto.EsteiraRequest;
import com.pi.demo.service.EsteiraService;

@RestController
@RequestMapping("/api/esteiras")
public class EsteiraController {

    private final EsteiraService service;

    public EsteiraController(EsteiraService service) {
        this.service = service;
    }

    @GetMapping
    public List<EsteiraDto> listar() {
        return service.listar().stream().map(EsteiraDto::de).toList();
    }

    @GetMapping("/{id}")
    public EsteiraDto buscar(@PathVariable Integer id) {
        return EsteiraDto.de(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EsteiraDto criar(@RequestBody EsteiraRequest req) {
        return EsteiraDto.de(service.criar(req));
    }

    @PatchMapping("/{id}")
    public EsteiraDto atualizar(@PathVariable Integer id, @RequestBody EsteiraRequest req) {
        return EsteiraDto.de(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Integer id) {
        service.remover(id);
    }
}
