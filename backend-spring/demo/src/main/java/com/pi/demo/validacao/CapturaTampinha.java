package com.pi.demo.validacao;

/**
 * CapturaTampinha.java - Entrega 1: captura de tampinhas em movimento (teste/validacao).
 *
 * Fluxo:
 *  1. Abre a camera e mostra o video ao vivo (janela "Captura - Camera").
 *  2. Procura a tampinha verde dentro da faixa vermelha (entre as duas linhas).
 *  3. Quando ela fica na faixa por alguns quadros, faz uma rajada curta e escolhe
 *     o quadro mais nitido (foco por nitidez, ja que o foco da camera nao e controlavel).
 *  4. Recorta em volta da tampinha, melhora a qualidade e salva em ./aprovada/
 *
 * Tecla: 'q' ou ESC encerra (com a janela da camera em foco).
 *
 * Uso:
 *   java -cp ".:<classpath>" CapturaTampinha            (camera 0)
 *   java -cp ".:<classpath>" CapturaTampinha 1          (outra camera)
 */

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JFrame;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
public class CapturaTampinha {

    // ===================== CONFIGURACOES (ajuste na bancada) =====================
    static final int CAMERA_PADRAO = 0;
    static final int LARGURA = 1280, ALTURA = 720;
    static final String PASTA_SAIDA = "reprovada";
    static final boolean SALVAR_BRUTO = false;   // true: grava tambem o recorte sem tratamento

    // Faixa vermelha (demarcacao), em fracao da largura. Ajuste para as suas linhas.
    static final double LINHA_ESQ = 0.25;
    static final double LINHA_DIR = 0.70;

    // Faixa vertical de busca (fracao da altura): ignora bordas e topo/fundo da esteira
    static final double Y_MIN = 0.25, Y_MAX = 0.75;

    // Verde em HSV (OpenCV: H 0-180, S 0-255, V 0-255)
    static final Scalar VERDE_MIN = new Scalar(35, 50, 40, 0);
    static final Scalar VERDE_MAX = new Scalar(90, 255, 255, 0);

    // Filtros de forma para aceitar so a tampinha
    static final double AREA_MIN = 2000, AREA_MAX = 150000;
    static final double SOLIDEZ_MIN = 0.5;      // area do contorno / area do retangulo envolvente
    static final double ASPECTO_MIN = 0.6, ASPECTO_MAX = 1.6;

    // Disparo e foco
    static final int FRAMES_ESTAVEIS = 3;   // quadros com a tampinha na faixa antes de disparar
    static final int FRAMES_RAJADA = 5;     // quadros extras para escolher o mais nitido
    static final int FRAMES_LIVRE = 5;      // quadros sem tampinha para liberar a proxima
    static final double MARGEM = 1.25;      // folga do recorte em volta da tampinha
    // =============================================================================

    static volatile boolean sair = false;

    public static void main(String[] args) throws Exception {
        int camera = args.length > 0 ? Integer.parseInt(args[0]) : CAMERA_PADRAO;

        new File(PASTA_SAIDA).mkdirs();
        if (SALVAR_BRUTO) new File(PASTA_SAIDA, "bruto").mkdirs();

        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(camera);
        grabber.setImageWidth(LARGURA);
        grabber.setImageHeight(ALTURA);
        grabber.start();

        OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat();

        CanvasFrame janela = new CanvasFrame("Captura - Camera", 1.0);
        CanvasFrame janelaMascara = new CanvasFrame("Mascara HSV", 1.0);
        janela.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        janela.setFocusable(true);
        janela.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == 'q' || e.getKeyCode() == KeyEvent.VK_ESCAPE) sair = true;
            }
        });

        boolean travado = false;        // true depois de salvar: espera a tampinha sair da faixa
        int contagem = 0;               // quadros seguidos com tampinha na faixa
        int semTampa = 0;               // quadros seguidos sem tampinha na faixa
        double melhorNitidez = -1;
        Mat melhorRecorte = null;

        System.out.println("=== Captura de tampinhas iniciada (q ou ESC para sair) ===");

        while (janela.isVisible() && !sair) {
            Frame frame = grabber.grab();
            if (frame == null) continue;

            // Copia limpa: o recorte e a deteccao usam SEMPRE esta copia, sem desenhos.
            Mat limpo = conv.convert(frame).clone();
            int w = limpo.cols(), h = limpo.rows();

            Mat mascara = new Mat();
            Rect tampa = detectarTampinha(limpo, mascara);

            // Quadro de exibicao: aqui sim podemos desenhar por cima
            Mat vis = limpo.clone();
            int xEsq = (int) (LINHA_ESQ * w), xDir = (int) (LINHA_DIR * w);
            line(vis, new Point(xEsq, 0), new Point(xEsq, h), new Scalar(0, 0, 255, 0), 3, LINE_8, 0);
            line(vis, new Point(xDir, 0), new Point(xDir, h), new Scalar(0, 0, 255, 0), 3, LINE_8, 0);

            String estado;
            if (tampa != null) {
                semTampa = 0;
                rectangle(vis,
                        new Point(tampa.x(), tampa.y()),
                        new Point(tampa.x() + tampa.width(), tampa.y() + tampa.height()),
                        new Scalar(0, 255, 0, 0), 2, LINE_8, 0);

                if (!travado) {
                    contagem++;
                    estado = "Tampinha na faixa...";

                    // Rajada: guarda o recorte mais nitido entre os quadros apos estabilizar
                    if (contagem > FRAMES_ESTAVEIS) {
                        Mat recorte = recortarTampinha(limpo, tampa);
                        double nit = nitidez(recorte);
                        if (nit > melhorNitidez) {
                            melhorNitidez = nit;
                            if (melhorRecorte != null) melhorRecorte.release();
                            melhorRecorte = recorte;
                        } else {
                            recorte.release();
                        }
                    }

                    if (contagem >= FRAMES_ESTAVEIS + FRAMES_RAJADA && melhorRecorte != null) {
                        salvarTampinha(melhorRecorte);
                        melhorRecorte.release();
                        melhorRecorte = null;
                        melhorNitidez = -1;
                        travado = true;
                        contagem = 0;
                        estado = "SALVO";
                    }
                } else {
                    estado = "Aguardando proxima tampinha";
                }
            } else {
                contagem = 0;
                semTampa++;
                // Tampinha saiu antes do fim da rajada: salva o melhor quadro que ja temos
                if (melhorRecorte != null) {
                    salvarTampinha(melhorRecorte);
                    melhorRecorte.release();
                    melhorRecorte = null;
                    melhorNitidez = -1;
                    travado = true;
                }
                if (semTampa >= FRAMES_LIVRE) travado = false;
                estado = "Aguardando";
            }

            putText(vis, estado, new Point(20, 40), FONT_HERSHEY_SIMPLEX, 1.0,
                    new Scalar(255, 255, 255, 0), 2, LINE_AA, false);

            janela.showImage(conv.convert(vis));
            janelaMascara.showImage(conv.convert(mascara));

            vis.release();
            mascara.release();
            limpo.release();
        }

        grabber.stop();
        grabber.release();
        janela.dispose();
        janelaMascara.dispose();
        System.out.println("Encerrado.");
    }

    /**
     * Procura a maior tampinha verde que esteja dentro da faixa vermelha.
     * Retorna o retangulo em coordenadas do quadro completo, ou null.
     * A mascara (regiao de busca) e gravada em 'mascaraSaida' para visualizacao.
     */
    static Rect detectarTampinha(Mat quadro, Mat mascaraSaida) {
        int w = quadro.cols(), h = quadro.rows();
        int y0 = (int) (h * Y_MIN), y1 = (int) (h * Y_MAX);
        Mat faixa = new Mat(quadro, new Rect(0, y0, w, y1 - y0));

        Mat hsv = new Mat();
        cvtColor(faixa, hsv, COLOR_BGR2HSV);
        Mat limiteMin = new Mat(hsv.rows(), hsv.cols(), hsv.type(), VERDE_MIN);
        Mat limiteMax = new Mat(hsv.rows(), hsv.cols(), hsv.type(), VERDE_MAX);
        inRange(hsv, limiteMin, limiteMax, mascaraSaida);
        limiteMin.release();
        limiteMax.release();
        hsv.release();
        faixa.release();

        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(5, 5));
        morphologyEx(mascaraSaida, mascaraSaida, MORPH_OPEN, kernel);
        morphologyEx(mascaraSaida, mascaraSaida, MORPH_CLOSE, kernel);

        MatVector contornos = new MatVector();
        findContours(mascaraSaida, contornos, new Mat(), RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        Rect melhor = null;
        double maiorArea = 0;
        for (long i = 0; i < contornos.size(); i++) {
            Mat c = contornos.get(i);
            double area = contourArea(c);
            if (area < AREA_MIN || area > AREA_MAX) continue;

            Rect r = boundingRect(c);
            double aspecto = (double) r.width() / r.height();
            double solidez = area / ((double) r.width() * r.height());
            if (aspecto < ASPECTO_MIN || aspecto > ASPECTO_MAX || solidez < SOLIDEZ_MIN) continue;

            int cx = r.x() + r.width() / 2;
            if (cx < LINHA_ESQ * w || cx > LINHA_DIR * w) continue;   // fora da faixa vermelha

            if (area > maiorArea) {
                maiorArea = area;
                melhor = new Rect(r.x(), r.y() + y0, r.width(), r.height());
            }
        }
        return melhor;
    }

    /** Recorte quadrado centralizado na tampinha, com folga (a partir do quadro LIMPO). */
    static Mat recortarTampinha(Mat quadro, Rect r) {
        int cx = r.x() + r.width() / 2, cy = r.y() + r.height() / 2;
        int lado = (int) (Math.max(r.width(), r.height()) * MARGEM);
        int x = Math.max(0, cx - lado / 2), y = Math.max(0, cy - lado / 2);
        int x2 = Math.min(quadro.cols(), x + lado), y2 = Math.min(quadro.rows(), y + lado);
        return new Mat(quadro, new Rect(x, y, x2 - x, y2 - y)).clone();
    }

    /** Nitidez = media do modulo do Laplaciano (quanto maior, mais bordas nitidas). */
    static double nitidez(Mat recorte) {
        Mat cinza = new Mat(), lap = new Mat(), abs = new Mat();
        cvtColor(recorte, cinza, COLOR_BGR2GRAY);
        Laplacian(cinza, lap, CV_64F);
        convertScaleAbs(lap, abs);
        Scalar m = mean(abs);
        return m.get(0);
    }

    /**
     * Melhora a qualidade da imagem:
     *  - CLAHE no canal de luminosidade (normaliza iluminacao e reflexos);
     *  - leve suavizacao + unsharp mask (realca detalhes da tampinha).
     */
    static Mat melhorarQualidade(Mat entrada) {
        Mat lab = new Mat();
        cvtColor(entrada, lab, COLOR_BGR2Lab);

        MatVector canais = new MatVector();
        split(lab, canais);
        CLAHE clahe = createCLAHE(2.5, new Size(8, 8));
        Mat lCl = new Mat();
        clahe.apply(canais.get(0), lCl);
        canais.put(0, lCl);

        Mat labMerge = new Mat();
        merge(canais, labMerge);
        Mat bgr = new Mat();
        cvtColor(labMerge, bgr, COLOR_Lab2BGR);

        Mat suave = new Mat();
        GaussianBlur(bgr, suave, new Size(0, 0), 1.0);
        Mat nitida = new Mat();
        addWeighted(bgr, 1.5, suave, -0.5, 0, nitida);

        lab.release(); lCl.release(); labMerge.release(); bgr.release(); suave.release();
        return nitida;
    }

    static void salvarTampinha(Mat recorte) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());

        if (SALVAR_BRUTO) {
            String brutoPath = new File(PASTA_SAIDA, "bruto/tampa_bruta_" + ts + ".png").getPath();
            opencv_imgcodecs.imwrite(brutoPath, recorte);
        }

        Mat tratada = melhorarQualidade(recorte);
        String caminho = new File(PASTA_SAIDA, "tampa_aprovada_" + ts + ".png").getPath();
        opencv_imgcodecs.imwrite(caminho, tratada);
        tratada.release();

        System.out.println("[SALVO] " + caminho);
    }
}
