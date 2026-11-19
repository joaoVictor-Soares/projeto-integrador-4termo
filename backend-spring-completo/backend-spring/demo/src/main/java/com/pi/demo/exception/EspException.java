package com.pi.demo.exception;

/** Falha de comunicacao com o ESP (porta serial fechada, timeout, frame invalido). */
public class EspException extends RuntimeException {
    public EspException(String message) {
        super(message);
    }

    public EspException(String message, Throwable cause) {
        super(message, cause);
    }
}
