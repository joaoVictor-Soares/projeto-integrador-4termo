#!/usr/bin/env python3
"""
refinador_tampinhas.py - refina os recortes de tampinhas gerados pela captura Java.

Pode ser usado de duas formas:
  1) Biblioteca (servidor/API):  from refinador_tampinhas import refinar
                                 saida = refinar(imagem_bgr)            # numpy BGR -> numpy BGR
  2) Pasta (Docker, padrao):     observa <raiz>/aprovada e <raiz>/reprovada e grava
                                 <raiz>/aprovada_refinada e <raiz>/reprovada_refinada
                                 com o nome <original>_refinada.png

Idempotente: se o arquivo refinado ja existe, a imagem nao e processada de novo.
"""
import argparse
import logging
import os
import sys
import time
from pathlib import Path

import cv2
import numpy as np

log = logging.getLogger("refinador")

EXTENSOES = {".png", ".jpg", ".jpeg"}
PARES = [("aprovada", "aprovada_refinada"), ("reprovada", "reprovada_refinada")]

# Mesma faixa de verde usada na captura Java
VERDE_MIN = np.array([35, 50, 40], dtype=np.uint8)
VERDE_MAX = np.array([90, 255, 255], dtype=np.uint8)
MARGEM = 1.10          # folga em volta do circulo da tampinha
IDADE_MINIMA_S = 1.5   # so processa arquivo parado ha X s (evita ler arquivo sendo gravado)


def mascara_verde(img_bgr):
    hsv = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2HSV)
    mascara = cv2.inRange(hsv, VERDE_MIN, VERDE_MAX)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    mascara = cv2.morphologyEx(mascara, cv2.MORPH_OPEN, kernel)
    mascara = cv2.morphologyEx(mascara, cv2.MORPH_CLOSE, kernel)
    return mascara


def localizar_tampinha(img_bgr):
    """Retorna (cx, cy, raio) em pixels, ou None se nada for encontrado."""
    h, w = img_bgr.shape[:2]
    contornos, _ = cv2.findContours(mascara_verde(img_bgr), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contornos:
        maior = max(contornos, key=cv2.contourArea)
        if cv2.contourArea(maior) > 0.005 * h * w:
            (cx, cy), raio = cv2.minEnclosingCircle(maior)
            return float(cx), float(cy), float(raio)

    # Plano B: circulos pelo formato (Hough)
    cinza = cv2.medianBlur(cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY), 5)
    lado = min(h, w)
    circulos = cv2.HoughCircles(cinza, cv2.HOUGH_GRADIENT, dp=1.2, minDist=lado // 2,
                                param1=100, param2=40, minRadius=lado // 8, maxRadius=lado // 2)
    if circulos is not None:
        x, y, r = circulos[0][0]
        return float(x), float(y), float(r)
    return None


def focar_na_tampinha(img_bgr, cx, cy, raio, mascarar=True):
    h, w = img_bgr.shape[:2]
    r = raio * MARGEM
    x0, y0 = int(max(0, cx - r)), int(max(0, cy - r))
    x1, y1 = int(min(w, cx + r)), int(min(h, cy + r))
    recorte = img_bgr[y0:y1, x0:x1].copy()
    if mascarar:
        mascara = np.zeros(recorte.shape[:2], dtype=np.uint8)
        cv2.circle(mascara, (int(round(cx - x0)), int(round(cy - y0))), int(round(raio)), 255, -1)
        recorte = cv2.bitwise_and(recorte, recorte, mask=mascara)
    return recorte


def quadrado(img_bgr):
    h, w = img_bgr.shape[:2]
    lado = max(h, w)
    saida = np.zeros((lado, lado, 3), dtype=np.uint8)
    y, x = (lado - h) // 2, (lado - w) // 2
    saida[y:y + h, x:x + w] = img_bgr
    return saida


def aumentar(img_bgr, tamanho):
    return cv2.resize(img_bgr, (tamanho, tamanho), interpolation=cv2.INTER_LANCZOS4)


def nitidez(img_bgr):
    """Unsharp mask leve."""
    desfocada = cv2.GaussianBlur(img_bgr, (0, 0), 1.0)
    return cv2.addWeighted(img_bgr, 1.5, desfocada, -0.5, 0)


def refinar(imagem_bgr, tamanho=512, mascarar=True):
    """Pipeline completo: localizar -> focar -> quadrado -> aumentar -> nitidez."""
    if imagem_bgr is None or imagem_bgr.size == 0:
        raise ValueError("imagem vazia")
    if imagem_bgr.ndim == 2:
        imagem_bgr = cv2.cvtColor(imagem_bgr, cv2.COLOR_GRAY2BGR)

    loc = localizar_tampinha(imagem_bgr)
    if loc is None:
        h, w = imagem_bgr.shape[:2]
        loc = (w / 2.0, h / 2.0, min(h, w) / 2.0)
        log.warning("tampinha nao localizada; usando o centro da imagem")
    cx, cy, raio = loc

    recorte = focar_na_tampinha(imagem_bgr, cx, cy, raio, mascarar=mascarar)
    recorte = quadrado(recorte)
    recorte = aumentar(recorte, tamanho)
    return nitidez(recorte)


def ler_imagem(caminho: Path):
    dados = np.fromfile(str(caminho), dtype=np.uint8)  # funciona com caminhos com acento no Windows
    return cv2.imdecode(dados, cv2.IMREAD_COLOR)


def processar_arquivo(entrada: Path, pasta_saida: Path, tamanho: int, mascarar: bool) -> bool:
    destino = pasta_saida / f"{entrada.stem}_refinada.png"
    if destino.exists():
        return False
    img = ler_imagem(entrada)
    if img is None:
        log.error("nao consegui ler %s", entrada.name)
        return False
    saida = refinar(img, tamanho=tamanho, mascarar=mascarar)
    ok, buffer = cv2.imencode(".png", saida)
    if not ok:
        log.error("falha ao codificar %s", entrada.name)
        return False
    temporario = destino.with_suffix(".tmp")
    temporario.write_bytes(buffer.tobytes())
    os.replace(temporario, destino)  # so aparece pronto
    log.info("refinada: %s", destino)
    return True


def varrer_uma_vez(raiz: Path, tamanho: int, mascarar: bool) -> int:
    total = 0
    agora = time.time()
    for nome_entrada, nome_saida in PARES:
        pasta_entrada = raiz / nome_entrada
        pasta_saida = raiz / nome_saida
        pasta_entrada.mkdir(parents=True, exist_ok=True)
        pasta_saida.mkdir(parents=True, exist_ok=True)
        for arquivo in sorted(pasta_entrada.iterdir()):
            if not arquivo.is_file() or arquivo.suffix.lower() not in EXTENSOES:
                continue
            if agora - arquivo.stat().st_mtime < IDADE_MINIMA_S:
                continue
            try:
                if processar_arquivo(arquivo, pasta_saida, tamanho, mascarar):
                    total += 1
            except Exception:  # um arquivo ruim nao derruba o servico
                log.exception("erro ao processar %s", arquivo.name)
    return total


def main(argv=None):
    parser = argparse.ArgumentParser(description="Refina recortes de tampinhas (aprovada/reprovada).")
    parser.add_argument("--raiz", default=os.environ.get("DADOS_DIR", "/dados"),
                        help="pasta que contem aprovada/ e reprovada/ (padrao: $DADOS_DIR ou /dados)")
    parser.add_argument("--tamanho", type=int, default=512, help="lado da saida em pixels (padrao 512)")
    parser.add_argument("--sem-mascara", action="store_true", help="nao zera o fundo fora do circulo")
    parser.add_argument("--intervalo", type=float, default=2.0, help="segundos entre varreduras (padrao 2)")
    parser.add_argument("--uma-vez", action="store_true", help="processa o que existe e encerra")
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s",
                        stream=sys.stdout)
    raiz = Path(args.raiz)
    log.info("observando %s (tamanho %d, mascara %s)", raiz.resolve(), args.tamanho, not args.sem_mascara)

    if args.uma_vez:
        total = varrer_uma_vez(raiz, args.tamanho, not args.sem_mascara)
        log.info("concluido: %d imagem(ns) refinada(s)", total)
        return 0

    try:
        while True:
            varrer_uma_vez(raiz, args.tamanho, not args.sem_mascara)
            time.sleep(args.intervalo)
    except KeyboardInterrupt:
        log.info("encerrado")
    return 0


if __name__ == "__main__":
    sys.exit(main())
