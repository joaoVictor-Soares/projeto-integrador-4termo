package com.pi.demo.dto;

import java.time.Instant;

public record LoginResponse(String token, String tipo, Instant expiraEm, UsuarioDto usuario) {
}
