import io
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import main  # noqa: E402

DATASET = ROOT / "database"


@pytest.fixture(scope="session")
def client():
    # "with" dispara o lifespan e carrega o modelo real uma única vez
    with TestClient(main.app) as c:
        yield c


def png_bytes(size=(32, 32), color=(10, 20, 30)):
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture
def imagem_valida():
    return png_bytes()
