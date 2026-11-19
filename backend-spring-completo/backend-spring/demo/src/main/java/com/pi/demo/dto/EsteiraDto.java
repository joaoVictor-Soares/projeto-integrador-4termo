package com.pi.demo.dto;

import java.time.LocalDateTime;

import com.pi.demo.model.Esteira;

public record EsteiraDto(
        Integer id,
        String nomeIdentificador,
        Integer velocidadeAtual,
        String status,
        LocalDateTime dataAlteracao) {

    public static EsteiraDto de(Esteira e) {
        return new EsteiraDto(e.getId(), e.getNomeIdentificador(), e.getVelocidadeAtual(),
                e.getStatus(), e.getDataAlteracao());
    }
}
