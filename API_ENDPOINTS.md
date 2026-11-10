# Documentação da API (backend Spring) — guia para o frontend

API REST da Raspberry Pi: controla a mesa seletora FESTO (via CLP), dispara a inspeção por IA e expõe o histórico.

- **Base URL:** `http://<ip-da-raspberry>:8080` (porta configurável por `SERVER_PORT`)
- **Formato:** JSON (`application/json`), exceto upload de imagem (multipart) e download de foto (`image/jpeg`)
- **Autenticação:** nenhuma por enquanto (Spring Security ainda não implementado). Use apenas na rede local.
- **CORS:** liberado para `/api/**` (métodos GET, POST, PUT, PATCH, DELETE, OPTIONS). Origens em `CORS_ORIGENS` (padrão `*`).
- **Datas:** `LocalDateTime` em ISO-8601 **sem fuso**, ex.: `"2026-11-09T18:30:15.123"` (horário do servidor).
- **Parâmetros booleanos de query:** `true` / `false`.

## Índice

| Grupo | Rotas |
|---|---|
| [Mesa (CLP)](#1-mesa-clp) | `GET /api/mesa/status`, `GET /api/mesa/conexao`, `POST /api/mesa/esteira/ligar`, `POST /api/mesa/esteira/desligar`, `POST /api/mesa/esteira/velocidade`, `POST /api/mesa/cilindros/{n}/avancar`, `POST /api/mesa/cilindros/{n}/recuar`, `POST /api/mesa/leds/{cor}`, `POST /api/mesa/reset` |
| [Inspeção](#2-inspeção) | `GET /api/inspecao/status`, `GET /api/inspecao/ultimo`, `POST /api/inspecao/disparar`, `POST /api/inspecao/analisar` |
| [Esteiras](#3-esteiras) | `GET/POST /api/esteiras`, `GET/PATCH/DELETE /api/esteiras/{id}` |
| [Produção](#4-produção) | `GET /api/producao/esteira/{id}`, `GET /api/producao/esteira/{id}/atual`, `POST /api/producao/esteira/{id}/iniciar` |
| [Peças recusadas](#5-peças-recusadas) | `GET /api/pecas-recusadas`, `GET /api/pecas-recusadas/{id}`, `GET /api/pecas-recusadas/{id}/imagem` |
| [Saúde](#6-saúde) | `GET /actuator/health` |

---

## Erros (padrão para todas as rotas)

Corpo no formato **RFC 7807** (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Service Unavailable",
  "status": 503,
  "detail": "Nao foi possivel conectar ao CLP (s7:tcp://192.168.0.1?rack=0&slot=1): ...",
  "instance": "/api/mesa/status"
}
```

Mostre `detail` ao usuário; use `status` para decidir o comportamento.

| Status | Quando acontece |
|---|---|
| `400` | Regra de negócio recusada (ex.: **emergência acionada**, status de esteira inválido, cilindro/cor inválidos, imagem vazia, velocidade não configurada) |
| `404` | Esteira, peça recusada, imagem ou registro de produção inexistente |
| `502` | API de IA indisponível/erro (só em rotas que propagam o erro) |
| `503` | CLP ou câmera inacessível |
| `500` | Erro interno **ou parâmetro com tipo errado/ausente** (ver [Limitações](#limitações-conhecidas)) |

---

## 1. Mesa (CLP)

### `GET /api/mesa/status`
Lê todas as entradas do CLP e devolve o estado da mesa. Ideal para *polling* (sugestão: a cada 1–2 s).
Se o CLP estiver offline **não dá erro**: retorna `200` com `clpConectado: false`.

**Resposta 200** — `MesaStatus`

```json
{
  "clpConectado": true,
  "emergencia": false,
  "esteiraLigada": true,
  "velocidade": null,
  "entradas": { "SENSOR_CAPACITIVO": false, "BOTAO_EMERGENCIA": false, "...": false },
  "saidas":   { "ESTEIRA": true, "LED_AZUL": true, "CILINDRO_1_AVANCA": false, "...": false },
  "lidoEm": "2026-11-09T18:30:15.123"
}
```

| Campo | Tipo | Observação |
|---|---|---|
| `clpConectado` | boolean | `false` = CLP não respondeu (`entradas` vem `{}`) |
| `emergencia` | boolean | Botão de emergência acionado |
| `esteiraLigada` | boolean | Último comando enviado à esteira (espelho do backend) |
| `velocidade` | number \| null | `null` se o endereço analógico não estiver configurado |
| `entradas` | `{ [nome]: boolean }` | Chaves em [Entradas](#entradas-do-clp) |
| `saidas` | `{ [nome]: boolean }` | Último valor comandado, chaves em [Saídas](#saídas-do-clp) |
| `lidoEm` | string (ISO) | Momento da leitura |

### `GET /api/mesa/conexao`
Tenta conectar ao CLP. **Resposta 200:** `{ "conectado": true }`

### `POST /api/mesa/esteira/ligar`
Liga a esteira (acende LED azul). Sem corpo. **Resposta 200:** `MesaStatus`.
Erros: `400` (emergência ativa), `503` (CLP offline).

### `POST /api/mesa/esteira/desligar`
Desliga a esteira. Sem corpo. **Resposta 200:** `MesaStatus`. Erro: `503`.

### `POST /api/mesa/esteira/velocidade?valor={int}`
Define a velocidade (analógica). Só funciona se `clp.endereco-velocidade-escrita` estiver configurado.

| Query | Tipo | Obrigatório |
|---|---|---|
| `valor` | integer | sim |

**Resposta 200:** `{ "velocidade": 80 }` · Erros: `400` (não configurado ou emergência), `503`.

### `POST /api/mesa/cilindros/{numero}/avancar`
### `POST /api/mesa/cilindros/{numero}/recuar`
Comando manual dos cilindros. `{numero}` = `1`, `2` ou `3` (3 é o de dupla ação).
Cilindros 1 e 2 (simples ação): *avançar* energiza a bobina; *recuar* a desenergiza (retorno por mola).
**Resposta 200:** `MesaStatus` · Erros: `400` (número inválido ou emergência), `503`.

### `POST /api/mesa/leds/{cor}?ligado={boolean}`
`{cor}` = `vermelho` | `amarelo` | `verde` | `azul` (case-insensitive).

| Query | Tipo | Obrigatório |
|---|---|---|
| `ligado` | boolean | sim |

**Resposta 200:** `MesaStatus` · Erros: `400` (cor inválida), `503`.

### `POST /api/mesa/reset`
Desliga **todas** as saídas (equivale ao botão RESET físico) e marca a esteira como `PAUSADA`. Sem corpo. **Resposta 200:** `MesaStatus`.

#### Entradas do CLP
`SENSOR_CAPACITIVO`, `SENSOR_INDUTIVO`, `BARREIRA_PECA_PEQUENA`, `BARREIRA_PECA_MEDIA`, `BARREIRA_PECA_GRANDE`, `BOTAO_LIGA`, `BOTAO_DESLIGA`, `BOTAO_RESET`, `BOTAO_EMERGENCIA`, `CHAVE_DUAS_POSICOES`, `FIM_DE_CURSO`, `RAMPA_1_CHEIA`, `RAMPA_2_CHEIA`, `RAMPA_3_CHEIA`, `SENSOR_COR_1`, `SENSOR_COR_2`, `SENSOR_COR_3`, `CILINDRO_1_RECUADO`, `CILINDRO_1_AVANCADO`, `CILINDRO_2_RECUADO`, `CILINDRO_2_AVANCADO`, `CILINDRO_3_RECUADO`, `CILINDRO_3_AVANCADO`

#### Saídas do CLP
`CILINDRO_1_AVANCA`, `CILINDRO_2_AVANCA`, `CILINDRO_3_AVANCA`, `CILINDRO_3_RECUA`, `ESTEIRA`, `LED_VERMELHO`, `LED_AMARELO`, `LED_VERDE`, `LED_AZUL`

---

## 2. Inspeção

Pipeline: câmera → IA (`main.py`) → banco → CLP. Normalmente disparado sozinho pelo sensor da mesa; estas rotas servem para acompanhar e testar.

### `GET /api/inspecao/status`
**Resposta 200** — `InspecaoStatus`

```json
{
  "pollingHabilitado": true,
  "iaDisponivel": true,
  "pendentes": 0,
  "totalInspecionadas": 42,
  "ultimoResultado": { "...": "ver ResultadoInspecao" }
}
```

| Campo | Tipo | Observação |
|---|---|---|
| `pollingHabilitado` | boolean | Leitura automática do sensor ligada |
| `iaDisponivel` | boolean | `/health` da IA respondeu com modelo carregado |
| `pendentes` | integer | Inspeções na fila |
| `totalInspecionadas` | integer | Desde que o backend iniciou (zera ao reiniciar) |
| `ultimoResultado` | `ResultadoInspecao` \| null | `null` se ainda não houve inspeção |

### `GET /api/inspecao/ultimo`
**200:** `ResultadoInspecao` · **204 (sem corpo):** nenhuma inspeção ainda.

### `POST /api/inspecao/disparar`
Disparo manual: captura da câmera → IA → (opcional) banco + CLP. **Síncrono**: pode levar alguns segundos (use *loading* e timeout ≥ 20 s).

| Query | Tipo | Padrão | Descrição |
|---|---|---|---|
| `atuar` | boolean | `true` | `false` = só classifica, **sem** gravar no banco e **sem** mover cilindros |
| `operadorId` | integer | — | Usuário a registrar na peça recusada (opcional) |

Sem corpo. **Resposta 200:** `ResultadoInspecao`. Erro `400` se `atuar=true` com emergência ativa.
Se a câmera ou a IA falharem, a resposta é **200** com `status: "erro"` e o motivo em `erro`.

### `POST /api/inspecao/analisar`
Classifica uma imagem enviada (sem usar a câmera).

- **Content-Type:** `multipart/form-data`
- **Campos:**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `file` | arquivo de imagem | sim | JPEG ou PNG (qualquer formato que o Pillow leia). Máx. **10 MB** |
| `atuar` | boolean | não (padrão `false`) | Grava no banco e comanda o CLP se `true` |
| `operadorId` | integer | não | — |

`atuar`/`operadorId` vão como campos do form ou query string.
**Resposta 200:** `ResultadoInspecao` · Erros: `400` (arquivo vazio).

```ts
// React Native
const form = new FormData();
form.append('file', { uri, name: 'peca.jpg', type: 'image/jpeg' } as any);
const r = await fetch(`${BASE}/api/inspecao/analisar`, { method: 'POST', body: form });
```

#### `ResultadoInspecao`

```json
{
  "origem": "CLP:SENSOR_CAPACITIVO",
  "dataHora": "2026-11-09T18:30:15.123",
  "status": "reprovadas",
  "confianca": 97.42,
  "probabilidades": { "aprovadas": 2.58, "reprovadas": 97.42 },
  "acaoIa": "Sinalizar CLP via OPC-UA para acionar CILINDRO DE SIMPLES AÇÃO (Desviar peça).",
  "atuacaoAgendada": true,
  "pecaRecusadaId": 17,
  "erro": null
}
```

| Campo | Tipo | Observação |
|---|---|---|
| `origem` | string | `CLP:<SENSOR>`, `MANUAL` ou `UPLOAD` |
| `dataHora` | string (ISO) | Início da inspeção |
| `status` | `"aprovadas"` \| `"reprovadas"` \| `"erro"` | Note o plural, igual ao `main.py` |
| `confianca` | number \| null | **0 a 100** (percentual) |
| `probabilidades` | `{ aprovadas: number, reprovadas: number }` \| null | 0 a 100 |
| `acaoIa` | string \| null | Texto informativo da IA |
| `atuacaoAgendada` | boolean | O CLP vai atuar (ou atuou) |
| `pecaRecusadaId` | integer \| null | Preenchido só para reprovadas gravadas (`atuar=true`) |
| `erro` | string \| null | Motivo se `status = "erro"`, ou falha ao gravar no banco |

---

## 3. Esteiras

#### `Esteira`
```json
{ "id": 1, "nomeIdentificador": "Linha Principal 01", "velocidadeAtual": 80, "status": "ATIVA", "dataAlteracao": "2026-11-09T18:00:00" }
```
`status`: `"ATIVA"` | `"PAUSADA"` | `"MANUTENCAO"`.

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `GET` | `/api/esteiras` | — | `200` `Esteira[]` |
| `GET` | `/api/esteiras/{id}` | — | `200` `Esteira` · `404` |
| `POST` | `/api/esteiras` | `EsteiraRequest` | `201` `Esteira` · `400` |
| `PATCH` | `/api/esteiras/{id}` | `EsteiraRequest` (parcial) | `200` `Esteira` · `400` · `404` |
| `DELETE` | `/api/esteiras/{id}` | — | `204` · `404` |

**`EsteiraRequest`** (`Content-Type: application/json`) — no `PATCH`, campos ausentes/nulos são ignorados:

```json
{ "nomeIdentificador": "Linha 02", "velocidadeAtual": 60, "status": "PAUSADA" }
```

| Campo | Tipo | `POST` |
|---|---|---|
| `nomeIdentificador` | string | **obrigatório** |
| `velocidadeAtual` | integer | opcional (padrão `0`) |
| `status` | string | opcional (padrão `PAUSADA`); aceita minúsculas, é normalizado |

> A esteira de id configurado em `INSPECAO_ESTEIRA_ID` (padrão `1`) é criada automaticamente na primeira inspeção.

---

## 4. Produção

#### `DadosProducao`
```json
{
  "id": 101, "esteiraId": 1, "dataHoraInicio": "2026-11-09T08:00:00",
  "totalProcessadas": 500, "totalAprovadas": 485, "totalRecusadas": 15, "taxaRecusa": 3.0
}
```
`taxaRecusa` = percentual de recusadas (0–100, 2 casas).

| Método | Rota | Resposta |
|---|---|---|
| `GET` | `/api/producao/esteira/{esteiraId}` | `200` `DadosProducao[]` (mais recente primeiro) · `404` esteira |
| `GET` | `/api/producao/esteira/{esteiraId}/atual` | `200` `DadosProducao` (rodada corrente) · `404` esteira ou sem registro |
| `POST` | `/api/producao/esteira/{esteiraId}/iniciar` | `201` `DadosProducao` zerado (abre nova rodada) · `404` |

Sem corpo nos `POST`. Os contadores sobem sozinhos a cada inspeção com `atuar=true`.

---

## 5. Peças recusadas

#### `PecaRecusada`
```json
{
  "id": 17, "esteiraId": 1, "operadorId": null,
  "dataHora": "2026-11-09T18:30:15.123",
  "imagemUrl": "/api/pecas-recusadas/17/imagem",
  "erroIdentificado": "Reprovada pela IA",
  "nivelAcuracia": 0.9742
}
```

| Campo | Tipo | Observação |
|---|---|---|
| `operadorId` | integer \| null | |
| `imagemUrl` | string \| null | **Caminho relativo**: concatene com a base URL |
| `nivelAcuracia` | number | **0 a 1** (diferente de `confianca`, que é 0–100) |

| Método | Rota | Query | Resposta |
|---|---|---|---|
| `GET` | `/api/pecas-recusadas` | `esteiraId` (integer, opcional) | `200` `PecaRecusada[]` (mais recente primeiro) |
| `GET` | `/api/pecas-recusadas/{id}` | — | `200` `PecaRecusada` · `404` |
| `GET` | `/api/pecas-recusadas/{id}/imagem` | — | `200` **`image/jpeg`** (binário) · `404` |

```tsx
<Image source={{ uri: `${BASE}${peca.imagemUrl}` }} style={{ width: 200, height: 200 }} />
```

---

## 6. Saúde

### `GET /actuator/health`
**200:** `{ "status": "UP" }` — usado pelo Docker. Fora do prefixo `/api` (sem CORS liberado).

---

## Tipos TypeScript (copiar para o app)

```ts
export type StatusEsteira = 'ATIVA' | 'PAUSADA' | 'MANUTENCAO';

export interface Problem { type: string; title: string; status: number; detail: string; instance: string }

export interface MesaStatus {
  clpConectado: boolean; emergencia: boolean; esteiraLigada: boolean;
  velocidade: number | null;
  entradas: Record<string, boolean>; saidas: Record<string, boolean>;
  lidoEm: string;
}

export interface ResultadoInspecao {
  origem: string; dataHora: string;
  status: 'aprovadas' | 'reprovadas' | 'erro';
  confianca: number | null;                       // 0-100
  probabilidades: { aprovadas: number; reprovadas: number } | null;
  acaoIa: string | null; atuacaoAgendada: boolean;
  pecaRecusadaId: number | null; erro: string | null;
}

export interface InspecaoStatus {
  pollingHabilitado: boolean; iaDisponivel: boolean;
  pendentes: number; totalInspecionadas: number;
  ultimoResultado: ResultadoInspecao | null;
}

export interface Esteira {
  id: number; nomeIdentificador: string; velocidadeAtual: number | null;
  status: StatusEsteira; dataAlteracao: string | null;
}
export type EsteiraRequest = Partial<Pick<Esteira, 'nomeIdentificador' | 'velocidadeAtual' | 'status'>>;

export interface DadosProducao {
  id: number; esteiraId: number; dataHoraInicio: string;
  totalProcessadas: number; totalAprovadas: number; totalRecusadas: number;
  taxaRecusa: number;                             // 0-100
}

export interface PecaRecusada {
  id: number; esteiraId: number; operadorId: number | null;
  dataHora: string; imagemUrl: string | null;
  erroIdentificado: string; nivelAcuracia: number; // 0-1
}
```

---

## Limitações conhecidas

- **Sem autenticação** (Spring Security ainda não implementado) e **sem endpoints de usuários/login**, apesar da tabela `usuarios` existir.
- Parâmetro de tipo errado ou ausente (ex.: `/cilindros/abc/avancar`, `velocidade` sem `valor`) e imagem acima de 10 MB retornam **500** em vez de 400/413.
- `DELETE /api/esteiras/{id}` de uma esteira que já tem produção ou peças recusadas falha com **500** (chave estrangeira).
- Notificações em tempo real (push/WebSocket) não existem: use *polling* em `/api/mesa/status` e `/api/inspecao/ultimo`.
- A API de IA (`main.py`, porta 8000) é de uso interno do backend; o app deve falar apenas com o Spring.
