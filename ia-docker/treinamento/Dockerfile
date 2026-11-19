# Servico "treino": refina as fotos e treina o modelo com as pastas *_refinada. Roda uma vez ao subir.
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TF_CPP_MIN_LOG_LEVEL=2 \
    DADOS_DIR=/dados \
    MODELO_SAIDA=/modelos/modelo_inspecao.tflite

RUN apt-get update \
 && apt-get install -y --no-install-recommends libglib2.0-0 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY refinador_tampinhas.py treinar_modelo.py preparar_modelo.py ./

CMD ["python", "preparar_modelo.py"]
