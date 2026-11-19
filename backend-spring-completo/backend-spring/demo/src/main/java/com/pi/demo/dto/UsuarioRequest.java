package com.pi.demo.dto;

/** POST: nome, email, senha e perfil obrigatorios. PATCH: campos nulos sao ignorados. */
public record UsuarioRequest(String nome, String email, String senha, String perfil) {
}
