package com.pi.demo.dto;

public record InspecaoStatusDto(
        boolean pollingHabilitado,
        boolean iaDisponivel,
        int pendentes,
        long totalInspecionadas,
        ResultadoInspecaoDto ultimoResultado) {
}
