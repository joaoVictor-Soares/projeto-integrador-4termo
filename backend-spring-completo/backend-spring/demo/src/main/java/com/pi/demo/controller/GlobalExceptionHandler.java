package com.pi.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.pi.demo.exception.CameraException;
import com.pi.demo.exception.EspException;
import com.pi.demo.exception.ConflitoException;
import com.pi.demo.exception.CredenciaisInvalidasException;
import com.pi.demo.exception.IaException;
import com.pi.demo.exception.OperacaoInvalidaException;
import com.pi.demo.exception.RecursoNaoEncontradoException;
import com.pi.demo.exception.TentativasExcedidasException;

/** Todas as respostas de erro seguem RFC 7807 (application/problem+json). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({OperacaoInvalidaException.class, IllegalArgumentException.class})
    public ProblemDetail requisicaoInvalida(RuntimeException e) {
        return problema(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ProblemDetail naoAutenticado(CredenciaisInvalidasException e) {
        return problema(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    public ProblemDetail muitasTentativas(TentativasExcedidasException e) {
        return problema(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException e) {
        return problema(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException e) {
        log.warn("Violacao de integridade: {}", e.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "A operacao viola uma restricao de integridade dos dados (registro em uso ou duplicado).");
    }

    @ExceptionHandler(EspException.class)
    public ProblemDetail esp(EspException e) {
        log.warn("ESP: {}", e.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(CameraException.class)
    public ProblemDetail camera(CameraException e) {
        log.warn("Camera: {}", e.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(IaException.class)
    public ProblemDetail ia(IaException e) {
        log.warn("IA: {}", e.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** Parametro de query/path com tipo errado (ex.: /cilindros/abc/avancar). */
    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail tipoInvalido(TypeMismatchException e) {
        String nome = e.getPropertyName() == null ? "parametro" : e.getPropertyName();
        return problema(HttpStatus.BAD_REQUEST, "Valor invalido para '" + nome + "': " + e.getValue());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail corpoInvalido(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "Corpo da requisicao ausente ou invalido (JSON malformado ou tipos incorretos).");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail arquivoGrande(MaxUploadSizeExceededException e) {
        return problema(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo maior que o limite de 10 MB.");
    }

    @ExceptionHandler(MultipartException.class)
    public ProblemDetail multipart(MultipartException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao multipart invalida: " + e.getMessage());
    }

    /**
     * Rede de seguranca. Excecoes padrao do Spring MVC (parametro ausente, rota inexistente, metodo ou
     * Content-Type nao suportado...) ja carregam status e corpo corretos via ErrorResponse: so repassamos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> generico(Exception e) {
        if (e instanceof ErrorResponse resposta) {
            return ResponseEntity.status(resposta.getStatusCode())
                    .headers(resposta.getHeaders())
                    .body(resposta.getBody());
        }
        log.error("Erro nao tratado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor."));
    }

    private ProblemDetail problema(HttpStatus status, String detalhe) {
        return ProblemDetail.forStatusAndDetail(status, detalhe);
    }
}
