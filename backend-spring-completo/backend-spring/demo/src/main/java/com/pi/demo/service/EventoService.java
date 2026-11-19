package com.pi.demo.service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

/**
 * Registro em memoria dos ultimos eventos da mesa (alarmes, inspecoes, emergencia) e difusao
 * em tempo real para quem se inscrever (SSE para o app). Nao persiste: serve para alertas ao vivo.
 */
@Service
public class EventoService {

    public static final String INFO = "INFO";
    public static final String AVISO = "AVISO";
    public static final String ERRO = "ERRO";

    public record Evento(long id, LocalDateTime dataHora, String tipo, String severidade,
                         String mensagem, Map<String, Object> dados) {
    }

    private static final int CAPACIDADE = 200;

    private final Deque<Evento> historico = new ArrayDeque<>();
    private final List<Consumer<Evento>> ouvintes = new CopyOnWriteArrayList<>();
    private long proximoId = 1;

    public Evento publicar(String tipo, String severidade, String mensagem, Map<String, Object> dados) {
        Evento evento;
        synchronized (this) {
            evento = new Evento(proximoId++, LocalDateTime.now(), tipo, severidade, mensagem,
                    dados == null ? Map.of() : dados);
            historico.addLast(evento);
            while (historico.size() > CAPACIDADE) {
                historico.removeFirst();
            }
        }
        for (Consumer<Evento> ouvinte : ouvintes) {
            try {
                ouvinte.accept(evento);
            } catch (RuntimeException ignored) {
                // um assinante com problema nao pode derrubar quem publica
            }
        }
        return evento;
    }

    public Evento publicar(String tipo, String severidade, String mensagem) {
        return publicar(tipo, severidade, mensagem, null);
    }

    /** Eventos com id maior que {@code depoisDe}, do mais antigo para o mais novo (no maximo {@code limite}). */
    public synchronized List<Evento> recentes(long depoisDe, int limite) {
        List<Evento> filtrados = new ArrayList<>();
        for (Evento e : historico) {
            if (e.id() > depoisDe) {
                filtrados.add(e);
            }
        }
        int inicio = Math.max(0, filtrados.size() - Math.max(1, limite));
        return new ArrayList<>(filtrados.subList(inicio, filtrados.size()));
    }

    public void adicionarOuvinte(Consumer<Evento> ouvinte) {
        ouvintes.add(ouvinte);
    }

    public void removerOuvinte(Consumer<Evento> ouvinte) {
        ouvintes.remove(ouvinte);
    }
}
