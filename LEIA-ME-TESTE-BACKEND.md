# Teste: câmera USB + IA + backend + ESP (Windows, mesma máquina)

Fluxo: o Java lê a câmera e detecta a tampinha na faixa vermelha, envia a foto para
`POST /api/inspecao/analisar` do backend. O backend classifica pela IA (Docker) e, se for
reprovada e `--atuar true`, aciona o cilindro pela COM7. O Java NÃO abre a COM7 neste modo.

Feche antes de começar: monitor serial do PlatformIO/Arduino e qualquer programa Java antigo.

## 1. Banco (Postgres no Docker)
```
docker run -d --name pg-inspecao -p 5432:5432 -e POSTGRES_DB=inspecao -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:16-alpine
```

## 2. IA (Docker)
```
cd C:\...\ia-docker
docker compose up -d --build
curl.exe http://localhost:8000/health
```

## 3. Backend local (PowerShell)
```
cd C:\...\backend-spring\demo
$env:ESP_PORTA="COM7"
$env:ESP_SIMULADO="false"
$env:ADMIN_SENHA="admin1234"
mvn spring-boot:run
```
Espere a linha de inicialização na porta 8080 e confira o ESP:
```
curl.exe -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@local\",\"senha\":\"admin1234\"}"
```

## 4. Compilar o Java (uma vez)
```
cd C:\...\captura-java
mvn dependency:copy-dependencies -DoutputDirectory=libs
mkdir out
javac -cp "libs\*" -d out CapturaTampinha.java
```

## 5. Teste SEM mover o cilindro (só classifica)
```
java -cp "out;libs\*" CapturaTampinha --backend http://localhost:8080 --email admin@local --senha admin1234 --atuar false --saida dados
```
Passe uma tampinha na frente da câmera. Deve aparecer `[BACKEND] status=... confianca=...`
e `[SALVO]` na pasta `dados\aprovada\` ou `dados\reprovada\`. Nada é acionado.

## 6. Teste REAL (classifica e recusa)
Mesmo comando com `--atuar true`. Para uma peça reprovada, o backend aciona o `NOK` cerca de
4 s depois de receber a foto (`inspecao.atraso-atuacao`). No log do backend aparece
`Enviado ao ESP: NOK`, e o firmware responde `[ESP] NOK recebido...`.

## 7. Parar
Digite `q` + ENTER no Java. Depois `Ctrl+C` no backend e `docker compose down` na IA.

## Atenção
- `--atuar true` aciona o cilindro de verdade e grava no banco. Comece sempre com `--atuar false`.
- O backend nunca foi compilado. Se o `mvn spring-boot:run` falhar, me mande o erro.
- O programa Java também nunca foi compilado com JavaCV. Se o `javac` falhar, me mande o erro.
- A detecção (HSV e faixas) é da bancada antiga. Confira a janela de teste antes de ligar a esteira.
