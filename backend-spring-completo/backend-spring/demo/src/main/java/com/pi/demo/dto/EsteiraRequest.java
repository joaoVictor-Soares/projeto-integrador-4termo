package com.pi.demo.dto;

/** Campos nulos sao ignorados em atualizacoes parciais. */
public record EsteiraRequest(
        String nomeIdentificador,
        Integer velocidadeAtual,
        /** ATIVA, PAUSADA ou MANUTENCAO */
        String status) {
}
