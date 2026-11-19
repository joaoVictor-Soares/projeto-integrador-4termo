package com.pi.demo.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pi.demo.config.InspecaoProperties;
import com.pi.demo.dto.IaPredictResponse;
import com.pi.demo.exception.RecursoNaoEncontradoException;
import com.pi.demo.model.Esteira;
import com.pi.demo.model.PecasRecusadas;
import com.pi.demo.model.Usuario;
import com.pi.demo.repository.PecasRecusadasRepository;
import com.pi.demo.repository.UsuarioRepository;

@Service
public class PecasRecusadasService {

    private static final DateTimeFormatter NOME_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final PecasRecusadasRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final InspecaoProperties props;

    public PecasRecusadasService(PecasRecusadasRepository repository,
                                 UsuarioRepository usuarioRepository,
                                 InspecaoProperties props) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<PecasRecusadas> listar(Integer esteiraId) {
        return esteiraId == null
                ? repository.findAllByOrderByDataHoraDesc()
                : repository.findByEsteiraIdOrderByDataHoraDesc(esteiraId);
    }

    @Transactional(readOnly = true)
    public PecasRecusadas buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Peca recusada " + id + " nao encontrada."));
    }

    /** Salva a foto no disco (volume Docker) e grava o registro da peca reprovada. */
    @Transactional
    public PecasRecusadas registrar(Esteira esteira, IaPredictResponse resultado, byte[] imagem, Integer operadorId) {
        BigDecimal acuracia = BigDecimal.valueOf(resultado.confianca())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return gravar(esteira, "Reprovada pela IA", acuracia, imagem, operadorId);
    }

    /** Peca desviada por falha de camera/IA (fail-safe): sem acuracia e possivelmente sem foto. */
    @Transactional
    public PecasRecusadas registrarFalha(Esteira esteira, String motivo, byte[] imagem, Integer operadorId) {
        String texto = motivo == null ? "Falha na verificacao" : motivo;
        return gravar(esteira, texto.length() > 250 ? texto.substring(0, 250) : texto, null, imagem, operadorId);
    }

    private PecasRecusadas gravar(Esteira esteira, String erro, BigDecimal acuracia, byte[] imagem, Integer operadorId) {
        LocalDateTime agora = LocalDateTime.now();

        PecasRecusadas p = new PecasRecusadas();
        p.setEsteira(esteira);
        p.setDataHora(agora);
        p.setImagemUrl(imagem == null || imagem.length == 0 ? null : salvarImagem(imagem, agora));
        p.setErroIdentificado(erro);
        p.setNivelAcuracia(acuracia);
        if (operadorId != null) {
            Usuario operador = usuarioRepository.findById(operadorId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario " + operadorId + " nao encontrado."));
            p.setOperador(operador);
        }
        return repository.save(p);
    }

    /** Arquivo da imagem de uma peca recusada (protegido contra path traversal). */
    @Transactional(readOnly = true)
    public Resource imagem(Integer id) {
        PecasRecusadas p = buscar(id);
        if (p.getImagemUrl() == null) {
            throw new RecursoNaoEncontradoException("Peca " + id + " nao possui imagem.");
        }
        Path pasta = pastaImagens();
        Path arquivo = pasta.resolve(Path.of(p.getImagemUrl()).getFileName().toString()).normalize();
        if (!arquivo.startsWith(pasta) || !Files.isReadable(arquivo)) {
            throw new RecursoNaoEncontradoException("Arquivo de imagem da peca " + id + " nao encontrado.");
        }
        return new PathResource(arquivo);
    }

    private String salvarImagem(byte[] imagem, LocalDateTime momento) {
        try {
            Path pasta = pastaImagens();
            Files.createDirectories(pasta);
            String nome = momento.format(NOME_ARQUIVO) + ".jpg";
            Files.write(pasta.resolve(nome), imagem);
            return "/storage/recusadas/" + nome;
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel salvar a imagem da peca recusada: " + e.getMessage(), e);
        }
    }

    private Path pastaImagens() {
        return Path.of(props.pastaImagens()).toAbsolutePath().normalize();
    }
}
