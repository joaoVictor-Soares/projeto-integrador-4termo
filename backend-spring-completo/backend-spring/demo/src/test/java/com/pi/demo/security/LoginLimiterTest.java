package com.pi.demo.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.pi.demo.TestSupport;

class LoginLimiterTest {

    private final AtomicLong agora = new AtomicLong(1_000_000);
    private final LoginLimiter limiter = new LoginLimiter(TestSupport.seguranca("x"));

    LoginLimiterTest() {
        limiter.usarRelogio(agora::get);
    }

    @Test
    void bloqueiaAposOMaximoDeFalhas() {
        assertFalse(limiter.bloqueado("ip|a"));
        limiter.registrarFalha("ip|a");
        limiter.registrarFalha("ip|a");
        assertFalse(limiter.bloqueado("ip|a"));
        limiter.registrarFalha("ip|a"); // maximo = 3
        assertTrue(limiter.bloqueado("ip|a"));
        assertFalse(limiter.bloqueado("ip|b"));
    }

    @Test
    void liberaDepoisDaJanela() {
        for (int i = 0; i < 3; i++) {
            limiter.registrarFalha("k");
        }
        assertTrue(limiter.bloqueado("k"));
        agora.addAndGet(5 * 60_000L + 1);
        assertFalse(limiter.bloqueado("k"));
    }

    @Test
    void sucessoZeraAsFalhas() {
        limiter.registrarFalha("k");
        limiter.registrarFalha("k");
        limiter.registrarSucesso("k");
        limiter.registrarFalha("k");
        assertFalse(limiter.bloqueado("k"));
    }
}
