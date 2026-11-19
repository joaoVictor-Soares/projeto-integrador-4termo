package com.pi.demo.exception;

/** Login recusado ou requisicao sem autenticacao. Vira HTTP 401. */
public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException(String message) {
        super(message);
    }
}
