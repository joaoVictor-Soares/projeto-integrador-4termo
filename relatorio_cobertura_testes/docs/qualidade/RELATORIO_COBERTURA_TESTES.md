# Relatório de Cobertura de Testes

**Projeto:** Sistema Automatizado de Inspeção de Peças com IA e IoT
**Repositório:** https://github.com/joaoVictor-Soares/projeto-integrador-4termo
**Commit analisado:** `ded01bd` (revisão 2; a 1ª versão analisou `0df7b3e`, quando só o app existia)
**Data:** 30/10/2026

---

## 1. Resumo

O repositório tem hoje **três** bases de código. **Nenhuma tinha teste útil antes desta entrega.**

| Componente | Estado original | Com a suíte desta entrega | Medido? |
|---|---|---|---|
| **API de IA** (`ia-modelo/main.py`, Python) | 0% (nenhum teste) | **98% linhas (86/88) · 100% branches (12/12)** · 19 testes (14 passam, 5 falhas esperadas) | **Sim**, com o modelo `.tflite` real |
| **App React Native** (`app-react-native/src`) | 0% (0/91 statements) | 25,3% do `src/` (23/91) · **100% do código próprio** (10/10 statements; branches 1/2) · 25 testes | **Sim** |
| **Backend Spring** (`backend-spring/demo`, Java) | 1 teste (`contextLoads`), sem JaCoCo | **Não medido** | **Não**: o Maven Central está bloqueado no ambiente de análise |

**Leitura honesta dos números:**

- **98% na API de IA é um número real e merece confiança**: os testes carregam o modelo verdadeiro e exercitam os dois endpoints, os erros de entrada e o ciclo de vida. As 2 linhas descobertas (27-28) são o *fallback* de importação do TensorFlow, que só roda se o `ai-edge-litert` não estiver instalado.
- **Cobertura alta não significa modelo bom.** Os testes revelaram que o modelo erra uma imagem do próprio treino e aprova imagens sem peça (SEC-15). Esses defeitos estão registrados como testes `xfail`, não escondidos.
- **No app, ~90% das instruções medidas são template do Expo** (tela "Explore", abas, animações). O código do projeto são só `OperadorScreen.js`, `api.js` e `app/index.tsx`, 100% cobertos, mas são poucos e simples. Veja a recomendação de remover o template (seção 5).
- **Backend Java: não consegui medir.** Rodar `./mvnw verify` localmente é o próximo passo (seção 6). Por inspeção, o único teste (`DemoApplicationTests.contextLoads`) **provavelmente falha** porque o `pom.xml` inclui `spring-boot-starter-data-jpa` sem driver de banco e o `application.properties` só tem o nome da aplicação. Isto é inferência, **não medição**.
- **Como a cobertura "antes" foi medida:** instalei o Jest/pytest em cópias temporárias; no app usei um teste vazio só para forçar a instrumentação de todos os arquivos (resultado 0/91), e na API Python o `main.py` tem 88 statements e nenhum teste o importava.

---

## 2. API de IA (Python / FastAPI) — detalhe

**Ferramentas:** pytest 9 + pytest-cov + `fastapi.testclient`. O runtime TFLite usado nos testes foi o `ai-edge-litert` (o `main.py` o prefere ao TensorFlow), com versões registradas em `docs/seguranca/python-versoes-resolvidas-2026-10-30.txt`.
**Comando:** `cd ia-modelo && python -m pytest --cov=main --cov-branch --cov-report=term-missing`

| Arquivo | Statements | Branches | Cobertura | Linhas descobertas |
|---|---|---|---|---|
| `main.py` | 88 (86 cobertos) | 12 (12 cobertos) | **98%** | 27–28 (`except ImportError`: *fallback* para o TensorFlow) |

**O que os 19 testes verificam** (`ia-modelo/tests/test_main.py`, `conftest.py`):

| Grupo | Testes |
|---|---|
| Saúde e ciclo de vida | `/health` com modelo carregado; falha de inicialização se o arquivo do modelo não existe; HTTP 503 se o modelo não está carregado |
| Inferência real | Estrutura da resposta (`status`, `confianca`, `probabilidades` somando 100%, `acao`); as 4 imagens do dataset |
| Entradas inválidas | Arquivo vazio e não-imagem (400); acima de 10 MB (413) nos dois endpoints; base64 inválido (400); data URL aceita |
| **Falhas esperadas (`xfail strict`)** | Peça reprovada branca classificada como aprovada · baixa confiança aprova automaticamente · imagem sem peça vira "aprovada" · imagem "bomba" gera HTTP 500 · API sem autenticação |

**Como ler os `xfail strict`:** cada um descreve o comportamento **correto** que o sistema ainda não tem. A suíte fica verde hoje e **quebra de propósito no dia em que o defeito for corrigido**, obrigando a remover o `xfail`. Assim as lacunas do relatório OWASP (SEC-13, 14, 15) ficam rastreáveis no CI. Resultado atual: **14 passam, 5 `xfail`**.

**Limites do que os testes provam:**

- As 4 imagens do dataset são as **mesmas do treino**; acertá-las não prova generalização. Para o modelo, o que falta é um **conjunto de teste independente** (seção 6).
- Não há testes de carga/concorrência (o `Lock` do interpretador não foi exercitado em paralelo).

Evidências: `docs/qualidade/evidencias/python/coverage.json` e `htmlcov/index.html`.

---

## 3. App React Native — detalhe

**Ferramentas:** Jest 30 + `jest-expo` 57 + React Native Testing Library 13. **Escopo:** `src/**/*.{js,jsx,ts,tsx}` (19 arquivos, incluindo as variantes `.web`). **Comando:** `npx jest --coverage` → 4 suítes, 25 testes (23 passam, 2 `todo`), ~2 s.

| Visão | Statements | Branches | Funções | Linhas |
|---|---|---|---|---|
| Todo o `src/` | 25,27% (23/91) | 41,53% (27/65) | 26,47% (9/34) | 24,13% (21/87) |
| Só código do projeto (`OperadorScreen.js`, `api.js`, `app/index.tsx`) | 100% (10/10) | 50% (1/2) | 100% (6/6) | 100% (8/8) |

| Arquivo | Stmts | Branch | Funções | Origem | Observação |
|---|---|---|---|---|---|
| `screens/OperadorScreen.js` | 100% | 50% | 100% | **Projeto** | ramo `statusEsteira !== 'ATIVA'` (linha 17) nunca ocorre: `setStatusEsteira` não é chamado (SEC-04) |
| `services/api.js` | 100% | 100% | 100% | **Projeto** | uma instrução (instância do axios) |
| `app/index.tsx` | 100% | 100% | 100% | **Projeto** | |
| `components/themed-text.tsx`, `themed-view.tsx`, `hooks/use-theme.ts` | 100% | 100% | 100% | Template | |
| `constants/theme.ts` | 100% | 50% | 100% | Template | ramo de plataforma |
| `_layout.tsx`, `explore.tsx`, `animated-icon*.tsx`, `app-tabs*.tsx`, `external-link.tsx`, `hint-row.tsx`, `web-badge.tsx`, `ui/collapsible.tsx`, `use-color-scheme*.ts` | 0% | — | 0% | Template | sem testes; candidatos a remoção |

| Arquivo de teste | O que verifica |
|---|---|
| `src/screens/__tests__/OperadorScreen.test.js` | Renderização; `+10%`/`-10%`; limites 0% e 100%; **1 `failing` (A01)** e **1 `todo` (A06)** |
| `src/services/__tests__/api.test.js` | `timeout` 5 s; `baseURL` `:8080/api`; **2 `failing` (A04 HTTPS, A07 token)** e **1 `todo` (A09)** |
| `src/__tests__/home.test.js` | A Home renderiza a tela do operador (fora de `src/app/` para não virar rota do Expo Router) |
| `src/components/__tests__/themed.test.js` | Cores de `ThemedText`/`ThemedView` nos temas claro, escuro e `unspecified` |

`it.failing` é o equivalente Jest do `xfail strict`: o teste descreve o controle de segurança ainda inexistente e quebra quando ele for implementado.

Evidências: `docs/qualidade/evidencias/app-react-native/` (`coverage-summary.json`, `lcov.info`, `lcov-report/index.html`).

---

## 4. Backend Spring (Java) — situação e limites

| Arquivo | Linhas | Teste | Observação |
|---|---|---|---|
| `DemoApplication.java` | 13 | `contextLoads` (provavelmente falha) | classe `main` |
| `model/PecasRecusadas.java` | 46 | nenhum | entidade JPA com Lombok (`@Data`) |
| `poc/ClpOpcUaPoc.java` | 129 | nenhum | PoC OPC-UA com menu de console; **faz I/O real com o CLP**, difícil de testar sem refatorar |

**Não medido.** Para uma estimativa honesta: o único teste é de contexto e, mesmo que passasse, executaria só a inicialização do Spring; o `ClpOpcUaPoc` (a maior parte do código) não tem nenhum teste. Antes de falar em percentual, é preciso (1) configurar um banco de teste (H2) para o `contextLoads` passar, (2) adicionar JaCoCo e (3) testar de fato as regras de negócio quando existirem (`InspectionOrchestratorService`, `OpcUaService`…, planejadas em `doc_pastas.txt`).

**Trecho para ativar o JaCoCo** (padrão do plugin; **não foi executado aqui**, valide com `./mvnw verify`). Em `<build><plugins>` do `pom.xml`:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution>
      <id>report</id><phase>verify</phase><goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```

O relatório sai em `target/site/jacoco/index.html`. Para o teste de contexto, adicione `com.h2database:h2` com `scope test` ou use Testcontainers (PostgreSQL).

---

## 5. Lacunas e riscos de qualidade

1. **Modelo de IA sem avaliação válida (o maior risco).** O dataset tem 4 imagens distintas × 8 cópias; a validação do notebook usa arquivos duplicados (vazamento). A "cobertura" de 98% não mede a qualidade do modelo. Falta conjunto de teste independente, matriz de confusão e meta de recall para "reprovadas" (SEC-15).
2. **Backend sem testes úteis e sem cobertura medida** (seção 4).
3. **Ramo morto em `OperadorScreen.js` (linha 17)** pelo mesmo motivo do SEC-04.
4. **`api.js` não é importado por nenhuma tela**: ainda não há integração app ↔ backend para testar.
5. **Template do Expo (~90% das instruções medidas do app) sem teste.** Remover (`npm run reset-project` ou manualmente) eleva a cobertura sem esforço e reduz superfície de ataque.
6. **Sem testes de integração/E2E** do fluxo crítico (sensor → câmera → IA → resultado → CLP → rejeição), que ainda não existe de ponta a ponta.
7. **CI:** não havia `.github/workflows`. Esta entrega inclui um `ci.yml` com três jobs (app, API de IA, backend Java).

---

## 6. Metas e plano

| Componente | Tipo de teste | Meta mínima sugerida | Motivo |
|---|---|---|---|
| **Modelo de IA** | Conjunto de **teste independente** (peças e fotos distintas, por iluminação e cor) + matriz de confusão | **Recall de "reprovadas" ≥ meta definida pelo projeto** (o erro caro é falso aprovado); teste de regressão a cada novo modelo | Decide o destino físico da peça |
| **API de IA** | pytest (já existente) | Manter ≥ 90% e converter cada `xfail` em teste normal ao corrigir SEC-13/14/15 | OWASP A06/A10 |
| **Backend – regras de decisão** (aprovar/reprovar, baixa confiança, timeouts) | JUnit 5 + Mockito, JaCoCo | **≥ 85%** linhas e branches nessas classes | Decide o acionamento do cilindro de rejeição |
| **Backend – segurança** (login, JWT, perfis, validação de comando) | `@SpringBootTest` + `spring-security-test` | **100% dos endpoints** com teste "sem token", "perfil errado", "perfil certo" | A01/A07 |
| **Backend – OPC-UA** | Servidor OPC-UA simulado (Eclipse Milo) | Cenários: conexão, queda, certificado inválido, valor fora de faixa | Falha de comunicação com o CLP |
| **Backend – persistência** | Testcontainers (PostgreSQL/MySQL) | Todas as consultas e constraints do DER | Integridade |
| **App mobile** | Jest (esta suíte) + E2E (Maestro/Detox) em login e visualização | **≥ 80%** no código do projeto; fluxos críticos com E2E | A07 |
| **CLP** | Teste de bancada documentado (aprovada, reprovada, falha de comunicação, parada de emergência) | 100% dos casos de segurança executados e registrados | Segurança física |

**Quality gate sugerido no CI:** falhar o pipeline se a cobertura de código novo ficar abaixo de 80% e se a auditoria de dependências encontrar item alto de runtime. No `ci.yml` desta entrega a auditoria do app e o job Java começam **não bloqueantes** (há achados abertos / build não verificado); remova `continue-on-error` quando estiverem resolvidos.

---

## 7. Como reproduzir

**API de IA** (Python 3.13):

```bash
cd ia-modelo
python -m venv .venv && . .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install fastapi uvicorn python-multipart numpy pillow ai-edge-litert -r requirements-dev.txt
python -m pytest --cov=main --cov-branch --cov-report=term-missing
```

**App React Native** (dentro de `app-react-native/`):

```bash
npm install -D jest@^30 jest-expo@~57.0.6 @react-native/jest-preset@0.86.3 \
  @testing-library/react-native@^13 react-test-renderer@19.2.3 @types/jest@^30 --legacy-peer-deps
# copie jest.config.js, __mocks__/ e os arquivos __tests__/ desta entrega
# em package.json > scripts:  "test": "jest",  "test:coverage": "jest --coverage"
npm run test:coverage
```

**Backend Java:** `cd backend-spring/demo && ./mvnw verify` (após adicionar JaCoCo e um banco de teste).

Notas de compatibilidade encontradas:

- `@react-native/jest-preset` precisa estar **na mesma versão do `react-native`** (0.86.3); a 0.88 falha com "Could not locate module react-native/setup-env".
- O Testing Library v14 exige o pacote extra `test-renderer`; por isso usei a v13 com `react-test-renderer`.
- `import '@/global.css'` (em `constants/theme.ts`) não é entendido pelo Jest; o `jest.config.js` mapeia `.css` para `__mocks__/styleMock.js`.
- Para o `tsc --noEmit` no CI é preciso o `expo-env.d.ts` (gerado pelo `expo start` e ignorado no Git); o workflow o recria.
- No `ia-modelo`, o `tensorflow` listado em `requirements.txt` não é necessário para os testes; o `ai-edge-litert` é o runtime que o `main.py` tenta primeiro.

---

## 8. Conclusão

A cobertura inicial do projeto era 0% em todas as frentes. Com esta entrega, a **API de IA chega a 98%** e o **código próprio do app a 100%**; o **backend Java segue sem medição** e é a próxima lacuna prática. O resultado mais valioso não é o percentual: os testes provaram, executando o código, que o modelo **aprova peça reprovada e imagens sem peça**, que a API **quebra com uma imagem "bomba"** e que ela **não exige autenticação**. Esses quatro pontos estão registrados como testes `xfail`/`failing` e viram requisitos de aceite das correções.
