package com.pi.demo.controller;

import java.security.Principal;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pi.demo.dto.AlterarSenhaRequest;
import com.pi.demo.dto.LoginRequest;
import com.pi.demo.dto.LoginResponse;
import com.pi.demo.dto.UsuarioDto;
import com.pi.demo.exception.CredenciaisInvalidasException;
import com.pi.demo.exception.TentativasExcedidasException;
import com.pi.demo.model.Usuario;
import com.pi.demo.security.LoginLimiter;
import com.pi.demo.security.OperadorResolver;
import com.pi.demo.security.TokenService;
import com.pi.demo.security.UsuarioLogado;
import com.pi.demo.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarios;
    private final TokenService tokens;
    private final LoginLimiter limiter;

    public AuthController(UsuarioService usuarios, TokenService tokens, LoginLimiter limiter) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        String chave = http.getRemoteAddr() + "|" + email;
        if (limiter.bloqueado(chave)) {
            throw new TentativasExcedidasException("Muitas tentativas de login. Aguarde alguns minutos.");
        }
        Usuario u;
        try {
            u = usuarios.autenticar(email, req.senha());
        } catch (CredenciaisInvalidasException e) {
            limiter.registrarFalha(chave);
            throw e;
        }
        limiter.registrarSucesso(chave);
        Instant expira = tokens.expiracaoDeAgora();
        return new LoginResponse(tokens.gerar(u.getId(), u.getPerfil(), expira), "Bearer", expira, UsuarioDto.de(u));
    }

    /** Dados do usuario do token atual. */
    @GetMapping("/eu")
    public UsuarioDto eu(Principal principal) {
        UsuarioLogado logado = OperadorResolver.logado(principal);
        if (logado == null) {
            throw new CredenciaisInvalidasException("Nao autenticado.");
        }
        return new UsuarioDto(logado.id(), logado.nome(), logado.email(), logado.perfil());
    }

    @PostMapping("/senha")
    public void alterarSenha(@RequestBody AlterarSenhaRequest req, Principal principal) {
        UsuarioLogado logado = OperadorResolver.logado(principal);
        if (logado == null) {
            throw new CredenciaisInvalidasException("Nao autenticado.");
        }
        usuarios.alterarSenha(logado.id(), req.senhaAtual(), req.novaSenha());
    }
}
