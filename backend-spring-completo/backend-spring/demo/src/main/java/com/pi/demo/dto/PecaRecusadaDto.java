package com.pi.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.pi.demo.model.PecasRecusadas;

public record PecaRecusadaDto(
        Integer id,
        Integer esteiraId,
        Integer operadorId,
        LocalDateTime dataHora,
        /** URL da imagem servida pela propria API */
        String imagemUrl,
        String erroIdentificado,
        BigDecimal nivelAcuracia) {

    public static PecaRecusadaDto de(PecasRecusadas p) {
        return new PecaRecusadaDto(
                p.getId(),
                p.getEsteira().getId(),
                p.getOperador() == null ? null : p.getOperador().getId(),
                p.getDataHora(),
                p.getImagemUrl() == null ? null : "/api/pecas-recusadas/" + p.getId() + "/imagem",
                p.getErroIdentificado(),
                p.getNivelAcuracia());
    }
}
