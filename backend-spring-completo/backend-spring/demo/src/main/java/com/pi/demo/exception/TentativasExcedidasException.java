package com.pi.demo.exception;

/** Muitas tentativas de login falhas. Vira HTTP 429. */
public class TentativasExcedidasException extends RuntimeException {
    public TentativasExcedidasException(String message) {
        super(message);
    }
}
