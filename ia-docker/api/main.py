"""
API de inspeção de peças (FastAPI + TensorFlow Lite), com refinamento antes da classificação.

Fluxo: imagem recebida -> refinar (localiza a tampinha, foca, recorta, amplia) -> classificar.
Variável de ambiente REFINAR=false desliga o refinamento (para comparar resultados).

Executar (local):
    uvicorn main:app --host 0.0.0.0 --port 8000

Endpoints:
    GET  /health            -> verifica se a API e o modelo estão ativos
    POST /predict           -> multipart/form-data, campo "file" (imagem)
    POST /predict/base64    -> JSON {"image": "<base64>"}
"""
import base64
import binascii
import os
import threading
from contextlib import asynccontextmanager

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, Request, UploadFile
from pydantic import BaseModel

from refinador_tampinhas import refinar

try:
    # Runtime recomendado (o tf.lite.Interpreter está deprecado)
    from ai_edge_litert.interpreter import Interpreter
except ImportError:
    from tensorflow.lite.python.interpreter import Interpreter

# ---------------------------------------------------------------------------
# Configuração
# ---------------------------------------------------------------------------
MODEL_PATH = os.getenv("MODEL_PATH", "/modelos/modelo_inspecao.tflite")  # treinado pelo servico "treino"
MODELO_EMBUTIDO = "modelo_inspecao.tflite"  # modelo de referencia, dentro da imagem
REFINAR = os.getenv("REFINAR", "true").lower() in ("1", "true", "sim", "yes")
TAMANHO_REFINO = 512   # saída do refinador antes da classificação
IMG_HEIGHT = 180
IMG_WIDTH = 180
# Mesma ordem de train_ds.class_names (ordem alfabética das pastas de treino)
CLASSES = ["aprovadas", "reprovadas"]
MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB

ACOES = {
    "aprovadas": "Sinalizar CLP via OPC-UA para continuar movimentação via CILINDRO DE DUPLA AÇÃO (Aprovada).",
    "reprovadas": "Sinalizar CLP via OPC-UA para acionar CILINDRO DE SIMPLES AÇÃO (Desviar peça).",
}


class ModelState:
    interpreter = None
    modelo_em_uso = None
    input_index = None
    output_index = None
    # O interpretador TFLite não é thread-safe: serializa as inferências
    lock = threading.Lock()


state = ModelState()


# ---------------------------------------------------------------------------
# Ciclo de vida: carrega o modelo UMA vez na inicialização
# ---------------------------------------------------------------------------
def escolher_modelo() -> str:
    """Usa o modelo treinado com as suas fotos, se existir; senao, o modelo embutido."""
    if os.path.exists(MODEL_PATH):
        return MODEL_PATH
    if os.path.exists(MODELO_EMBUTIDO):
        print(f"AVISO: {MODEL_PATH} nao existe. Usando o modelo embutido ({MODELO_EMBUTIDO}).")
        return MODELO_EMBUTIDO
    raise RuntimeError("Nenhum modelo encontrado (nem treinado, nem embutido).")


@asynccontextmanager
async def lifespan(app: FastAPI):
    caminho = escolher_modelo()
    interpreter = Interpreter(model_path=caminho)
    state.modelo_em_uso = os.path.abspath(caminho)
    interpreter.allocate_tensors()
    state.interpreter = interpreter
    state.input_index = interpreter.get_input_details()[0]["index"]
    state.output_index = interpreter.get_output_details()[0]["index"]
    print(f"Modelo carregado: {state.modelo_em_uso} | refinamento: {REFINAR}")
    yield
    state.interpreter = None


app = FastAPI(title="API de Inspeção de Peças", version="1.1.0", lifespan=lifespan)


@app.middleware("http")
async def registrar_requisicao(request: Request, call_next):
    """DIAGNOSTICO TEMPORARIO: mostra o cabecalho de cada /predict que chega (ver log do container)."""
    if request.url.path.startswith("/predict"):
        print(f"[REQ] {request.method} {request.url.path} "
              f"content-type={request.headers.get('content-type')} "
              f"content-length={request.headers.get('content-length')} "
              f"transfer-encoding={request.headers.get('transfer-encoding')}", flush=True)
    return await call_next(request)


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------
class Base64Request(BaseModel):
    image: str  # base64 puro ou data URL ("data:image/jpeg;base64,...")


class PredictResponse(BaseModel):
    status: str
    confianca: float  # 0 a 100
    probabilidades: dict[str, float]
    acao: str


# ---------------------------------------------------------------------------
# Pré-processamento e inferência
# ---------------------------------------------------------------------------
def decodificar(image_bytes: bytes) -> np.ndarray:
    dados = np.frombuffer(image_bytes, dtype=np.uint8)
    img = cv2.imdecode(dados, cv2.IMREAD_COLOR)  # BGR
    if img is None:
        raise HTTPException(status_code=400, detail="Arquivo não é uma imagem válida.")
    return img


def preprocess(image_bytes: bytes) -> np.ndarray:
    """Decodifica, refina (se REFINAR=true) e prepara a entrada do modelo.
    O Rescaling(1/255) está dentro do modelo, então a entrada é float32 com valores 0-255."""
    img = decodificar(image_bytes)
    if REFINAR:
        try:
            img = refinar(img, tamanho=TAMANHO_REFINO)
        except Exception as e:
            raise HTTPException(status_code=422, detail=f"Falha no refinamento da imagem: {e}")
    img = cv2.resize(img, (IMG_WIDTH, IMG_HEIGHT), interpolation=cv2.INTER_AREA)
    rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB).astype(np.float32)
    return np.expand_dims(rgb, axis=0)  # (1, 180, 180, 3)


def run_inference(image_bytes: bytes) -> PredictResponse:
    if state.interpreter is None:
        raise HTTPException(status_code=503, detail="Modelo não carregado.")
    input_data = preprocess(image_bytes)

    with state.lock:
        state.interpreter.set_tensor(state.input_index, input_data)
        state.interpreter.invoke()
        output = state.interpreter.get_tensor(state.output_index)[0]

    idx = int(np.argmax(output))
    status = CLASSES[idx]
    return PredictResponse(
        status=status,
        confianca=round(float(output[idx]) * 100, 2),
        probabilidades={c: round(float(p) * 100, 2) for c, p in zip(CLASSES, output)},
        acao=ACOES[status],
    )


# ---------------------------------------------------------------------------
# Endpoints (def síncrono: o FastAPI roda em threadpool, sem bloquear o loop)
# ---------------------------------------------------------------------------
@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": state.interpreter is not None,
            "modelo": state.modelo_em_uso, "refinamento": REFINAR}


@app.post("/predict", response_model=PredictResponse)
def predict(file: UploadFile = File(...)):
    data = file.file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Imagem maior que 10 MB.")
    if not data:
        raise HTTPException(status_code=400, detail="Arquivo vazio.")
    return run_inference(data)


@app.post("/predict/base64", response_model=PredictResponse)
def predict_base64(body: Base64Request):
    payload = body.image
    if "," in payload and payload.lstrip().startswith("data:"):
        payload = payload.split(",", 1)[1]
    try:
        data = base64.b64decode(payload, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="Base64 inválido.")
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Imagem maior que 10 MB.")
    return run_inference(data)
