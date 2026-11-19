package com.pi.demo.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.pi.demo.dto.IaPredictResponse;
import com.pi.demo.exception.IaException;

/** Cliente HTTP da API de IA (ia-modelo/main.py, FastAPI + TFLite). */
@Component
public class PythonMlClient {

    private static final Logger log = LoggerFactory.getLogger(PythonMlClient.class);

    private final RestClient rest;

    public PythonMlClient(RestClient iaRestClient) {
        this.rest = iaRestClient;
    }

    /**
     * POST /predict (multipart, campo "file") -> classificacao da peca (aprovadas | reprovadas).
     * Multipart e o mesmo formato que a API aceita para imagens binarias, sem base64.
     */
    public IaPredictResponse prever(byte[] imagem) {
        try {
            MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
            corpo.add("file", new ByteArrayResource(imagem) {
                @Override
                public String getFilename() {
                    return "tampa.png";
                }
            });
            IaPredictResponse resposta = rest.post()
                    .uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve()
                    .body(IaPredictResponse.class);
            if (resposta == null || resposta.status() == null) {
                throw new IaException("Resposta vazia da API de IA.");
            }
            return resposta;
        } catch (RestClientException e) {
            throw new IaException("Falha ao consultar a API de IA: " + e.getMessage(), e);
        }
    }

    /** GET /health -> true se a API respondeu e o modelo esta carregado. */
    public boolean saudavel() {
        try {
            HealthResponse h = rest.get().uri("/health").retrieve().body(HealthResponse.class);
            return h != null && h.model_loaded();
        } catch (RestClientException e) {
            log.debug("API de IA indisponivel: {}", e.getMessage());
            return false;
        }
    }

    /** Espelha o JSON de /health ({"status": "ok", "model_loaded": true}). */
    private record HealthResponse(String status, boolean model_loaded) {
    }
}
