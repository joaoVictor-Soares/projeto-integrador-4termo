package com.pi.demo.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ResultadoInspecaoDto(
        String origem,
        LocalDateTime dataHora,
        /** aprovadas | reprovadas | erro */
        String status,
        Double confianca,
        Map<String, Double> probabilidades,
        String acaoIa,
        /** true se a atuacao no CLP foi agendada/executada */
        boolean atuacaoAgendada,
        Integer pecaRecusadaId,
        String erro) {
}
