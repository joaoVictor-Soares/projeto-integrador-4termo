# IA de inspeção - rodar já com o treino automático (Windows + Docker Desktop)

## Estrutura

```
ia-docker\
  docker-compose.yml       sobe 3 serviços: treino, api e refinador
  dados\                   suas fotos: aprovada\ e reprovada\ (entrada); *_refinada\ (gerado)
  modelos\                 modelo treinado (gerado pelo treino)
  api\                     API de classificação (porta 8000) + refinador + modelo embutido
  treinamento\             serviço de treino: refina e treina ao subir
```

## 1. Colocar as fotos

Copie as fotos capturadas pelo programa Java:
- `ia-docker\dados\aprovada\` -> fotos de peças aprovadas
- `ia-docker\dados\reprovada\` -> fotos de peças reprovadas

Mínimo para treinar: 10 fotos em cada pasta. Quanto mais, melhor. Não use as fotos antigas com
overlay (linha vermelha e retângulo).

## 2. Subir tudo

No PowerShell, dentro de `ia-docker`:

```
docker compose up -d --build
```

O que acontece, em ordem:
1. **treino** refina as fotos que ainda não têm `*_refinada`, treina o modelo com as pastas
   `aprovada_refinada` e `reprovada_refinada`, grava em `modelos\modelo_inspecao.tflite` e termina.
   A primeira vez leva alguns minutos (o TensorFlow é grande).
2. **api** só sobe depois que o treino terminou. Usa `modelos\modelo_inspecao.tflite`. Se o treino
   não rodou (poucas fotos), usa o modelo embutido e aparece um aviso no log.
3. **refinador** fica observando `dados\aprovada\` e `dados\reprovada\` e refina as fotos novas.

Ver o que aconteceu:
```
docker compose logs treino
docker compose logs api
```

## 3. Conferir a API

```
curl.exe http://localhost:8000/health
```
Exemplo de resposta:
```json
{"status":"ok","model_loaded":true,"modelo":"/modelos/modelo_inspecao.tflite","refinamento":true}
```
Se `modelo` mostrar `modelo_inspecao.tflite` sem `/modelos/`, o treino não rodou e a API está com o
modelo embutido.

Testar uma foto:
```
curl.exe -F "file=@C:\fotos\teste.png" http://localhost:8000/predict
```

Documentação interativa: `http://localhost:8000/docs`.

## 4. Treinar de novo com mais fotos

Adicione fotos em `dados\aprovada\` e `dados\reprovada\`, espere o refinador processar e rode:

```
docker compose up treino
docker compose restart api
```

O treino só roda de novo se o número de imagens refinadas mudou. Se não mudou, aparece
"Modelo ja treinado com estas imagens. Nada a fazer."

Para ajustar o número de épocas, troque `EPOCAS` no `docker-compose.yml`.

## 5. Desligar o refinamento (para comparar)

No `docker-compose.yml`, na seção `api`, troque `REFINAR: "true"` por `REFINAR: "false"` e rode
`docker compose up -d api`. Compare o resultado das mesmas fotos nos dois modos.

## 6. Parar

```
docker compose down
```
Os dados em `dados\` e `modelos\` ficam no seu PC.

## 7. Erros comuns

| Sintoma | Causa provável |
|---|---|
| Log do treino: "Imagens refinadas insuficientes" | Menos de 10 fotos em alguma pasta, ou as fotos têm menos de 1,5 s de idade (espere e rode de novo) |
| `api` não sobe, aguardando | O `treino` ainda está rodando. Veja `docker compose logs treino` |
| `modelo` aparece como `modelo_inspecao.tflite` (sem `/modelos/`) | Treino não gerou o modelo; confira o log do `treino` |
| `Falha no treino` no log | Veja o erro completo no log; geralmente falta de memória do Docker (Settings > Resources) |
| Pasta `*_refinada` vazia | Pasta `dados` não liberada no Docker Desktop (Settings > Resources > File sharing) |
| Porta 8000 ocupada | Troque para `"8001:8000"` no compose |

## 8. Limites desta entrega

- O treino não foi executado com as suas fotos reais (aqui não há fotos). Os testes foram com imagens sintéticas:
  o caminho de código foi validado, mas a qualidade do modelo depende das suas fotos.
- O Docker não foi executado nesta sandbox. Os Dockerfiles e o compose não foram testados como imagens.
- A API ainda não é chamada pelo programa Java; a decisão de recusa continua pelo teclado na captura.
