#!/usr/bin/env python3
"""
refinar_tampinhas.py - Entrega 1 (parte 2)

Pega as imagens geradas pelo CapturaTampinha.java (pasta aprovada/) e:
  1. localiza a tampinha pela cor verde (fallback: Hough circles);
  2. recorta em volta dela, descartando a esteira;
  3. zera o fundo fora do circulo da tampinha (desligue com --sem-mascara);
  4. aumenta a resolucao (Lanczos ou super-resolucao DNN, se houver modelo);
  5. aplica nitidez leve e salva em PNG em aprovada_refinada/.

Uso:
  python refinar_tampinhas.py
  python refinar_tampinhas.py --tamanho 512 --debug
  python refinar_tampinhas.py --modelo FSRCNN_x4.pb --modelo-nome fsrcnn --escala 4

Dependencias:
  pip install opencv-python numpy
  (super-resolucao por modelo exige opencv-contrib-python)
"""
import argparse
import glob
import os

import cv2
import numpy as np

# Verde em HSV (OpenCV: H 0-180, S 0-255, V 0-255)
VERDE_MIN = np.array([35, 50, 40], dtype=np.uint8)
VERDE_MAX = np.array([90, 255, 255], dtype=np.uint8)


def localizar_tampinha(img):
    """Retorna (cx, cy, raio) da tampinha, ou None se nao encontrar."""
    h, w = img.shape[:2]

    # 1) Mascara verde + maior contorno -> circulo envolvente
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, VERDE_MIN, VERDE_MAX)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
    contornos, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contornos:
        maior = max(contornos, key=cv2.contourArea)
        if cv2.contourArea(maior) > 0.02 * h * w:
            (x, y), r = cv2.minEnclosingCircle(maior)
            return int(x), int(y), int(r)

    # 2) Fallback: Hough (quando o verde estiver fraco)
    cinza = cv2.medianBlur(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY), 5)
    lado = min(h, w)
    circulos = cv2.HoughCircles(
        cinza, cv2.HOUGH_GRADIENT, dp=1.2, minDist=lado // 2,
        param1=100, param2=30,
        minRadius=int(lado * 0.2), maxRadius=int(lado * 0.55),
    )
    if circulos is not None:
        x, y, r = np.round(circulos[0][0]).astype(int)
        return int(x), int(y), int(r)
    return None


def focar_na_tampinha(img, cx, cy, raio, margem=1.10, mascarar=True):
    """Recorta em volta da tampinha e, se pedido, zera o fundo fora do circulo."""
    h, w = img.shape[:2]
    R = int(raio * margem)
    x1, y1 = max(0, cx - R), max(0, cy - R)
    x2, y2 = min(w, cx + R), min(h, cy + R)
    recorte = img[y1:y2, x1:x2].copy()
    if mascarar:
        m = np.zeros(recorte.shape[:2], np.uint8)
        cv2.circle(m, (cx - x1, cy - y1), raio, 255, -1)
        recorte = cv2.bitwise_and(recorte, recorte, mask=m)
    return recorte


def quadrado(img):
    """Completa com preto para deixar a imagem quadrada (evita distorcer no resize)."""
    h, w = img.shape[:2]
    s = max(h, w)
    out = np.zeros((s, s, 3), np.uint8)
    oy, ox = (s - h) // 2, (s - w) // 2
    out[oy:oy + h, ox:ox + w] = img
    return out


def aumentar(img, tamanho, modelo=None, nome_modelo="fsrcnn", escala=4):
    """Aumenta a resolucao: super-resolucao por DNN (se houver modelo) + Lanczos."""
    if modelo:
        sr = cv2.dnn_superres.DnnSuperResImpl_create()
        sr.readModel(modelo)
        sr.setModel(nome_modelo, escala)
        img = sr.upsample(img)
    return cv2.resize(img, (tamanho, tamanho), interpolation=cv2.INTER_LANCZOS4)


def nitidez(img):
    """Unsharp mask leve para realcar a borda da tampinha."""
    suave = cv2.GaussianBlur(img, (0, 0), 1.5)
    return cv2.addWeighted(img, 1.4, suave, -0.4, 0)


def desenhar_debug(img, cx, cy, raio):
    d = img.copy()
    cv2.circle(d, (cx, cy), raio, (0, 255, 0), 2)
    cv2.circle(d, (cx, cy), 3, (0, 0, 255), -1)
    return d


def main():
    p = argparse.ArgumentParser(description="Refina as tampinhas capturadas pelo Java.")
    p.add_argument("-e", "--entrada", default="reprovada", help="pasta com as imagens do Java")
    p.add_argument("-s", "--saida", default="reprovada_refinada", help="pasta de saida")
    p.add_argument("-t", "--tamanho", type=int, default=512, help="lado final em pixels")
    p.add_argument("--sem-mascara", action="store_true", help="mantem o fundo ao redor da tampinha")
    p.add_argument("--modelo", help="arquivo .pb de super-resolucao (FSRCNN/EDSR/ESPCN)")
    p.add_argument("--modelo-nome", default="fsrcnn", help="nome do modelo: fsrcnn, edsr, espcn...")
    p.add_argument("--escala", type=int, default=4, help="fator de aumento do modelo DNN")
    p.add_argument("--debug", action="store_true", help="salva imagem com o circulo detectado")
    a = p.parse_args()

    os.makedirs(a.saida, exist_ok=True)
    if a.debug:
        os.makedirs(os.path.join(a.saida, "debug"), exist_ok=True)

    arquivos = sorted(
        glob.glob(os.path.join(a.entrada, "tampa_aprovada_*.png"))
        + glob.glob(os.path.join(a.entrada, "tampa_aprovada_*.jpg"))
    )
    if not arquivos:
        print(f"Nenhuma imagem 'tampa_aprovada_*' encontrada em '{a.entrada}'.")
        return

    for caminho in arquivos:
        img = cv2.imread(caminho)
        if img is None:
            print(f"[ERRO] nao consegui ler {caminho}")
            continue

        h, w = img.shape[:2]
        loc = localizar_tampinha(img)
        if loc is None:
            print(f"[AVISO] tampinha nao encontrada em {caminho}; usando o centro da imagem")
            cx, cy, r = w // 2, h // 2, min(h, w) // 2
        else:
            cx, cy, r = loc

        recorte = focar_na_tampinha(img, cx, cy, r, mascarar=not a.sem_mascara)
        out = aumentar(quadrado(recorte), a.tamanho, a.modelo, a.modelo_nome, a.escala)
        out = nitidez(out)

        base = os.path.splitext(os.path.basename(caminho))[0]
        cv2.imwrite(os.path.join(a.saida, base + "_refinada.png"), out)
        if a.debug:
            cv2.imwrite(os.path.join(a.saida, "debug", base + "_debug.png"),
                        desenhar_debug(img, cx, cy, r))
        print(f"[OK] {base} -> {out.shape[1]}x{out.shape[0]}")


if __name__ == "__main__":
    main()
