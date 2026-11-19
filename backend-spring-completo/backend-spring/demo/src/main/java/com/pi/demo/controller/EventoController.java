package com.pi.demo.controller;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.pi.demo.service.EventoService;
import com.pi.demo.service.EventoService.Evento;

import jakarta.annotation.PreDestroy;

/** Alertas em tempo real para o app: consulta incremental e fluxo SSE. */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService eventos;
    private final List<SseEmitter> clientes = new CopyOnWriteArrayList<>();
    /** Envio em thread propria: um cliente lento nunca trava o polling do CLP. */
    private final ExecutorService envio = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-envio");
        t.setDaemon(true);
        return t;
    });

    public EventoController(EventoService eventos) {
        this.eventos = eventos;
        eventos.adicionarOuvinte(evento -> envio.execute(() -> difundir(evento)));
    }

    @PreDestroy
    public void encerrar() {
        envio.shutdownNow();
    }

    /** Eventos mais recentes. {@code depois}: devolve so os de id maior (para polling incremental). */
    @GetMapping
    public List<Evento> recentes(@RequestParam(defaultValue = "0") long depois,
                                 @RequestParam(defaultValue = "50") int limite) {
        return eventos.recentes(depois, Math.min(Math.max(limite, 1), 200));
    }

    /** Fluxo SSE (text/event-stream). Cada mensagem: event = tipo, data = JSON do evento. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L); // sem timeout
        clientes.add(emitter);
        emitter.onCompletion(() -> clientes.remove(emitter));
        emitter.onTimeout(() -> clientes.remove(emitter));
        emitter.onError(e -> clientes.remove(emitter));
        envio.execute(() -> {
            try {
                emitter.send(SseEmitter.event().comment("conectado"));
            } catch (IOException | RuntimeException e) {
                clientes.remove(emitter);
            }
        });
        return emitter;
    }

    @Scheduled(fixedRate = 25_000)
    public void batimento() {
        envio.execute(() -> {
            for (SseEmitter c : clientes) {
                try {
                    c.send(SseEmitter.event().comment("ping"));
                } catch (IOException | RuntimeException e) {
                    clientes.remove(c);
                }
            }
        });
    }

    private void difundir(Evento evento) {
        for (SseEmitter c : clientes) {
            try {
                c.send(SseEmitter.event().id(String.valueOf(evento.id())).name(evento.tipo()).data(evento, MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException e) {
                clientes.remove(c);
            }
        }
    }
}
