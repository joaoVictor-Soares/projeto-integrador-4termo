"""Testes da API de inspeção (ia-modelo/main.py) usando o modelo TFLite real."""
import base64
import io

import pytest
from PIL import Image

import main
from conftest import DATASET, png_bytes

AMOSTRAS = [
    ("aprovadas/aprovada_azul.jpg", "aprovadas"),
    ("aprovadas/aprovada_branca.jpg", "aprovadas"),
    ("reprovadas/recusada_azul.jpg", "reprovadas"),
    pytest.param(
        "reprovadas/recusada_branca.jpg",
        "reprovadas",
        marks=pytest.mark.xfail(
            strict=True,
            reason="DEFEITO REAL DO MODELO: peça reprovada classificada como aprovada (~55%). "
            "Dataset com só 4 imagens distintas; retreinar e reavaliar.",
        ),
    ),
]


# ---------------------------------------------------------------- saúde
def test_health_modelo_carregado(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "model_loaded": True}


# ------------------------------------------------------ /predict (multipart)
def test_predict_retorna_estrutura_valida(client, imagem_valida):
    r = client.post("/predict", files={"file": ("x.png", imagem_valida, "image/png")})
    assert r.status_code == 200
    j = r.json()
    assert j["status"] in main.CLASSES
    assert set(j["probabilidades"]) == set(main.CLASSES)
    assert sum(j["probabilidades"].values()) == pytest.approx(100, abs=0.1)
    assert j["acao"] == main.ACOES[j["status"]]


@pytest.mark.parametrize("arquivo,esperado", AMOSTRAS)
def test_modelo_classifica_imagens_do_proprio_treino(client, arquivo, esperado):
    """Sanidade mínima: o modelo deve acertar as próprias imagens de treino."""
    dados = (DATASET / arquivo).read_bytes()
    r = client.post("/predict", files={"file": (arquivo, dados, "image/jpeg")})
    assert r.status_code == 200
    assert r.json()["status"] == esperado


def test_predict_arquivo_vazio_400(client):
    r = client.post("/predict", files={"file": ("x.png", b"", "image/png")})
    assert r.status_code == 400


def test_predict_nao_imagem_400(client):
    r = client.post("/predict", files={"file": ("x.txt", b"isto nao e imagem", "text/plain")})
    assert r.status_code == 400


def test_predict_acima_de_10mb_413(client, monkeypatch):
    monkeypatch.setattr(main, "MAX_UPLOAD_BYTES", 1024)
    r = client.post("/predict", files={"file": ("x.bin", b"0" * 2048, "application/octet-stream")})
    assert r.status_code == 413


# -------------------------------------------------------- /predict/base64
def test_base64_valido(client, imagem_valida):
    b64 = base64.b64encode(imagem_valida).decode()
    r = client.post("/predict/base64", json={"image": b64})
    assert r.status_code == 200


def test_base64_data_url(client, imagem_valida):
    b64 = base64.b64encode(imagem_valida).decode()
    r = client.post("/predict/base64", json={"image": f"data:image/png;base64,{b64}"})
    assert r.status_code == 200


def test_base64_invalido_400(client):
    r = client.post("/predict/base64", json={"image": "isto-nao-e-base64!!"})
    assert r.status_code == 400


def test_base64_acima_do_limite_413(client, imagem_valida, monkeypatch):
    monkeypatch.setattr(main, "MAX_UPLOAD_BYTES", 10)
    b64 = base64.b64encode(imagem_valida).decode()
    r = client.post("/predict/base64", json={"image": b64})
    assert r.status_code == 413


# ------------------------------------------------ ciclo de vida / estado
def test_inferencia_sem_modelo_503(monkeypatch, imagem_valida):
    monkeypatch.setattr(main.state, "interpreter", None)
    with pytest.raises(main.HTTPException) as exc:
        main.run_inference(imagem_valida)
    assert exc.value.status_code == 503


def test_lifespan_sem_arquivo_de_modelo_falha(monkeypatch):
    import asyncio

    monkeypatch.setattr(main, "MODEL_PATH", "/caminho/inexistente.tflite")

    async def subir():
        async with main.lifespan(main.app):
            pass

    with pytest.raises(RuntimeError):
        asyncio.run(subir())


# ------------------------------------------------------------------------
# Controles de segurança AINDA NÃO implementados (OWASP Top 10:2025).
# xfail(strict=True): hoje "falham como esperado"; quando o controle for
# implementado o teste passa a quebrar -> remova o xfail para ativá-lo.
# ------------------------------------------------------------------------
@pytest.mark.xfail(strict=True, reason="A06/A10: sem limiar de confiança; baixa confiança deveria ir para revisão manual")
def test_baixa_confianca_nao_aprova_automaticamente(client):
    dados = (DATASET / "reprovadas/recusada_branca.jpg").read_bytes()
    j = client.post("/predict", files={"file": ("x.jpg", dados, "image/jpeg")}).json()
    # a peça reprovada é classificada como aprovada com ~55% de confiança
    assert not (j["status"] == "aprovadas" and j["confianca"] < 80)


@pytest.mark.xfail(strict=True, reason="A10: DecompressionBombError não é tratado -> HTTP 500 em vez de 4xx")
def test_imagem_com_dimensoes_absurdas_retorna_4xx(client):
    buf = io.BytesIO()
    Image.new("1", (20000, 20000)).save(buf, format="PNG")  # ~400 Mpx, arquivo minúsculo
    r = client.post("/predict", files={"file": ("bomba.png", buf.getvalue(), "image/png")})
    assert 400 <= r.status_code < 500


@pytest.mark.xfail(strict=True, reason="A02/A07: API sem autenticação")
def test_exige_autenticacao(client, imagem_valida):
    r = client.post("/predict", files={"file": ("x.png", imagem_valida, "image/png")})
    assert r.status_code in (401, 403)


@pytest.mark.xfail(strict=True, reason="A06/A10: imagem sem peça (câmera tampada/esteira vazia) é classificada como aprovada")
def test_imagem_sem_peca_nao_vira_aprovada(client):
    r = client.post("/predict", files={"file": ("preta.png", png_bytes((640, 480), (0, 0, 0)), "image/png")})
    assert r.json()["status"] != "aprovadas"
