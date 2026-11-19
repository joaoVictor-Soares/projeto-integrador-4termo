package com.pi.demo.exception;

/** Falha ao capturar imagem da camera. */
public class CameraException extends RuntimeException {
    public CameraException(String message) {
        super(message);
    }

    public CameraException(String message, Throwable cause) {
        super(message, cause);
    }
}
