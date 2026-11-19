package com.pi.demo.exception;

/** Conflito com o estado atual (ex.: e-mail ja cadastrado, esteira com historico). Vira HTTP 409. */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String message) {
        super(message);
    }
}
