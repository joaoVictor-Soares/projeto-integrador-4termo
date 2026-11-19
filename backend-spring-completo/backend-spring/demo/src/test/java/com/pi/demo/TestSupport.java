package com.pi.demo;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import com.pi.demo.config.CameraProperties;
import com.pi.demo.config.EspProperties;
import com.pi.demo.config.InspecaoProperties;
import com.pi.demo.config.SegurancaProperties;
import com.pi.demo.service.EspSimuladoGateway;

/** Fabricas de configuracao e dubles usados nos testes. */
public final class TestSupport {

    public static final int ENDERECO_SENSOR = 0;
    public static final int ENDERECO_CILINDRO = 0;

    private TestSupport() {
    }

    public static EspProperties esp() {
        return new EspProperties(true, "SIMULADO", 115200, Duration.ofMillis(50), 100, ENDERECO_SENSOR, ENDERECO_CILINDRO);
    }

    /** Tempos curtos para os testes. {@code atraso} = tempo do gatilho ate o cilindro atuar. */
    public static InspecaoProperties inspecao(Duration atraso, boolean falhaReprova) {
        return new InspecaoProperties(false, Duration.ofMillis(100), Duration.ofMillis(100), atraso,
                falhaReprova, 1, "Linha de teste", "./target/storage-teste", 10);
    }

    public static SegurancaProperties seguranca(String segredo) {
        return new SegurancaProperties(true, segredo, Duration.ofHours(1), "admin@teste", "", "Admin", 3,
                Duration.ofMinutes(5));
    }

    public static CameraProperties camera() {
        return new CameraProperties("/dev/null", "ffmpeg", "640x480", 0, Duration.ofSeconds(1), "");
    }

    public static boolean aguardar(BooleanSupplier condicao, long timeoutMs) {
        long limite = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condicao.getAsBoolean();
    }

    /** ESP simulado que registra cada escrita de bit (com o instante), para checar a sequencia. */
    public static class RegistradorGateway extends EspSimuladoGateway {

        public record Escrita(long nanos, int endereco, boolean ativo) {
        }

        public final List<Escrita> escritas = new CopyOnWriteArrayList<>();

        @Override
        public void escreverBit(int endereco, boolean ativar) {
            escritas.add(new Escrita(System.nanoTime(), endereco, ativar));
            super.escreverBit(endereco, ativar);
        }

        public boolean escreveu(int endereco, boolean ativo) {
            return primeiraEscrita(endereco, ativo) >= 0;
        }

        /** nanoTime da primeira escrita endereco=ativo, ou -1. */
        public long primeiraEscrita(int endereco, boolean ativo) {
            for (Escrita e : escritas) {
                if (e.endereco() == endereco && e.ativo() == ativo) {
                    return e.nanos();
                }
            }
            return -1;
        }
    }
}
