# Backend Spring (Raspberry Pi) — Verificação de Peças

Fluxo: **CLP (sensor) → câmera → IA (`ia-modelo/main.py`) → banco → CLP (cilindro)**.

```
PlcPollingService ──gatilho──▶ InspecaoOrchestratorService
                                 ├─ CameraService (ffmpeg/v4l2)
                                 ├─ PythonMlClient ──HTTP──▶ ia-modelo/main.py  (/predict/base64)
                                 ├─ ProducaoService / PecasRecusadasService (PostgreSQL)
                                 └─ MesaService ──▶ ClpService (PLC4X / S7) ──▶ CLP
```

## Subir tudo (containers)
```bash
cd cloud-docker && cp .env.example .env && docker compose up -d --build
```

## Mapa de I/O
Ver `clp/Entrada.java` e `clp/Saida.java` (extraídos do PDF "Mesa Seletora FESTO Reformada").

## Endpoints principais
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/mesa/status` | Entradas/saídas, emergência, conexão com o CLP |
| POST | `/api/mesa/esteira/ligar` · `/desligar` | Liga/desliga a esteira |
| POST | `/api/mesa/cilindros/{1..3}/avancar` · `/recuar` | Comando manual dos cilindros |
| POST | `/api/mesa/leds/{vermelho\|amarelo\|verde\|azul}?ligado=` | LEDs da botoeira |
| POST | `/api/mesa/reset` | Desliga todas as saídas |
| POST | `/api/inspecao/disparar?atuar=` | Captura + IA (+ banco + CLP se `atuar=true`) |
| POST | `/api/inspecao/analisar` (multipart `file`) | Classifica imagem enviada, sem câmera |
| GET | `/api/inspecao/status` · `/ultimo` | Estado do pipeline / último resultado |
| CRUD | `/api/esteiras` | Esteiras |
| GET/POST | `/api/producao/esteira/{id}` · `/atual` · `/iniciar` | Contadores de produção |
| GET | `/api/pecas-recusadas` · `/{id}` · `/{id}/imagem` | Peças reprovadas e fotos |

## Configuração
Todas as propriedades estão em `application.properties` e podem ser sobrescritas por variáveis de ambiente
(`CLP_CONNECTION_STRING`, `IA_BASE_URL`, `CAMERA_DEVICE`, `INSPECAO_SENSOR_GATILHO`, `INSPECAO_ATRASO_ATUACAO`, ...).
Para desenvolver sem CLP/câmera: `INSPECAO_POLLING=false` e `CAMERA_IMAGEM_SIMULADA=/caminho/img.jpg`.
