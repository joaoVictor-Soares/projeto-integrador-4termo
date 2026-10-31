"""
API de inspeção de peças (FastAPI + TensorFlow Lite).

Executar:
    uvicorn main:app --host 0.0.0.0 --port 8000

Endpoints:
    GET  /health            -> verifica se a API e o modelo estão ativos
    POST /predict           -> multipart/form-data, campo "file" (imagem)
    POST /predict/base64    -> JSON {"image": "<base64>"}
"""
import base64
import binascii
import io
import os
import threading
from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel
try:
    # Runtime recomendado (o tf.lite.Interpreter está deprecado)
    from ai_edge_litert.interpreter import Interpreter
except ImportError:
    from tensorflow.lite.python.interpreter import Interpreter

# ---------------------------------------------------------------------------
# Configuração
# ---------------------------------------------------------------------------
MODEL_PATH = os.getenv("MODEL_PATH", "modelo_inspecao.tflite")
IMG_HEIGHT = 180
IMG_WIDTH = 180
# Mesma ordem de train_ds.class_names (ordem alfabética das pastas)
CLASSES = ["aprovadas", "reprovadas"]
MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB

ACOES = {
    "aprovadas": "Sinalizar CLP via OPC-UA para continuar movimentação via CILINDRO DE DUPLA AÇÃO (Aprovada).",
    "reprovadas": "Sinalizar CLP via OPC-UA para acionar CILINDRO DE SIMPLES AÇÃO (Desviar peça).",
}


class ModelState:
    interpreter = None
    input_index = None
    output_index = None
    # O interpretador TFLite não é thread-safe: serializa as inferências
    lock = threading.Lock()


state = ModelState()


# ---------------------------------------------------------------------------
# Ciclo de vida: carrega o modelo UMA vez na inicialização
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    if not os.path.exists(MODEL_PATH):
        raise RuntimeError(f"Modelo não encontrado em: {os.path.abspath(MODEL_PATH)}")
    interpreter = Interpreter(model_path=MODEL_PATH)
    interpreter.allocate_tensors()
    state.interpreter = interpreter
    state.input_index = interpreter.get_input_details()[0]["index"]
    state.output_index = interpreter.get_output_details()[0]["index"]
    print(f"Modelo carregado: {os.path.abspath(MODEL_PATH)}")
    yield
    state.interpreter = None


app = FastAPI(title="API de Inspeção de Peças", version="1.0.0", lifespan=lifespan)


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
# Lógica de inferência
# ---------------------------------------------------------------------------
def preprocess(image_bytes: bytes) -> np.ndarray:
    """Replica o preprocess do notebook. O Rescaling(1/255) já está dentro do
    modelo, então a entrada deve ser float32 com valores 0-255."""
    try:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except (UnidentifiedImageError, OSError):
        raise HTTPException(status_code=400, detail="Arquivo não é uma imagem válida.")
    img = img.resize((IMG_WIDTH, IMG_HEIGHT))
    arr = np.asarray(img, dtype=np.float32)
    return np.expand_dims(arr, axis=0)  # (1, 180, 180, 3)


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
    return {"status": "ok", "model_loaded": state.interpreter is not None}


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
