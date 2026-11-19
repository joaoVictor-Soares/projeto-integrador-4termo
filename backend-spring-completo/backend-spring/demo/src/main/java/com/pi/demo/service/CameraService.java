package com.pi.demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi.demo.config.CameraProperties;
import com.pi.demo.exception.CameraException;

/**
 * Captura um frame JPEG da webcam USB usando ffmpeg (v4l2).
 * No container, mapeie o dispositivo: devices: ["/dev/video0:/dev/video0"].
 */
@Service
public class CameraService {

    private static final Logger log = LoggerFactory.getLogger(CameraService.class);

    private final CameraProperties props;

    public CameraService(CameraProperties props) {
        this.props = props;
    }

    /** Captura uma imagem (JPEG). Serializado: a webcam nao aceita dois acessos simultaneos. */
    public synchronized byte[] capturar() {
        if (props.usaImagemSimulada()) {
            return lerImagemSimulada();
        }

        List<String> comando = List.of(
                props.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y",
                "-f", "v4l2",
                "-video_size", props.resolucao(),
                "-i", props.dispositivo(),
                // descarta os primeiros frames (auto-exposicao da webcam)
                "-vf", "select=gte(n\\," + props.framesDescartados() + ")",
                "-frames:v", "1",
                "-f", "image2", "-c:v", "mjpeg",
                "pipe:1");

        Process processo = null;
        try {
            processo = new ProcessBuilder(comando).start();
            // stderr sai com -loglevel error (pouco volume); lido so em caso de falha
            byte[] imagem = processo.getInputStream().readAllBytes();

            if (!processo.waitFor(props.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                processo.destroyForcibly();
                throw new CameraException("Timeout ao capturar imagem da camera " + props.dispositivo());
            }
            if (processo.exitValue() != 0 || imagem.length == 0) {
                String erro = new String(processo.getErrorStream().readAllBytes()).trim();
                throw new CameraException("ffmpeg falhou ao capturar de " + props.dispositivo()
                        + (erro.isEmpty() ? "" : ": " + erro));
            }
            return imagem;
        } catch (IOException e) {
            throw new CameraException("Nao foi possivel executar o ffmpeg ('" + props.ffmpeg() + "'): "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (processo != null) {
                processo.destroyForcibly();
            }
            throw new CameraException("Captura interrompida.", e);
        }
    }

    /** imagem-simulada pode ser um arquivo ou uma PASTA (sorteia uma imagem a cada captura). */
    private byte[] lerImagemSimulada() {
        try {
            Path caminho = Path.of(props.imagemSimulada());
            if (Files.isDirectory(caminho)) {
                try (var arquivos = Files.walk(caminho)) {
                    List<Path> imagens = arquivos
                            .filter(Files::isRegularFile)
                            .filter(f -> f.getFileName().toString().toLowerCase().matches(".*\\.(jpe?g|png)$"))
                            .toList();
                    if (imagens.isEmpty()) {
                        throw new CameraException("Nenhuma imagem (jpg/png) em " + caminho);
                    }
                    caminho = imagens.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(imagens.size()));
                }
            }
            log.debug("Camera simulada: lendo {}", caminho);
            return Files.readAllBytes(caminho);
        } catch (IOException e) {
            throw new CameraException("Imagem simulada ilegivel (" + props.imagemSimulada() + "): "
                    + e.getMessage(), e);
        }
    }
}
