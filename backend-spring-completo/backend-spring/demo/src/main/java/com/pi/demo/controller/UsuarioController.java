package com.pi.demo.controller;

import java.security.Principal;
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

import com.pi.demo.dto.UsuarioDto;
import com.pi.demo.dto.UsuarioRequest;
import com.pi.demo.security.OperadorResolver;
import com.pi.demo.security.UsuarioLogado;
import com.pi.demo.service.UsuarioService;

/** Gestao de usuarios (somente ADMIN). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioDto> listar() {
        return service.listar().stream().map(UsuarioDto::de).toList();
    }

    @GetMapping("/{id}")
    public UsuarioDto buscar(@PathVariable Integer id) {
        return UsuarioDto.de(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDto criar(@RequestBody UsuarioRequest req) {
        return UsuarioDto.de(service.criar(req));
    }

    @PatchMapping("/{id}")
    public UsuarioDto atualizar(@PathVariable Integer id, @RequestBody UsuarioRequest req) {
        return UsuarioDto.de(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Integer id, Principal principal) {
        UsuarioLogado logado = OperadorResolver.logado(principal);
        service.remover(id, logado == null ? null : logado.id());
    }
}
