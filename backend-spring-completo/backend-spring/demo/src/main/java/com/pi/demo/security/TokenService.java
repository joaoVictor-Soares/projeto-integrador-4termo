package com.pi.demo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi.demo.config.SegurancaProperties;

/**
 * Token de acesso assinado com HMAC-SHA256 (stateless): {@code base64url(id|perfil|expiracao).base64url(assinatura)}.
 * Nao carrega dados sensiveis; a expiracao e verificada em toda requisicao e o usuario e relido do banco.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] chave;
    private final SegurancaProperties props;

    public TokenService(SegurancaProperties props) {
        this.props = props;
        String segredo = props.segredo();
        if (segredo == null || segredo.isBlank()) {
            byte[] aleatorio = new byte[32];
            new SecureRandom().nextBytes(aleatorio);
            this.chave = aleatorio;
            log.warn("seguranca.segredo nao definido: usando segredo aleatorio. Os tokens deixam de valer "
                    + "a cada reinicio. Defina SEGURANCA_SEGREDO (min. 32 caracteres) para manter as sessoes.");
        } else if (segredo.length() < 32) {
            throw new IllegalStateException("seguranca.segredo deve ter pelo menos 32 caracteres.");
        } else {
            this.chave = segredo.getBytes(StandardCharsets.UTF_8);
        }
    }

    public Instant expiracaoDeAgora() {
        return Instant.now().plus(props.validadeToken());
    }

    public String gerar(Integer usuarioId, String perfil, Instant expiracao) {
        String payload = B64.encodeToString((usuarioId + "|" + perfil + "|" + expiracao.getEpochSecond())
                .getBytes(StandardCharsets.UTF_8));
        return payload + "." + B64.encodeToString(assinar(payload));
    }

    /** Valida assinatura e expiracao. Qualquer defeito resulta em Optional vazio. */
    public Optional<UsuarioLogado> validar(String token) {
        return validar(token, Instant.now());
    }

    Optional<UsuarioLogado> validar(String token, Instant agora) {
        if (token == null) {
            return Optional.empty();
        }
        try {
            int ponto = token.indexOf('.');
            if (ponto <= 0 || ponto == token.length() - 1) {
                return Optional.empty();
            }
            String payload = token.substring(0, ponto);
            byte[] assinaturaRecebida = B64D.decode(token.substring(ponto + 1));
            if (!MessageDigest.isEqual(assinar(payload), assinaturaRecebida)) {
                return Optional.empty();
            }
            String[] partes = new String(B64D.decode(payload), StandardCharsets.UTF_8).split("\\|");
            if (partes.length != 3) {
                return Optional.empty();
            }
            if (agora.getEpochSecond() >= Long.parseLong(partes[2])) {
                return Optional.empty();
            }
            return Optional.of(new UsuarioLogado(Integer.valueOf(partes[0]), partes[1], null, null));
        } catch (IllegalArgumentException e) { // base64 ou numero malformado
            return Optional.empty();
        }
    }

    private byte[] assinar(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(chave, ALGORITMO));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponivel", e);
        }
    }
}
