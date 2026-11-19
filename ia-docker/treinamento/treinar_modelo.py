"""
Treina o classificador aprovada x reprovada usando as pastas REFINADAS e exporta TFLite.

Entrada (dentro de --dados):
    aprovada_refinada/*.png     -> classe 0 "aprovadas"
    reprovada_refinada/*.png    -> classe 1 "reprovadas"

Saida:
    --saida (padrao modelo_inspecao_novo.tflite) + metricas_treino.json
    O modelo recebe float32 0-255 em 180x180x3 (Rescaling 1/255 dentro dele) e devolve
    softmax com 2 valores, na ordem CLASSES do api/main.py. Assim e substituivel direto.

Nao sobrescreve o modelo atual: copie o novo para api/ somente depois de testar.

Uso (Windows, Python 3.11):
    py -3.11 -m pip install -r requirements.txt
    py treinar_modelo.py --dados ..\\dados --epocas 25
"""
import argparse
import json
import os
from pathlib import Path

import numpy as np
import tensorflow as tf

IMG = 180
CLASSES = ["aprovadas", "reprovadas"]  # MESMA ordem de CLASSES em api/main.py
PASTAS = {"aprovadas": "aprovada_refinada", "reprovadas": "reprovada_refinada"}
EXTENSOES = {".png", ".jpg", ".jpeg"}


def listar_imagens(dados: Path):
    caminhos, rotulos = [], []
    for i, classe in enumerate(CLASSES):
        pasta = dados / PASTAS[classe]
        arquivos = []
        if pasta.is_dir():
            arquivos = sorted(p for p in pasta.iterdir() if p.is_file() and p.suffix.lower() in EXTENSOES)
        print(f"  {classe:<10} {len(arquivos):>5} imagens em {pasta}")
        caminhos += [str(p) for p in arquivos]
        rotulos += [i] * len(arquivos)
    if rotulos.count(0) < 10 or rotulos.count(1) < 10:
        raise SystemExit("Poucas imagens (minimo 10 por classe). Rode o refinador e confira as pastas *_refinada.")
    return np.array(caminhos), np.array(rotulos, dtype=np.int32)


def carregar(caminho, rotulo):
    bytes_img = tf.io.read_file(caminho)
    img = tf.image.decode_image(bytes_img, channels=3, expand_animations=False)
    img = tf.image.resize(img, (IMG, IMG), method=tf.image.ResizeMethod.AREA)  # como cv2.INTER_AREA
    return img, rotulo


def aumentar(img, rotulo):
    # Aumento so no treino: espelhamento e brilho leve (a tampinha pode vir em qualquer posicao)
    img = tf.image.random_flip_left_right(img)
    img = tf.image.random_flip_up_down(img)
    img = tf.image.random_brightness(img, max_delta=20.0)
    img = tf.clip_by_value(img, 0.0, 255.0)
    return img, rotulo


def montar_datasets(caminhos, rotulos, val_frac, lote, seed):
    rng = np.random.default_rng(seed)
    ordem = rng.permutation(len(caminhos))
    caminhos, rotulos = caminhos[ordem], rotulos[ordem]
    n_val = int(len(caminhos) * val_frac)
    val_c, val_r = caminhos[:n_val], rotulos[:n_val]
    tr_c, tr_r = caminhos[n_val:], rotulos[n_val:]

    def ds(c, r, treino):
        d = tf.data.Dataset.from_tensor_slices((c, r))
        d = d.map(carregar, num_parallel_calls=tf.data.AUTOTUNE)
        if treino:
            d = d.shuffle(len(c), seed=seed).map(aumentar, num_parallel_calls=tf.data.AUTOTUNE)
        return d.batch(lote).prefetch(tf.data.AUTOTUNE)

    return ds(tr_c, tr_r, True), ds(val_c, val_r, False), (val_c, val_r)


def criar_modelo():
    entrada = tf.keras.Input((IMG, IMG, 3))
    x = tf.keras.layers.Rescaling(1.0 / 255.0)(entrada)  # mesmo contrato do modelo atual
    for filtros in (32, 64, 128, 128):
        x = tf.keras.layers.Conv2D(filtros, 3, padding="same", activation="relu")(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.MaxPooling2D()(x)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    saida = tf.keras.layers.Dense(len(CLASSES), activation="softmax")(x)
    return tf.keras.Model(entrada, saida)


def pesos_classe(rotulos):
    contagem = np.bincount(rotulos, minlength=len(CLASSES))
    total = contagem.sum()
    return {i: float(total / (len(CLASSES) * c)) for i, c in enumerate(contagem)}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Treina aprovada x reprovada com as pastas *_refinada.")
    ap.add_argument("--dados", default="../dados", help="pasta que contem aprovada_refinada e reprovada_refinada")
    ap.add_argument("--saida", default="modelo_inspecao_novo.tflite")
    ap.add_argument("--epocas", type=int, default=25)
    ap.add_argument("--lote", type=int, default=32)
    ap.add_argument("--validacao", type=float, default=0.2)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args(argv)

    tf.keras.utils.set_random_seed(args.seed)
    dados = Path(args.dados).resolve()
    print(f"Lendo imagens de: {dados}")
    caminhos, rotulos = listar_imagens(dados)

    treino, validacao, (val_c, val_r) = montar_datasets(caminhos, rotulos, args.validacao, args.lote, args.seed)
    modelo = criar_modelo()
    modelo.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                   loss="sparse_categorical_crossentropy", metrics=["accuracy"])

    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=6, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=3),
    ]
    historico = modelo.fit(treino, validation_data=validacao, epochs=args.epocas,
                           class_weight=pesos_classe(rotulos), callbacks=callbacks, verbose=2)

    # Metricas na validacao (matriz de confusao simples)
    prob = modelo.predict(validacao, verbose=0)
    pred = prob.argmax(axis=1)
    matriz = np.zeros((len(CLASSES), len(CLASSES)), dtype=int)
    for real, p in zip(val_r, pred):
        matriz[real, p] += 1
    acertos = np.trace(matriz) / matriz.sum()
    print("\nMatriz de confusao (linhas = real, colunas = previsto):")
    print(f"              {CLASSES[0]:>12} {CLASSES[1]:>12}")
    for i, c in enumerate(CLASSES):
        print(f"{c:>12}  {matriz[i, 0]:>12} {matriz[i, 1]:>12}")
    print(f"Acuracia na validacao: {acertos * 100:.2f}%")

    # Exporta TFLite
    conversor = tf.lite.TFLiteConverter.from_keras_model(modelo)
    tflite = conversor.convert()
    saida = Path(args.saida).resolve()
    saida.write_bytes(tflite)
    print(f"\nModelo TFLite salvo em: {saida} ({saida.stat().st_size / 1e6:.1f} MB)")

    metricas = {
        "classes": CLASSES,
        "imagens_por_classe": {c: int((rotulos == i).sum()) for i, c in enumerate(CLASSES)},
        "imagens_total": int(len(rotulos)),
        "acuracia_validacao": round(float(acertos), 4),
        "matriz_confusao": matriz.tolist(),
        "epocas_executadas": len(historico.history["loss"]),
    }
    (saida.parent / "metricas_treino.json").write_text(json.dumps(metricas, indent=2, ensure_ascii=False),
                                                       encoding="utf-8")
    print(f"Metricas em: {saida.parent / 'metricas_treino.json'}")
    print("\nPROXIMO PASSO: teste o novo modelo antes de substituir o atual (ver LEIA-ME-IA.md).")


if __name__ == "__main__":
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    main()
