package com.pi.demo.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Component;

import com.pi.demo.config.SegurancaProperties;

/** Limita tentativas de login falhas por chave (ip + e-mail) numa janela de tempo. Em memoria. */
@Component
public class LoginLimiter {

    private final int maximo;
    private final long janelaMs;
    private final Map<String, Deque<Long>> falhas = new ConcurrentHashMap<>();
    private LongSupplier relogio = System::currentTimeMillis;

    public LoginLimiter(SegurancaProperties props) {
        this.maximo = props.loginTentativasMax();
        this.janelaMs = props.loginJanela().toMillis();
    }

    void usarRelogio(LongSupplier relogio) {
        this.relogio = relogio;
    }

    public boolean bloqueado(String chave) {
        Deque<Long> fila = falhas.get(chave);
        if (fila == null) {
            return false;
        }
        synchronized (fila) {
            limpar(fila);
            return fila.size() >= maximo;
        }
    }

    public void registrarFalha(String chave) {
        Deque<Long> fila = falhas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            limpar(fila);
            fila.addLast(relogio.getAsLong());
        }
    }

    public void registrarSucesso(String chave) {
        falhas.remove(chave);
    }

    private void limpar(Deque<Long> fila) {
        long limite = relogio.getAsLong() - janelaMs;
        while (!fila.isEmpty() && fila.peekFirst() < limite) {
            fila.removeFirst();
        }
    }
}
