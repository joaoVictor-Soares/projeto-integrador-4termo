"""
Prepara o modelo ao subir o Docker (servico "treino"):
  1. refina as fotos de aprovada/ e reprovada/ que ainda nao tem *_refinada;
  2. se houver pelo menos 10 imagens refinadas por classe, treina o modelo;
  3. se ja existe um modelo treinado com exatamente esses dados, nao treina de novo.

Sempre termina com codigo 0: sem dados suficientes, a API sobe com o modelo embutido.

Variaveis de ambiente:
  DADOS_DIR        pasta com aprovada/, reprovada/ e *_refinada/   (padrao /dados)
  MODELO_SAIDA     arquivo .tflite de saida                         (padrao /modelos/modelo_inspecao.tflite)
  EPOCAS           epocas de treino                                  (padrao 25)
  TREINAR_AO_SUBIR "false" para pular tudo                           (padrao true)
"""
import json
import logging
import os
import sys
from pathlib import Path

import refinador_tampinhas as refinador
import treinar_modelo as treino

log = logging.getLogger("preparar")
MINIMO_POR_CLASSE = 10


def contar_refinadas(dados: Path):
    contagem = {}
    for classe in treino.CLASSES:
        pasta = dados / treino.PASTAS[classe]
        arquivos = [p for p in pasta.iterdir() if p.is_file() and p.suffix.lower() in treino.EXTENSOES] \
            if pasta.is_dir() else []
        contagem[classe] = len(arquivos)
    return contagem


def ja_treinado_com(metricas: Path, contagem: dict) -> bool:
    if not metricas.exists():
        return False
    try:
        anterior = json.loads(metricas.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return False
    return anterior.get("imagens_por_classe") == contagem


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s", stream=sys.stdout)
    if os.getenv("TREINAR_AO_SUBIR", "true").lower() not in ("1", "true", "sim", "yes"):
        log.info("TREINAR_AO_SUBIR=false: nada a fazer.")
        return 0

    dados = Path(os.getenv("DADOS_DIR", "/dados"))
    saida = Path(os.getenv("MODELO_SAIDA", "/modelos/modelo_inspecao.tflite"))
    epocas = os.getenv("EPOCAS", "25")
    dados.mkdir(parents=True, exist_ok=True)
    saida.parent.mkdir(parents=True, exist_ok=True)

    log.info("Refinando fotos pendentes em %s ...", dados)
    novas = refinador.varrer_uma_vez(dados, 512, True)
    log.info("Fotos refinadas agora: %d", novas)

    contagem = contar_refinadas(dados)
    log.info("Imagens refinadas por classe: %s", contagem)
    if any(n < MINIMO_POR_CLASSE for n in contagem.values()):
        log.warning("Imagens refinadas insuficientes (minimo %d por classe). A API usara o modelo embutido.",
                    MINIMO_POR_CLASSE)
        return 0

    metricas = saida.parent / "metricas_treino.json"
    if saida.exists() and ja_treinado_com(metricas, contagem):
        log.info("Modelo ja treinado com estas imagens (%s). Nada a fazer.", saida)
        return 0

    log.info("Treinando com %d imagens (%d epocas)...", sum(contagem.values()), int(epocas))
    try:
        treino.main(["--dados", str(dados), "--saida", str(saida), "--epocas", epocas])
    except SystemExit as e:
        log.warning("Treino nao executado: %s. A API usara o modelo embutido.", e)
        return 0
    except Exception:
        log.exception("Falha no treino. A API usara o modelo embutido.")
        return 0
    log.info("Modelo pronto: %s", saida)
    return 0


if __name__ == "__main__":
    sys.exit(main())
