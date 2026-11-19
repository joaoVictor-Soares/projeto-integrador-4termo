package com.pi.demo.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pi.demo.repository.UsuarioRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le "Authorization: Bearer <token>" e autentica a requisicao. O usuario e relido do banco a cada
 * requisicao: perfil alterado ou usuario removido tem efeito imediato.
 * O fluxo SSE (/api/eventos/stream) tambem aceita ?token=, pois o EventSource nao envia cabecalhos.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TokenService tokens;
    private final UsuarioRepository usuarios;

    public TokenAuthFilter(TokenService tokens, UsuarioRepository usuarios) {
        this.tokens = tokens;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extrair(request);
        if (token != null) {
            tokens.validar(token)
                    .flatMap(t -> usuarios.findById(t.id()))
                    .ifPresent(u -> {
                        UsuarioLogado principal = new UsuarioLogado(u.getId(), u.getPerfil(), u.getEmail(), u.getNome());
                        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + u.getPerfil())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    private String extrair(HttpServletRequest request) {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho != null && cabecalho.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return cabecalho.substring(7).trim();
        }
        if (request.getRequestURI().endsWith("/api/eventos/stream")) {
            return request.getParameter("token");
        }
        return null;
    }
}
