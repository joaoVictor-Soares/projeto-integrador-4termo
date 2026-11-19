package com.pi.demo.security;

/** Principal da requisicao autenticada. */
public record UsuarioLogado(Integer id, String perfil, String email, String nome) {

    public boolean admin() {
        return "ADMIN".equals(perfil);
    }
}
