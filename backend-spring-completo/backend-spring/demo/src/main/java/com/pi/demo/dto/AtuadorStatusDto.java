package com.pi.demo.dto;

import java.time.LocalDateTime;

/**
 * Estado atual do ESP (co-processador de I/O da mesa).
 * Unica entrada: sensor capacitivo. Unica saida: atuador (cilindro de rejeicao) via rele.
 */
public record AtuadorStatusDto(
        /** true = porta serial aberta e o ESP esta respondendo */
        boolean conectado,
        /** true = sem ESP fisico: leituras simuladas em memoria (esp.simulado=true) */
        boolean simulado,
        /** porta serial configurada, ex.: /dev/ttyACM0 */
        String porta,
        /** ultimo estado lido do sensor capacitivo (D2 do ESP) */
        boolean sensorAtivo,
        /** true enquanto o rele/atuador esta energizado */
        boolean atuadorAcionado,
        /** duracao atual do ciclo avanca+recua (variavel global, ver AtuadorConfigDto) */
        long tempoAcionamentoMs,
        LocalDateTime lidoEm) {
}
