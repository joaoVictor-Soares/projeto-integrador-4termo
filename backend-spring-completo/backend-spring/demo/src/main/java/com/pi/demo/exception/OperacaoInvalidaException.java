package com.pi.demo.exception;

/** Operacao recusada por regra de negocio (ex.: emergencia ativa, fila cheia). */
public class OperacaoInvalidaException extends RuntimeException {
    public OperacaoInvalidaException(String message) {
        super(message);
    }
}
