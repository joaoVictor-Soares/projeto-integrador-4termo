package com.pi.demo.dto;

import java.time.LocalDateTime;

import com.pi.demo.model.DadosProducao;

public record DadosProducaoDto(
        Integer id,
        Integer esteiraId,
        LocalDateTime dataHoraInicio,
        Integer totalProcessadas,
        Integer totalAprovadas,
        Integer totalRecusadas,
        /** percentual de recusadas (0-100) */
        double taxaRecusa) {

    public static DadosProducaoDto de(DadosProducao d) {
        int proc = d.getTotalProcessadas() == null ? 0 : d.getTotalProcessadas();
        int rec = d.getTotalRecusadas() == null ? 0 : d.getTotalRecusadas();
        double taxa = proc == 0 ? 0.0 : Math.round(rec * 10000.0 / proc) / 100.0;
        return new DadosProducaoDto(d.getId(), d.getEsteira().getId(), d.getDataHoraInicio(),
                d.getTotalProcessadas(), d.getTotalAprovadas(), d.getTotalRecusadas(), taxa);
    }
}
