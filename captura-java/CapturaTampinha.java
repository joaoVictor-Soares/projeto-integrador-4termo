/*
 * CapturaTampinha.java - captura de tampinhas na esteira, SEM interface (somente terminal).
 *
 * Fluxo:
 *   1. Abre a camera USB (JavaCV/OpenCV), sem nenhuma janela.
 *   2. Procura a tampinha verde (HSV) na faixa vertical de 25% a 75% da altura.
 *   3. Quando o CENTRO da tampinha entra na faixa vermelha (25%-70% da largura) por
 *      FRAMES_ESTAVEIS quadros seguidos, faz uma rajada de FRAMES_RAJADA quadros e
 *      guarda o mais nitido (Laplaciano) recortado em volta da tampinha.
 *   4. Pede o rotulo no terminal: "a" (aprovada) ou "r" (reprovada), seguido de ENTER,
 *      dentro de --janela-rotulo-ms. Sem tecla, vale --padrao (padrao: a).
 *   5. Salva o recorte BRUTO (com margem) em <saida>/aprovada ou <saida>/reprovada.
 *      O refinamento e feito depois pelo refinador Python (Docker).
 *   6. Se for reprovada, envia "NOK\n" ao ESP8266 (firmware mesa-de-lorenzo, 115200 bps)
 *      depois de --atraso-nok-ms (tempo para a peca chegar ao cilindro).
 *
 * Teclas no terminal (digite e ENTER):
 *   a  = aprovada      r = reprovada      q = encerrar
 *
 * Uso (Windows, a partir da pasta com o .class e a pasta libs\ com os jars):
 *   java -cp "out;libs\*" CapturaTampinha --porta COM7 --saida dados
 *   java -cp "out;libs\*" CapturaTampinha --testar-esp --porta COM7        (teste seguro)
 *   java -cp "out;libs\*" CapturaTampinha --testar-esp --disparar --porta COM7
 *   java -cp "out;libs\*" CapturaTampinha --sem-esp --saida dados           (sem ESP)
 *
 * Dependencias: javacv-platform 1.5.10 e jSerialComm 2.10.4 (ver pom.xml desta pasta).
 */

import com.fazecast.jSerialComm.SerialPort;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.opencv.global.opencv_core.CV_64F;
import static org.bytedeco.opencv.global.opencv_core.convertScaleAbs;
import static org.bytedeco.opencv.global.opencv_core.inRange;
import static org.bytedeco.opencv.global.opencv_core.mean;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2HSV;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_ELLIPSE;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL;
import static org.bytedeco.opencv.global.opencv_imgproc.boundingRect;
import static org.bytedeco.opencv.global.opencv_imgproc.contourArea;
import static org.bytedeco.opencv.global.opencv_imgproc.convexHull;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.erode;
import static org.bytedeco.opencv.global.opencv_imgproc.findContours;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.Laplacian;

public class CapturaTampinha {

    // ---------------- Parametros de deteccao (valores da bancada) ----------------
    static final int VERDE_H_MIN = 35, VERDE_H_MAX = 90, VERDE_S_MIN = 50, VERDE_V_MIN = 40;
    static final double FAIXA_VERT_INI = 0.25, FAIXA_VERT_FIM = 0.75;   // busca (altura)
    static final double LINHA_ESQ = 0.25, LINHA_DIR = 0.70;             // faixa vermelha (largura)
    static final double AREA_MIN = 2000, AREA_MAX = 150000;
    static final double PROP_MIN = 0.6, PROP_MAX = 1.6, SOLIDEZ_MIN = 0.5;
    static final int FRAMES_ESTAVEIS = 3, FRAMES_RAJADA = 5, FRAMES_LIVRE = 5;
    static final double MARGEM_RECORTE = 1.25;
    static final int LARGURA = 1280, ALTURA = 720;

    static final String PASTA_APROVADA = "aprovada";
    static final String PASTA_REPROVADA = "reprovada";
    static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Config cfg;
    private final AtomicBoolean parar = new AtomicBoolean(false);
    private BackendClient backend; // modo backend (--backend): a IA decide e o backend aciona
    private final BlockingQueue<String> teclas = new LinkedBlockingQueue<>();

    private CapturaTampinha(Config cfg) {
        this.cfg = cfg;
    }

    // =======================================================================
    // MAIN
    // =======================================================================
    public static void main(String[] args) throws Exception {
        Config cfg = Config.ler(args);
        if (cfg.ajuda) {
            imprimirAjuda();
            return;
        }
        if (cfg.testarEsp) {
            testarEsp(cfg);
            return;
        }
        new CapturaTampinha(cfg).executar();
    }

    private void executar() throws Exception {
        Path raiz = Paths.get(cfg.saida).toAbsolutePath();
        Path pastaAprov = raiz.resolve(PASTA_APROVADA);
        Path pastaReprov = raiz.resolve(PASTA_REPROVADA);
        Files.createDirectories(pastaAprov);
        Files.createDirectories(pastaReprov);
        System.out.println("Salvando fotos em: " + raiz);

        if (cfg.backend != null) {
            backend = new BackendClient(cfg.backend, cfg.email, cfg.senha);
            backend.login();
            System.out.println("[BACKEND] autenticado em " + cfg.backend + " (atuar=" + cfg.atuar + ")");
        }

        Esp esp = null;
        if (cfg.backend == null && !cfg.semEsp) {
            esp = new Esp(cfg.porta, cfg.baud);
            esp.abrir();
            System.out.println("[ESP] conectado em " + cfg.porta + " a " + cfg.baud + " bps");
        } else {
            System.out.println("[ESP] desativado (modo backend ou --sem-esp): o Java nao aciona o cilindro");
        }

        ScheduledExecutorService agenda = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agenda-nok");
            t.setDaemon(true);
            return t;
        });

        Thread leitorTeclado = new Thread(this::lerTeclado, "teclado");
        leitorTeclado.setDaemon(true);
        leitorTeclado.start();

        System.out.println("Pronto. Digite a (aprovada), r (reprovada) ou q (sair) + ENTER quando solicitado.");

        FrameGrabber camera = null;
        Estado estado = Estado.LIVRE;
        int estaveis = 0, livres = 0;
        try {
            camera = abrirCamera();
            OpenCVFrameConverter.ToMat conversor = new OpenCVFrameConverter.ToMat();
            try (Detector detector = new Detector()) {
                while (!parar.get()) {
                    Frame quadroCam;
                    try {
                        quadroCam = camera.grab();
                    } catch (Exception e) {
                        quadroCam = null;
                    }
                    if (quadroCam == null || quadroCam.image == null) {
                        System.out.println("[CAMERA] quadro nao recebido. Reabrindo a camera...");
                        camera = reabrirCamera(camera);
                        estado = Estado.LIVRE;
                        estaveis = 0;
                        livres = 0;
                        continue;
                    }

                    try (Mat quadro = conversor.convert(quadroCam).clone()) {
                        Rect r = detector.detectar(quadro);
                        boolean naFaixa = naFaixaVermelha(r, quadro.cols());

                        if (estado == Estado.LIVRE) {
                            if (naFaixa) {
                                estaveis++;
                                if (estaveis >= FRAMES_ESTAVEIS) {
                                    rajada(camera, conversor, detector, esp, agenda, pastaAprov, pastaReprov);
                                    estado = Estado.OCUPADO;
                                    estaveis = 0;
                                    livres = 0;
                                }
                            } else {
                                estaveis = 0;
                            }
                        } else { // OCUPADO: so libera depois de sair da faixa por alguns quadros
                            if (naFaixa) {
                                livres = 0;
                            } else if (++livres >= FRAMES_LIVRE) {
                                estado = Estado.LIVRE;
                                livres = 0;
                            }
                        }
                    }
                }
            }
        } finally {
            agenda.shutdownNow();
            if (camera != null) {
                fecharCamera(camera);
            }
            if (esp != null) {
                esp.close();
            }
            System.out.println("Encerrado.");
        }
    }

    private enum Estado { LIVRE, OCUPADO }

    /** Rajada de quadros: escolhe o mais nitido, rotula e salva. */
    private void rajada(FrameGrabber camera, OpenCVFrameConverter.ToMat conversor, Detector detector,
                        Esp esp, ScheduledExecutorService agenda, Path pastaAprov, Path pastaReprov)
            throws Exception {
        Mat melhorRecorte = null;
        double melhorNitidez = -1;

        for (int i = 0; i < FRAMES_RAJADA; i++) {
            Frame f = camera.grab();
            if (f == null || f.image == null) {
                continue;
            }
            try (Mat quadro = conversor.convert(f).clone()) {
                Rect r = detector.detectar(quadro);
                if (r == null) {
                    continue;
                }
                Mat recorte = recortar(quadro, r);
                double nit = nitidez(recorte);
                if (nit > melhorNitidez) {
                    if (melhorRecorte != null) {
                        melhorRecorte.close();
                    }
                    melhorRecorte = recorte;
                    melhorNitidez = nit;
                } else {
                    recorte.close();
                }
            }
        }

        if (melhorRecorte == null) {
            System.out.println("[AVISO] tampinha sumiu durante a rajada. Nada salvo.");
            return;
        }

        try (Mat recorte = melhorRecorte) {
            if (backend != null) {
                tratarViaBackend(recorte, melhorNitidez, pastaAprov, pastaReprov);
                return;
            }
            String rotulo = rotular();
            boolean reprovada = rotulo.equals("r");
            Path pasta = reprovada ? pastaReprov : pastaAprov;
            String nomeRotulo = reprovada ? "reprovada" : "aprovada";
            Path destino = pasta.resolve("tampa_" + nomeRotulo + "_" + LocalDateTime.now().format(FMT_DATA) + ".png");

            boolean ok = imwrite(destino.toString(), recorte);
            if (ok) {
                System.out.println("[SALVO] " + destino.toAbsolutePath() + " (nitidez " + String.format("%.1f", melhorNitidez) + ")");
            } else {
                System.out.println("[ERRO] nao foi possivel gravar " + destino.toAbsolutePath());
            }

            if (reprovada && esp != null) {
                agenda.schedule(() -> {
                    try {
                        esp.enviarNok();
                    } catch (Exception e) {
                        System.out.println("[ERRO] falha ao enviar NOK: " + e.getMessage());
                    }
                }, cfg.atrasoNokMs, TimeUnit.MILLISECONDS);
                System.out.println("[NOK] agendado em " + cfg.atrasoNokMs + " ms");
            }
        }
    }

    /** Modo backend: a foto vai para o backend, que classifica (IA) e aciona o cilindro. */
    private void tratarViaBackend(Mat recorte, double nitidezValor, Path pastaAprov, Path pastaReprov)
            throws Exception {
        Path tmp = Files.createTempFile("tampa_", ".png");
        try {
            if (!imwrite(tmp.toString(), recorte)) {
                System.out.println("[ERRO] nao foi possivel gerar a imagem temporaria para o backend");
                return;
            }
            String resposta;
            try {
                resposta = backend.analisar(Files.readAllBytes(tmp), cfg.atuar);
            } catch (IOException e) {
                System.out.println("[ERRO] backend indisponivel: " + e.getMessage());
                return;
            }
            String status = BackendClient.extrair(resposta, "status");
            String confianca = BackendClient.extrair(resposta, "confianca");
            System.out.println("[BACKEND] status=" + status + " confianca=" + confianca + "% nitidez="
                    + String.format("%.1f", nitidezValor)
                    + (cfg.atuar ? " (atuacao habilitada)" : " (so classificacao)"));

            Path pasta;
            String rotulo;
            if ("reprovadas".equals(status)) {
                pasta = pastaReprov;
                rotulo = "reprovada";
            } else if ("aprovadas".equals(status)) {
                pasta = pastaAprov;
                rotulo = "aprovada";
            } else {
                System.out.println("[AVISO] resposta sem status valido, foto nao salva: " + resposta);
                return;
            }
            Path destino = pasta.resolve("tampa_" + rotulo + "_" + LocalDateTime.now().format(FMT_DATA) + ".png");
            Files.copy(tmp, destino);
            System.out.println("[SALVO] " + destino.toAbsolutePath());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Pergunta o rotulo no terminal dentro da janela de tempo. */
    private String rotular() throws InterruptedException {
        teclas.clear();
        System.out.println("  Rotule a peca: a = aprovada | r = reprovada (+ ENTER). Em "
                + cfg.janelaRotuloMs + " ms vale: " + (cfg.padrao.equals("r") ? "reprovada" : "aprovada"));
        String tecla = teclas.poll(cfg.janelaRotuloMs, TimeUnit.MILLISECONDS);
        if (tecla == null) {
            return cfg.padrao;
        }
        return tecla.startsWith("r") ? "r" : "a";
    }

    private void lerTeclado() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                String l = linha.trim().toLowerCase();
                if (l.equals("q")) {
                    parar.set(true);
                    break;
                }
                if (!l.isEmpty()) {
                    teclas.offer(l.substring(0, 1));
                }
            }
        } catch (IOException e) {
            System.out.println("[TECLADO] leitura encerrada: " + e.getMessage());
        }
    }

    // =======================================================================
    // Camera
    // =======================================================================
    private FrameGrabber abrirCamera() throws Exception {
        while (true) {
            try {
                FrameGrabber g;
                if (cfg.dshow != null) {
                    // Windows: abre a camera pelo nome, via DirectShow (FFmpeg embutido no JavaCV)
                    FFmpegFrameGrabber f = new FFmpegFrameGrabber("video=" + cfg.dshow);
                    f.setFormat("dshow");
                    g = f;
                } else {
                    g = new OpenCVFrameGrabber(cfg.camera);
                }
                g.setImageWidth(LARGURA);
                g.setImageHeight(ALTURA);
                g.start();
                System.out.println("[CAMERA] " + (cfg.dshow != null ? "\"" + cfg.dshow + "\" (DirectShow)" : "indice " + cfg.camera)
                        + " aberta (" + LARGURA + "x" + ALTURA + ")");
                return g;
            } catch (Exception e) {
                System.out.println("[CAMERA] falha ao abrir: " + e.getMessage() + ". Nova tentativa em 3 s.");
                Thread.sleep(3000);
                if (parar.get()) {
                    throw e;
                }
            }
        }
    }

    private FrameGrabber reabrirCamera(FrameGrabber atual) throws Exception {
        fecharCamera(atual);
        Thread.sleep(1000);
        return abrirCamera();
    }

    private static void fecharCamera(FrameGrabber g) {
        try {
            g.stop();
            g.release();
        } catch (Exception ignorada) {
            // ja estava fechada
        }
    }

    // =======================================================================
    // Geometria e nitidez
    // =======================================================================
    static boolean naFaixaVermelha(Rect r, int largura) {
        if (r == null) {
            return false;
        }
        double cx = r.x() + r.width() / 2.0;
        return cx >= largura * LINHA_ESQ && cx <= largura * LINHA_DIR;
    }

    /** Recorte quadrado centrado na tampinha, com margem. Retorna Mat novo (quem chama fecha). */
    static Mat recortar(Mat src, Rect r) {
        int lado = (int) Math.round(Math.max(r.width(), r.height()) * MARGEM_RECORTE);
        lado = Math.min(lado, Math.min(src.cols(), src.rows()));
        int cx = r.x() + r.width() / 2;
        int cy = r.y() + r.height() / 2;
        int x = clamp(cx - lado / 2, 0, src.cols() - lado);
        int y = clamp(cy - lado / 2, 0, src.rows() - lado);
        try (Mat vista = new Mat(src, new Rect(x, y, lado, lado))) {
            return vista.clone();
        }
    }

    /** Nitidez = media do modulo do Laplaciano (quanto maior, mais nitido). */
    static double nitidez(Mat bgr) {
        try (Mat cinza = new Mat(); Mat lap = new Mat(); Mat abs = new Mat()) {
            cvtColor(bgr, cinza, COLOR_BGR2GRAY);
            Laplacian(cinza, lap, CV_64F);
            convertScaleAbs(lap, abs);
            return mean(abs).get(0);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    // =======================================================================
    // Detector de tampinha verde (um por execucao: kernel reutilizado)
    // =======================================================================
    static final class Detector implements Closeable {
        private final Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(5, 5));

        /** Retorna o retangulo da maior tampinha valida na faixa de busca, ou null. */
        Rect detectar(Mat quadro) {
            int linhas = quadro.rows(), colunas = quadro.cols();
            double yIni = linhas * FAIXA_VERT_INI, yFim = linhas * FAIXA_VERT_FIM;

            try (Mat hsv = new Mat();
                 Mat lo = new Mat(linhas, colunas, quadro.type(), new Scalar(VERDE_H_MIN, VERDE_S_MIN, VERDE_V_MIN, 0));
                 Mat hi = new Mat(linhas, colunas, quadro.type(), new Scalar(VERDE_H_MAX, 255, 255, 0));
                 Mat mascara = new Mat();
                 Mat hierarquia = new Mat();
                 MatVector contornos = new MatVector()) {

                cvtColor(quadro, hsv, COLOR_BGR2HSV);
                inRange(hsv, lo, hi, mascara);
                erode(mascara, mascara, kernel);
                dilate(mascara, mascara, kernel);
                findContours(mascara, contornos, hierarquia, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

                Rect melhor = null;
                double melhorArea = 0;
                long n = contornos.size();
                for (long i = 0; i < n; i++) {
                    Mat c = contornos.get(i); // elemento do vetor: NAO fechar aqui
                    double area = contourArea(c);
                    if (area < AREA_MIN || area > AREA_MAX) {
                        continue;
                    }
                    Rect r = boundingRect(c);
                    double prop = (double) r.width() / Math.max(1, r.height());
                    if (prop < PROP_MIN || prop > PROP_MAX) {
                        continue;
                    }
                    double cy = r.y() + r.height() / 2.0;
                    if (cy < yIni || cy > yFim) {
                        continue;
                    }
                    try (Mat casco = new Mat()) {
                        convexHull(c, casco);
                        double areaCasco = contourArea(casco);
                        if (areaCasco <= 0 || area / areaCasco < SOLIDEZ_MIN) {
                            continue;
                        }
                    }
                    if (area > melhorArea) {
                        melhorArea = area;
                        melhor = r;
                    }
                }
                return melhor;
            }
        }

        @Override
        public void close() {
            kernel.close();
        }
    }

    // =======================================================================
    // ESP8266 (firmware mesa-de-lorenzo): texto, 115200 bps, comando "NOK" + '\n'
    // =======================================================================
    static final class Esp implements Closeable {
        private final SerialPort porta;

        Esp(String nome, int baud) {
            this.porta = SerialPort.getCommPort(nome);
            this.porta.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            this.porta.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 1000);
        }

        void abrir() throws IOException, InterruptedException {
            if (!porta.openPort()) {
                throw new IOException("nao foi possivel abrir " + porta.getSystemPortName()
                        + " (porta errada, cabo ou Monitor Serial aberto?)");
            }
            // Abrir a porta faz o NodeMCU resetar (DTR). Espera o boot terminar.
            Thread.sleep(2500);
            Thread leitor = new Thread(this::lerContinuamente, "leitor-esp");
            leitor.setDaemon(true);
            leitor.start();
        }

        /** Imprime tudo que o ESP envia, linha a linha. */
        private void lerContinuamente() {
            StringBuilder linha = new StringBuilder();
            while (porta.isOpen()) {
                int disponiveis = porta.bytesAvailable();
                if (disponiveis <= 0) {
                    sleep(20);
                    continue;
                }
                byte[] buf = new byte[disponiveis];
                int lidos = porta.readBytes(buf, disponiveis);
                for (int i = 0; i < lidos; i++) {
                    char c = (char) (buf[i] & 0xFF);
                    if (c == '\n' || c == '\r') {
                        if (linha.length() > 0) {
                            System.out.println("[ESP] " + linha);
                            linha.setLength(0);
                        }
                    } else {
                        linha.append(c);
                    }
                }
            }
        }

        synchronized void enviar(String comando) throws IOException {
            byte[] dados = (comando + "\n").getBytes(StandardCharsets.US_ASCII);
            int escritos = porta.writeBytes(dados, dados.length);
            if (escritos != dados.length) {
                throw new IOException("escrita incompleta na serial (" + escritos + "/" + dados.length + ")");
            }
            System.out.println("[ESP] >> " + comando);
        }

        void enviarNok() throws IOException {
            enviar("NOK");
        }

        @Override
        public void close() {
            if (porta.isOpen()) {
                porta.closePort();
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =======================================================================
    // Teste de comunicacao (sem camera)
    // =======================================================================
    private static void testarEsp(Config cfg) throws Exception {
        Esp esp = new Esp(cfg.porta, cfg.baud);
        try {
            esp.abrir();
            System.out.println("[TESTE] porta " + cfg.porta + " aberta a " + cfg.baud + " bps. Aguardando 2 s...");
            sleep(2000);

            // Comando desconhecido: o firmware responde "Comando desconhecido: OI".
            // Prova ida e volta da serial SEM acionar o cilindro.
            System.out.println("[TESTE] enviando comando de teste (nao aciona nada)...");
            esp.enviar("OI");
            sleep(1500);

            if (cfg.disparar) {
                System.out.println("[TESTE] ATENCAO: enviando NOK. O cilindro/rele deve acionar por ~5 s.");
                esp.enviarNok();
                sleep(6000);
            } else {
                System.out.println("[TESTE] para testar o acionamento, rode de novo com --disparar.");
            }
        } finally {
            esp.close();
            System.out.println("[TESTE] fim.");
        }
    }

    // BEGIN BACKEND
    /** Cliente HTTP do backend Spring. So JDK 11+, sem bibliotecas extras. */
    static final class BackendClient {
        private final String base;
        private final String email;
        private final String senha;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private volatile String token;

        BackendClient(String base, String email, String senha) {
            this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            this.email = email;
            this.senha = senha;
        }

        void login() throws IOException, InterruptedException {
            String corpo = "{\"email\":\"" + escapar(email) + "\",\"senha\":\"" + escapar(senha) + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IOException("login recusado (HTTP " + resp.statusCode() + "): " + resp.body());
            }
            String t = extrair(resp.body(), "token");
            if (t == null) {
                throw new IOException("resposta de login sem token");
            }
            token = t;
        }

        /** POST /api/inspecao/analisar (multipart, campo "file"). Retorna o JSON da resposta. */
        String analisar(byte[] png, boolean atuar) throws IOException, InterruptedException {
            HttpResponse<String> resp = enviar(png, atuar);
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                login(); // token expirou (validade de 8 h): renova e tenta de novo
                resp = enviar(png, atuar);
            }
            if (resp.statusCode() != 200) {
                throw new IOException("backend HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        }

        private HttpResponse<String> enviar(byte[] png, boolean atuar) throws IOException, InterruptedException {
            String limite = "----CapturaTampinha" + System.nanoTime();
            ByteArrayOutputStream corpo = new ByteArrayOutputStream();
            corpo.write(("--" + limite + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"tampa.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            corpo.write(png);
            corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/inspecao/analisar?atuar=" + atuar))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** Le um campo simples do JSON (string ou numero). Sem biblioteca de JSON. */
        static String extrair(String json, String campo) {
            Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"?([^\",}]*)\"?").matcher(json);
            return m.find() ? m.group(1) : null;
        }

        private static String escapar(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
    // END BACKEND

    // =======================================================================
    // Configuracao de linha de comando
    // =======================================================================
    static final class Config {
        int camera = 0;
        String dshow = null;     // Windows: nome da camera como aparece no Gerenciador (ex.: GENERAL WEBCAM)
        String saida = "dados";
        String porta = "COM7";
        int baud = 115200;
        boolean semEsp = false;
        String padrao = "a";
        long janelaRotuloMs = 1500;
        long atrasoNokMs = 4000;
        boolean testarEsp = false;
        boolean disparar = false;
        boolean ajuda = false;
        String backend = null;   // ex.: http://localhost:8080 (modo backend)
        String email = null;
        String senha = null;
        boolean atuar = true;    // modo backend: o backend pode acionar o cilindro

        static Config ler(String[] args) {
            Config c = new Config();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--camera": c.camera = Integer.parseInt(proximo(args, ++i, a)); break;
                    case "--dshow": c.dshow = proximo(args, ++i, a); break;
                    case "--saida": c.saida = proximo(args, ++i, a); break;
                    case "--porta": c.porta = proximo(args, ++i, a); break;
                    case "--baud": c.baud = Integer.parseInt(proximo(args, ++i, a)); break;
                    case "--sem-esp": c.semEsp = true; break;
                    case "--padrao":
                        c.padrao = proximo(args, ++i, a).toLowerCase();
                        if (!c.padrao.equals("a") && !c.padrao.equals("r")) {
                            throw new IllegalArgumentException("--padrao deve ser a ou r");
                        }
                        break;
                    case "--janela-rotulo-ms": c.janelaRotuloMs = Long.parseLong(proximo(args, ++i, a)); break;
                    case "--atraso-nok-ms": c.atrasoNokMs = Long.parseLong(proximo(args, ++i, a)); break;
                    case "--testar-esp": c.testarEsp = true; break;
                    case "--disparar": c.disparar = true; break;
                    case "--backend": c.backend = proximo(args, ++i, a); break;
                    case "--email": c.email = proximo(args, ++i, a); break;
                    case "--senha": c.senha = proximo(args, ++i, a); break;
                    case "--atuar": c.atuar = Boolean.parseBoolean(proximo(args, ++i, a)); break;
                    case "--ajuda":
                    case "-h":
                        c.ajuda = true; break;
                    default:
                        throw new IllegalArgumentException("argumento desconhecido: " + a + " (use --ajuda)");
                }
            }
            if (c.backend != null && (c.email == null || c.senha == null)) {
                throw new IllegalArgumentException("--backend exige --email e --senha");
            }
            return c;
        }

        private static String proximo(String[] args, int i, String nomeArg) {
            if (i >= args.length) {
                throw new IllegalArgumentException("falta valor para " + nomeArg);
            }
            return args[i];
        }
    }

    private static void imprimirAjuda() {
        System.out.println("CapturaTampinha - captura headless de tampinhas (terminal apenas)");
        System.out.println("  --camera N              indice da camera (padrao 0)");
        System.out.println("  --dshow NOME            Windows: abre a camera pelo nome (ex.: --dshow \"GENERAL WEBCAM\")");
        System.out.println("  --saida PASTA           raiz das fotos; cria PASTA/aprovada e PASTA/reprovada (padrao: dados)");
        System.out.println("  --porta COM7            porta do ESP8266 (padrao COM7; Linux: /dev/ttyUSB0)");
        System.out.println("  --baud 115200           taxa do firmware mesa-de-lorenzo (padrao 115200)");
        System.out.println("  --sem-esp               nao abre a serial (reprovadas nao acionam o cilindro)");
        System.out.println("  --padrao a|r            rotulo se nao digitar nada na janela (padrao a)");
        System.out.println("  --janela-rotulo-ms N    tempo para digitar o rotulo (padrao 1500)");
        System.out.println("  --atraso-nok-ms N       atraso entre a foto e o NOK (padrao 4000) - CALIBRAR NA MESA");
        System.out.println("  --testar-esp            testa a serial (envia OI, nao aciona nada)");
        System.out.println("  --testar-esp --disparar tambem envia NOK (aciona o cilindro por ~5 s)");
        System.out.println("  --backend URL          modo backend: envia a foto para POST URL/api/inspecao/analisar");
        System.out.println("  --email E --senha S    credenciais do backend (usuario operador ou admin)");
        System.out.println("  --atuar true|false     modo backend: true = o backend aciona o cilindro (padrao true)");
        System.out.println("Teclas durante a execucao: a (aprovada), r (reprovada), q (sair). Digite + ENTER.");
    }
}
