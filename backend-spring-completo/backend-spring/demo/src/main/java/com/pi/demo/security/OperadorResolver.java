package com.pi.demo.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;

/** Descobre o usuario autenticado de uma requisicao (nulo quando a seguranca esta desligada). */
public final class OperadorResolver {

    private OperadorResolver() {
    }

    public static UsuarioLogado logado(Principal principal) {
        if (principal instanceof Authentication a && a.getPrincipal() instanceof UsuarioLogado u) {
            return u;
        }
        return null;
    }

    /** Usa o id informado na requisicao; se nao houver, o do usuario logado. */
    public static Integer operadorId(Principal principal, Integer informado) {
        if (informado != null) {
            return informado;
        }
        UsuarioLogado u = logado(principal);
        return u == null ? null : u.id();
    }
}
