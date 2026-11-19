package com.pi.demo.dto;

import com.pi.demo.model.Usuario;

/** Nunca expoe o hash da senha. */
public record UsuarioDto(Integer id, String nome, String email, String perfil) {

    public static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNome(), u.getEmail(), u.getPerfil());
    }
}
