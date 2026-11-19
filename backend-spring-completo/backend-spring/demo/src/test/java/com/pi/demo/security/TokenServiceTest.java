package com.pi.demo.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.pi.demo.TestSupport;

class TokenServiceTest {

    private static final String SEGREDO = "um-segredo-bem-longo-com-mais-de-32-chars";

    private final TokenService tokens = new TokenService(TestSupport.seguranca(SEGREDO));

    @Test
    void geraEValidaToken() {
        String token = tokens.gerar(7, "ADMIN", Instant.now().plusSeconds(60));
        UsuarioLogado u = tokens.validar(token).orElseThrow();
        assertEquals(7, u.id());
        assertEquals("ADMIN", u.perfil());
    }

    @Test
    void rejeitaTokenExpirado() {
        String token = tokens.gerar(7, "OPERADOR", Instant.now().plusSeconds(60));
        assertTrue(tokens.validar(token, Instant.now().plusSeconds(120)).isEmpty());
        assertTrue(tokens.validar(token, Instant.now()).isPresent());
    }

    @Test
    void rejeitaAssinaturaAdulterada() {
        String token = tokens.gerar(7, "OPERADOR", Instant.now().plusSeconds(60));
        String adulterado = token.substring(0, token.length() - 3) + "AAA";
        assertTrue(tokens.validar(adulterado).isEmpty());
    }

    @Test
    void rejeitaPayloadTrocadoComAssinaturaAntiga() {
        String token = tokens.gerar(7, "OPERADOR", Instant.now().plusSeconds(60));
        String assinatura = token.substring(token.indexOf('.') + 1);
        String payloadFalso = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("7|ADMIN|" + Instant.now().plusSeconds(60).getEpochSecond()).getBytes());
        assertTrue(tokens.validar(payloadFalso + "." + assinatura).isEmpty());
    }

    @Test
    void rejeitaTokenDeOutroSegredo() {
        TokenService outro = new TokenService(TestSupport.seguranca("outro-segredo-diferente-com-32-caracteres"));
        String token = outro.gerar(1, "ADMIN", Instant.now().plusSeconds(60));
        assertTrue(tokens.validar(token).isEmpty());
    }

    @Test
    void rejeitaLixo() {
        for (String lixo : new String[]{null, "", "abc", "a.b", ".", "..", "x.", ".y", "!!!.???"}) {
            assertFalse(tokens.validar(lixo).isPresent(), "deveria rejeitar: " + lixo);
        }
    }

    @Test
    void segredoCurtoNaoSobe() {
        assertThrows(IllegalStateException.class, () -> new TokenService(TestSupport.seguranca("curto")));
    }

    @Test
    void semSegredoGeraAleatorioEFunciona() {
        TokenService t = new TokenService(TestSupport.seguranca(""));
        String token = t.gerar(3, "OPERADOR", Instant.now().plusSeconds(60));
        assertTrue(t.validar(token).isPresent());
        assertTrue(tokens.validar(token).isEmpty());
    }
}
