package com.pi.demo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.pi.demo.service.UsuarioService;

/** Na primeira subida (tabela de usuarios vazia) cria o administrador inicial. */
@Component
public class AdminInicializador implements ApplicationRunner {

    private final UsuarioService usuarios;

    public AdminInicializador(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public void run(ApplicationArguments args) {
        usuarios.garantirAdminInicial();
    }
}
