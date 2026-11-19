package com.pi.demo.exception;

/** Falha ao consultar a API de IA (main.py). */
public class IaException extends RuntimeException {
    public IaException(String message) {
        super(message);
    }

    public IaException(String message, Throwable cause) {
        super(message, cause);
    }
}
