package com.pi.demo.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fazecast.jSerialComm.SerialPort;
import com.pi.demo.config.EspProperties;
import com.pi.demo.exception.EspException;

import jakarta.annotation.PreDestroy;

/**
 * Comunicacao real com o ESP8266 NodeMCU via USB Serial (jSerialComm), firmware
 * "mesa-de-lorenzo".
 *
 * Protocolo do firmware (texto, 115200 bps, 8N1, uma mensagem por linha terminada em '\n'):
 *   Pi -> ESP: "NOK\n"  aciona a saida de rejeicao (cilindro). O proprio firmware mantem a
 *                       saida acionada pelo tempo fixo TEMPO_NOK_MS e depois desarma.
 *   ESP -> Pi: linhas de texto de resposta ("NOK recebido...", "Comando desconhecido: ...").
 *              Sao apenas registradas no log. O firmware NAO envia estado de sensor.
 *
 * Consequencias:
 *  - Nao ha leitura de entrada: lerBit() e sempre false (o sensor capacitivo nao existe
 *    no firmware atual; ver inspecao.polling-habilitado).
 *  - A saida e por pulso: escreverBit(enderecoCilindro, true) envia NOK; com false nada e
 *    enviado, pois o firmware desarma sozinho. O tempo real do acionamento e o do firmware.
 *  - Ao abrir a porta, o NodeMCU reinicia (DTR); aguardamos o boot antes de enviar comandos.
 */
@Service
@ConditionalOnProperty(prefix = "esp", name = "simulado", havingValue = "false", matchIfMissing = true)
public class EspSerialGateway implements EspGateway {

    private static final Logger log = LoggerFactory.getLogger(EspSerialGateway.class);

    /** Comando de rejeicao reconhecido pelo firmware mesa-de-lorenzo. */
    public static final String COMANDO_NOK = "NOK";
    /** Tempo de boot do NodeMCU apos abrir a porta (reset automatico via DTR). */
    static final long ESPERA_BOOT_MS = 2500;

    private final EspProperties props;
    private final Object escritaLock = new Object();

    private volatile SerialPort porta;
    private volatile boolean encerrando;
    private Thread threadLeitura;

    public EspSerialGateway(EspProperties props) {
        this.props = props;
    }

    @jakarta.annotation.PostConstruct
    void iniciar() {
        threadLeitura = new Thread(this::executar, "esp-serial");
        threadLeitura.setDaemon(true);
        threadLeitura.start();
    }

    @PreDestroy
    void parar() {
        encerrando = true;
        if (threadLeitura != null) {
            threadLeitura.interrupt();
        }
        fecharPortaSilenciosa();
    }

    @Override
    public boolean conectado() {
        SerialPort p = porta;
        return p != null && p.isOpen();
    }

    @Override
    public boolean simulado() {
        return false;
    }

    @Override
    public String porta() {
        return props.porta();
    }

    /** O firmware nao envia estado de entrada: sempre false. */
    @Override
    public boolean lerBit(int endereco) {
        return false;
    }

    @Override
    public void escreverBit(int endereco, boolean ativar) {
        SerialPort p = porta;
        if (p == null || !p.isOpen()) {
            throw new EspException("ESP nao conectado (porta " + props.porta() + "). Nao foi possivel escrever o bit "
                    + endereco + ".");
        }
        if (endereco != props.enderecoCilindroRejeicao()) {
            throw new EspException("Endereco de saida " + endereco + " nao existe no firmware; "
                    + "a unica saida e o cilindro (endereco " + props.enderecoCilindroRejeicao() + ").");
        }
        if (!ativar) {
            log.debug("Desativacao ignorada: o firmware desarma a saida sozinho apos o tempo de acionamento.");
            return;
        }
        enviarLinha(COMANDO_NOK);
    }

    // --- envio -------------------------------------------------------------

    private void enviarLinha(String comando) {
        SerialPort p = porta;
        byte[] dados = (comando + "\n").getBytes(StandardCharsets.US_ASCII);
        synchronized (escritaLock) {
            try {
                OutputStream saida = p.getOutputStream();
                saida.write(dados);
                saida.flush();
                log.info("Enviado ao ESP: {}", comando);
            } catch (IOException e) {
                throw new EspException("Falha ao escrever no ESP: " + e.getMessage(), e);
            }
        }
    }

    // --- thread de fundo: conecta, le linhas de resposta, reconecta em caso de falha ----

    private void executar() {
        while (!encerrando) {
            try {
                if (abrirPorta()) {
                    lerEnquantoAberta();
                }
            } catch (Exception e) {
                log.warn("Erro na comunicacao serial com o ESP: {}", e.getMessage());
            } finally {
                fecharPortaSilenciosa();
            }
            if (!encerrando) {
                dormir(props.intervaloReconexao().toMillis());
            }
        }
    }

    private boolean abrirPorta() {
        SerialPort p = SerialPort.getCommPort(props.porta());
        p.setComPortParameters(props.baudRate(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
        if (!p.openPort()) {
            log.debug("Nao foi possivel abrir a porta {} (ESP desconectado?).", props.porta());
            return false;
        }
        dormir(ESPERA_BOOT_MS);
        porta = p;
        log.info("Conectado ao ESP em {} ({} bps).", props.porta(), props.baudRate());
        return true;
    }

    /**
     * Le so quando ha bytes disponiveis (bytesAvailable). Nao usa readLine com timeout: no
     * jSerialComm, um timeout sem dados lanca IOException e derrubaria a conexao a cada segundo,
     * reiniciando o NodeMCU sem parar.
     */
    private void lerEnquantoAberta() throws IOException {
        SerialPort p = porta;
        StringBuilder linha = new StringBuilder();
        while (!encerrando && p.isOpen()) {
            int disponiveis = p.bytesAvailable();
            if (disponiveis < 0) {
                throw new IOException("porta serial perdida");
            }
            if (disponiveis == 0) {
                dormir(20);
                continue;
            }
            byte[] buf = new byte[disponiveis];
            int lidos = p.readBytes(buf, disponiveis);
            for (int i = 0; i < lidos; i++) {
                char c = (char) (buf[i] & 0xFF);
                if (c == '\n' || c == '\r') {
                    if (linha.length() > 0) {
                        log.info("[ESP] {}", linha.toString().trim());
                        linha.setLength(0);
                    }
                } else {
                    linha.append(c);
                }
            }
        }
    }

    private void fecharPortaSilenciosa() {
        SerialPort p = porta;
        porta = null;
        if (p != null && p.isOpen()) {
            p.closePort();
        }
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
