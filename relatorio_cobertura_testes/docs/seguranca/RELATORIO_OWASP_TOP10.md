# Relatório de Segurança — OWASP Top 10:2025

**Projeto:** Sistema Automatizado de Inspeção de Peças com IA e IoT (projeto integrador, 4º termo ADS)
**Repositório:** https://github.com/joaoVictor-Soares/projeto-integrador-4termo
**Commit analisado:** `ded01bd` (main, após o merge do PR #7 "feat: OWASP"). A 1ª versão deste relatório analisou o commit `0df7b3e`; esta revisão incorpora o backend Spring, a API Python de IA, o modelo e o DER atualizado.
**Data da análise:** 30/10/2026 (revisão 2)
**Referência:** OWASP Top 10:2025 (A01–A10)
**Tipo de análise:** revisão estática manual do código e da documentação, `npm audit` (app) e `pip-audit` (API Python), mais **execução real da API de IA com o modelo `.tflite`** (testes automatizados e sondagens de comportamento). O backend Java **não pôde ser compilado nem executado** neste ambiente (o Maven Central está bloqueado), portanto foi analisado só por leitura. Não houve DAST/pentest.

---

## 1. Resumo executivo

O repositório agora tem três partes com código: o **app React Native** (template do Expo + `OperadorScreen.js` + `api.js`), um **esqueleto de backend Spring Boot** (`backend-spring/demo`, com a entidade `PecasRecusadas` e uma prova de conceito OPC-UA, `ClpOpcUaPoc.java`) e a **API de IA em Python/FastAPI** (`ia-modelo/main.py` + `modelo_inspecao.tflite`). `cloud-docker` e `hardware` continuam só com arquivos-texto de marcação.

O relatório separa dois tipos de achado:

- **[Código]:** problema que existe hoje, com arquivo e linha (ou medição).
- **[Projeto]:** risco que decorre do que está documentado (README, `esquematico.jpg`, DER, `doc_pastas.txt`) e que surgirá quando o componente for implementado.

**Resultado:** 16 achados — 7 de severidade Alta, 8 Média e 1 Informativa. Nenhum é crítico **hoje**, porque nada está implantado e o app ainda não conversa com o backend (`src/services/api.js` não é importado em lugar nenhum). Mas a revisão 2 trouxe duas descobertas **medidas, não teóricas**, sobre o componente que é o coração do projeto:

1. **O modelo de IA aprova peça defeituosa.** Ao classificar as próprias imagens do dataset de treino, `recusada_branca.jpg` (peça **reprovada**) sai como **"aprovada" com 54,7% de confiança**, e a API aplica `argmax` sem nenhum limiar de confiança. Em um sistema cujo objetivo é desviar peças reprovadas, isso é a falha mais importante encontrada (SEC-15).
2. **A API de IA é iniciada em `0.0.0.0:8000` sem autenticação** (instrução no próprio `main.py`), e o OPC-UA do PoC conecta de forma **anônima e sem criptografia**, escrevendo direto nas saídas dos cilindros (SEC-13, SEC-08).

| Severidade | Qtde | Achados |
|---|---|---|
| Alta | 7 | SEC-01, SEC-02, SEC-03, SEC-07, SEC-08, SEC-13, SEC-15 |
| Média | 8 | SEC-04, SEC-05, SEC-06, SEC-09, SEC-10, SEC-11, SEC-14, SEC-16 |
| Informativa | 1 | SEC-12 |

> **Como ler a severidade.** Foi avaliada como "risco se este código/desenho fosse para produção como está", combinando probabilidade e impacto. É uma avaliação qualitativa do analista, não um score CVSS. Como o contexto é industrial (decisão de qualidade e atuadores pneumáticos), o impacto físico/operacional foi considerado.

---

## 2. Escopo e metodologia

| Componente | Estado no repositório | Como foi avaliado |
|---|---|---|
| `app-react-native/` | Código real (template Expo + `OperadorScreen.js` + `api.js`); **sem alterações** desde a 1ª análise | Leitura integral, busca por padrões de risco, `npm audit` |
| `backend-spring/demo/` | Esqueleto Spring Boot 4.1.1 / Java 21: `DemoApplication`, entidade `PecasRecusadas`, `ClpOpcUaPoc` (PoC OPC-UA), 1 teste `contextLoads`; `doc_pastas.txt` descreve a estrutura planejada | **Leitura estática** (não foi possível compilar/executar: Maven Central bloqueado) |
| `ia-modelo/` | API FastAPI (`main.py`), modelo `modelo_inspecao.tflite` (16 MB), notebook `obsoleto.ipynb`, `requirements.txt`, dataset (32 arquivos), `__pycache__` | Leitura, **execução real com o modelo** (19 testes), `pip-audit`, análise do notebook e do dataset |
| `cloud-docker/` | Apenas `bd.txt` | Só pelo desenho |
| `hardware/` (CLP S7-1500) | Apenas `embarcados.txt` | Só pelo desenho |
| `docs/` | README, `der.txt` (só reindentado), `der.png`, `esquematico.jpg`, lista de materiais | Leitura e extração de requisitos |

**Passos executados:** (1) inventário dos arquivos versionados (64 na 1ª análise; 114 no commit atual, a maior parte imagens do dataset); (2) leitura integral do código-fonte; (3) busca por segredos, URLs em texto claro, armazenamento inseguro, `eval`, HTML dinâmico; (4) `npm audit` no lockfile e rastreio do caminho de cada dependência vulnerável; (5) análise do diagrama e do DER contra cada categoria do Top 10:2025; (6) execução da API de IA com o modelo real, sondagem de entradas maliciosas e `pip-audit`; (7) leitura do notebook de treino e verificação de duplicidade do dataset por hash.

**Limitações:** sem execução do app em dispositivo; **backend Java não executado** (a afirmação de que `contextLoads` provavelmente falha é inferência, não medição); sem revisão de IaC/compose (inexistentes); sem análise do binário do modelo além de carregá-lo e inferir. O resultado de `npm audit`/`pip-audit` reflete as bases de advisories de 30/10/2026 e muda com o tempo.

---

## 3. Visão geral por categoria (OWASP Top 10:2025)

| # | Categoria | Situação | Achados |
|---|---|---|---|
| A01 | Broken Access Control | Achado no código + risco de projeto | SEC-02, SEC-03, SEC-07, SEC-10, SEC-13 |
| A02 | Security Misconfiguration | Achado no código + risco de projeto | SEC-01, SEC-06, SEC-07, SEC-08, SEC-13 |
| A03 | Software Supply Chain Failures | Achado no código | SEC-05, SEC-06 |
| A04 | Cryptographic Failures | Achado no código + risco de projeto | SEC-01, SEC-07, SEC-08, SEC-10 |
| A05 | Injection | Sem ponto de injeção no código atual; risco preventivo | ver seção 5 (A05); SEC-14 |
| A06 | Insecure Design | Achado no código | SEC-03, SEC-04, SEC-15, SEC-16 |
| A07 | Authentication Failures | Achado no código + risco de projeto | SEC-02, SEC-08, SEC-13 |
| A08 | Software or Data Integrity Failures | Achado no código | SEC-06, SEC-11, SEC-15 |
| A09 | Security Logging & Alerting Failures | Achado (ausência) | SEC-09 |
| A10 | Mishandling of Exceptional Conditions | Achado no código (medido) | SEC-04, SEC-14, SEC-15 |

---

## 4. Achados detalhados

Legenda de tipo: **[Código]** confirmado no repositório; **[Projeto]** risco derivado do desenho documentado.

### SEC-01 — Comunicação com o backend em HTTP puro, com IP fixo no código
- **Severidade:** Alta (latente) · **Tipo:** [Código] · **OWASP:** A04 Cryptographic Failures, A02 Security Misconfiguration
- **Evidência:** `app-react-native/src/services/api.js:4` → `baseURL: 'http://192.168.1.100:8080/api'`
- **Descrição:** a URL usa `http://` (sem TLS) e o IP da Raspberry Pi está embutido no código-fonte. Quando o app passar a enviar credenciais, tokens ou comandos, tudo trafegará em texto claro na rede local, que no desenho é uma rede de fábrica com celulares pessoais (BYOD).
- **Impacto:** interceptação e adulteração de tráfego (man-in-the-middle) por qualquer dispositivo na mesma rede Wi-Fi; roubo de token; comandos forjados.
- **Observação:** builds de produção costumam bloquear tráfego sem TLS por padrão (App Transport Security no iOS e restrição a cleartext no Android 9+). O risco é a equipe "resolver" isso liberando HTTP globalmente em vez de habilitar TLS.
- **Correção recomendada:**
  1. Servir a API com HTTPS (certificado próprio com CA interna da fábrica, ou certificado válido via domínio interno).
  2. Ler a URL de configuração de ambiente (`EXPO_PUBLIC_API_URL` via `app.config.js`/EAS), nunca fixa no código.
  3. Opcional: certificate pinning no app para a CA interna.
- **Teste que comprova:** `api.test.js` contém um teste marcado `it.failing` ("A04 - deve usar HTTPS"). Ele passa a ser exigível quando a correção for feita (basta trocar `it.failing` por `it`).

### SEC-02 — Aplicativo sem autenticação e sem controle de acesso por perfil
- **Severidade:** Alta · **Tipo:** [Código] + [Projeto] · **OWASP:** A07 Authentication Failures, A01 Broken Access Control
- **Evidência:** `src/app/index.tsx` renderiza `OperadorScreen` diretamente; não existe tela de login, guarda de rota, armazenamento de token nem verificação de perfil. O README (seção Segurança) reconhece: "Autenticação do app mobile e do acesso administrativo continua como item a ser detalhado".
- **Atualização (rev. 2):** o backend esboçado (`backend-spring/demo/pom.xml`) **não inclui `spring-boot-starter-security`**, embora `backend-spring/doc_pastas.txt` planeje um `SecurityConfig.java`. Sem essa dependência, qualquer controller futuro nasce aberto, escutando por padrão na porta 8080 em todas as interfaces.
- **Descrição:** qualquer pessoa que instale ou abra o app acessa a tela do operador e seus controles. O desenho prevê os perfis `OPERADOR` e `ADMIN` (tabela `usuarios.perfil` no DER) e uma área de Admin, mas nada disso existe no app.
- **Impacto:** acesso não autorizado a status, histórico e (no futuro) comandos; impossibilidade de atribuir ações a pessoas (ver SEC-09).
- **Correção recomendada:**
  1. Backend com Spring Security: login com e-mail/senha (hash forte), JWT de curta duração + refresh token rotativo, ou sessão opaca.
  2. App: guardar tokens com `expo-secure-store` (Keychain/Keystore), **nunca** em `AsyncStorage`.
  3. Rotas do Expo Router protegidas por grupo `(auth)`/`(app)`; menu/telas de Admin só para `perfil = ADMIN`.
  4. **A autorização é sempre reforçada no servidor**; esconder botão no app não é controle de acesso.
  5. Rate limiting e bloqueio progressivo de login no backend.
- **Teste que comprova:** `OperadorScreen.test.js` contém `it.failing` ("A01 - só deve permitir alterar a velocidade para perfil autorizado").

### SEC-03 — Controle de velocidade da esteira sem validação no servidor nem intertravamento
- **Severidade:** Alta · **Tipo:** [Código] + [Projeto] · **OWASP:** A06 Insecure Design, A01 Broken Access Control
- **Evidência:** `src/screens/OperadorScreen.js:27` e `:30` — os botões `-10%` e `+10%` alteram a velocidade (0–100%) apenas em estado local; o limite 0–100 é imposto só na interface.
- **Descrição:** hoje o botão não envia nada. Mas o desenho prevê que o app exiba/controle a máquina, e a própria documentação afirma, corretamente, que o CLP deve ser o único a acionar atuadores. O risco de projeto é a velocidade passar a ser um comando vindo de um celular pessoal sem validação independente.
- **Impacto:** valores fora de faixa, comandos repetidos ou forjados, velocidade incompatível com o estado da linha (ex.: manutenção), com consequência física sobre a esteira.
- **Correção recomendada:**
  1. Tratar o comando como dado não confiável: validar tipo, faixa, passo máximo e estado da esteira **no backend** e novamente **no CLP** (limites e intertravamentos no programa do S7-1500).
  2. Permissão específica para alterar velocidade (ex.: só `ADMIN` ou operador logado e escalado para a linha).
  3. Confirmação explícita para alterações relevantes e registro em auditoria (SEC-09).
  4. Parada de emergência e funções de segurança permanecem **fisicamente cabeadas no CLP/painel**, nunca dependentes do app ou da rede.
- **Teste que comprova:** `OperadorScreen.test.js` cobre o clamp 0–100 da UI (comportamento atual) e deixa `it.todo` para a validação no servidor.

### SEC-04 — Estado da esteira "fixo" na tela e nenhum tratamento de falhas de rede
- **Severidade:** Média · **Tipo:** [Código] · **OWASP:** A10 Mishandling of Exceptional Conditions, A06 Insecure Design
- **Evidência:** `OperadorScreen.js:5-6` inicializa `statusEsteira = 'ATIVA'` e `velocidade = 80` como valores literais; `setStatusEsteira` nunca é chamado, então o ramo vermelho (`:17`) jamais executa (por isso o teste mostra 50% de branches nesse arquivo). `api.js` define `timeout: 5000`, mas não há interceptors, tratamento de erro, estados de carregamento ou de "sem conexão".
- **Descrição:** quando a integração for ligada, se a Raspberry Pi ou a rede cair, nada impede que a tela continue mostrando "ATIVA" em verde. O sistema "falha aberto" na informação: o operador acredita que a linha está operando e inspecionando.
- **Impacto:** decisão operacional errada por informação desatualizada; peças reprovadas passando despercebidas se o alerta de rejeição não chegar.
- **Correção recomendada:**
  1. Estados explícitos: `CARREGANDO`, `SEM CONEXÃO`, `DADO DESATUALIZADO (há N s)`, `ERRO`. Nunca mostrar "ATIVA" sem dado recente do servidor.
  2. Interceptor de resposta no axios com mensagens de erro genéricas ao usuário e detalhe só em log.
  3. Retentativas com backoff e indicador de "última atualização".
  4. No backend: tratar timeouts do OPC-UA e da câmera de forma segura (falha → peça tratada como não aprovada/revisão, nunca "aprovada por padrão").

### SEC-05 — Dependências com vulnerabilidades conhecidas (18 no `npm audit`)
- **Severidade:** Média · **Tipo:** [Código] · **OWASP:** A03 Software Supply Chain Failures
- **Evidência:** `npm audit --package-lock-only` em 30/10/2026: **18 vulnerabilidades — 1 crítica, 9 altas, 3 moderadas, 5 baixas** (JSON completo em `docs/seguranca/npm-audit-2026-10-30.json`).
- **Análise de relevância real** (o `npm audit` não distingue ferramenta de build de código que roda no celular):

| Pacote | Sev. | Vem de | Roda no app instalado? | Observação |
|---|---|---|---|---|
| `shell-quote` | Crítica | `react-native` → `react-devtools-core` | Não (ferramenta de desenvolvimento) | Injeção de comando; exposição limitada à máquina do desenvolvedor |
| `follow-redirects` | Alta | `axios` | Pouco provável | Só é usado pelo adaptador Node do axios; o React Native usa o adaptador XHR. Atualizar mesmo assim |
| `decode-uri-component` | Alta | `expo-router` → `query-string` | Sim | Negação de serviço ao processar URL/parâmetros malformados (deep links) |
| `zod`, `node-forge`, `source-map-js` | Alta | `expo` → `@expo/cli`/`metro-config` | Não (toolchain) | Afetam o ambiente de build/desenvolvimento |
| `uuid`, `deepmerge` e correlatos | Moderada/Baixa | Toolchain do Expo | Não | Baixo risco prático |

- **Atenção:** a sugestão automática do `npm audit fix` aponta para `expo@51` (um *downgrade* de major), o que **não deve ser aplicado**. O caminho correto é atualizar para os patches mais recentes da linha do SDK 57 (ou para o próximo SDK quando estável) com `npx expo install --fix` e rodar `npx expo-doctor`.
- **Atualização (rev. 2) — novos ecossistemas:**
  - **Python (`ia-modelo/requirements.txt`):** `pip-audit` no ambiente resolvido em 30/10/2026 (fastapi 0.162.0, starlette 1.10.0, pillow 13.0.0, numpy 2.5.4, ai-edge-litert 2.3.0 etc.) **não encontrou vulnerabilidades conhecidas** (o `tensorflow`, listado em `requirements.txt`, não foi instalado na varredura por ser muito pesado; no lugar dele usei `ai-edge-litert`, que o `main.py` prefere quando disponível; o `requirements.txt` não lista esse pacote, então no seu ambiente o *fallback* para o TensorFlow é o que roda). Porém **nenhuma dependência tem versão fixada** (nem hashes), então o resultado de hoje não é reproduzível amanhã. O notebook foi executado com `tensorflow 2.21.0`, `numpy 2.5.3`, `pillow 12.3.0`; fixe versões conhecidas.
  - **Java (`pom.xml`):** a dependência do Eclipse Milo está em `0.6.8`, enquanto o comentário do PoC pede `0.6.15`; use a versão mais recente compatível e confirme com o Dependabot/OWASP Dependency-Check. O Maven Wrapper baixa o Maven por HTTPS **sem `distributionSha256Sum`** (`.mvn/wrapper/maven-wrapper.properties`), então a integridade da distribuição não é verificada.
- **Correção recomendada:**
  1. Atualizar `axios`, `expo` e `expo-router` para as versões corrigidas e regenerar o lockfile.
  2. Ativar Dependabot (arquivo `.github/dependabot.yml` incluso na entrega) e executar `npm audit --audit-level=high` no CI (workflow incluso).
  3. Manter `package-lock.json` versionado (já está, ponto positivo) e usar `npm ci` no CI.
  4. Fixar versões em `requirements.txt` (com `pip-compile --generate-hashes` ou `uv lock`), adicionar `distributionSha256Sum` ao wrapper e habilitar Dependabot para `pip` e `maven` (já incluído no `dependabot.yml` desta entrega); gerar SBOM (CycloneDX).

### SEC-06 — Higiene do repositório: sem `.gitignore` na raiz, `.env` não ignorado, sem CI nem política de segurança
- **Severidade:** Média · **Tipo:** [Código/Repositório] · **OWASP:** A02 Security Misconfiguration, A03 Supply Chain, A08 Integrity
- **Evidência:** há `.gitignore` em `app-react-native/` e (novo) em `backend-spring/demo/`, mas **não na raiz nem em `ia-modelo/` e `cloud-docker/`**. No app, a regra `.env*.local` ignora apenas `.env.local`, `.env.development.local` etc.; um arquivo **`.env` comum seria versionado**. Também **não há** `.github/` (CI, Dependabot, `SECURITY.md`) nem proteção de segredos.
- **Itens novos (rev. 2):** `ia-modelo/__pycache__/main.cpython-313.pyc` foi versionado (bytecode compilado não deve ir ao Git); `obsoleto.ipynb` foi commitado **com as saídas das células**, incluindo o caminho local `c:\Users\User\projeto-integrador-4termo\...` e a lista de pacotes instalados; o dataset tem 32 arquivos de imagem, dos quais apenas 4 são distintos (ver SEC-15); o modelo binário de 16 MB não tem hash registrado (ver SEC-11). Ponto positivo: o `.gitignore` do `backend-spring/demo` está correto para Maven/IDEs.
- **Impacto:** quando entrarem `application.properties`, `docker-compose.yml`, `.env`, chaves/certificados OPC-UA e dumps de banco, o caminho natural é commitar senhas e chaves por acidente. Segredo commitado deve ser considerado vazado, mesmo após remoção (fica no histórico).
- **Correção recomendada:**
  1. Adicionar `.gitignore` na raiz (modelo incluso) cobrindo `.env*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `application-*.properties` com segredos, volumes do Docker e dumps.
  2. Padrão de segredos: arquivos `*.example` versionados, valores reais fora do repositório (variáveis de ambiente, Docker secrets).
  3. Secret scanning (GitHub secret scanning/push protection ou `gitleaks` no CI — incluído no workflow).
  4. `SECURITY.md` com canal de reporte; branch protection na `main` (revisão obrigatória, status checks).

### SEC-07 — Exposição do banco na nuvem e caminho de dados do app Admin  *(risco de projeto)*
- **Severidade:** Alta · **Tipo:** [Projeto] · **OWASP:** A02, A01, A04
- **Evidência:** `docs/esquematico.jpg` mostra a Raspberry Pi enviando dados ao container Docker "pela Internet" e uma seta de "Dados históricos" **do container do banco direto para o "Acesso de Admin"** no app. O README descreve o banco como "isolado em um container Docker", sem camada de API na nuvem.
- **Riscos:**
  1. Se o app falar direto com o banco, as credenciais do banco ficam no aparelho (BYOD) ou o banco precisa estar exposto à Internet.
  2. O Docker publica portas (`-p 5432:5432`) **ignorando as regras do firewall do host** (UFW/iptables), expondo o banco sem que o administrador perceba.
  3. Replicação Pi → nuvem sem TLS e sem autenticação mútua.
- **Correção recomendada:**
  1. Colocar uma **API na nuvem** (ou usar a própria API Spring) entre app/Pi e o banco; o app **nunca** acessa o banco.
  2. Não publicar a porta do banco; usar rede Docker interna (`internal: true`) e acessá-lo só pelo serviço da API. Se precisar publicar, vincular a `127.0.0.1`.
  3. TLS em todo o trajeto Pi → nuvem (ideal: mTLS ou VPN/WireGuard) e credenciais separadas por função (usuário de aplicação com privilégio mínimo; sem usar `root`/`postgres` na aplicação).
  4. Backups cifrados e testados; atualização regular das imagens Docker; scan de imagem (Trivy) no CI.

### SEC-08 — OPC-UA sem segurança: conexão anônima e escrita direta nos atuadores
- **Severidade:** Alta · **Tipo:** [Código] (confirmado na rev. 2) + [Projeto] · **OWASP:** A02, A07, A04, A06
- **Evidência:** `backend-spring/demo/src/main/java/com/pi/demo/poc/ClpOpcUaPoc.java`
  - linha 47: `OpcUaClient.create(endpoint); // acesso anônimo, sem segurança`: sem política de segurança, sem certificado, sem usuário;
  - linha 31: endpoint fixo `opc.tcp://192.168.0.10:4840`;
  - linhas 34–37: o cliente **escreve diretamente** nas variáveis dos cilindros (`GVL.cilindro_simples`, `GVL.cilindro_dupla_avanca`, `GVL.cilindro_dupla_recua`);
  - linha 86 (opção 5 do menu): lê **qualquer NodeId** digitado.
- **Descrição:** o README apresenta o OPC-UA como mitigação por "suportar autenticação e criptografia", e afirma que "o Raspberry Pi nunca comanda atuadores diretamente". O código atual faz o oposto nos dois pontos: conecta sem nenhuma proteção e aciona saídas de cilindro. *Suporte* a segurança não é segurança *ativa*. Um servidor OPC-UA que aceita cliente anônimo permite que **qualquer dispositivo na rede** acione cilindros.
- **Observação:** os NodeIds usam o padrão `ns=2;s=GVL.…`. Confirme no UaExpert/TIA Portal se correspondem ao servidor OPC-UA do S7-1500 (os nós de DB costumam seguir outra forma); se vierem de outro CLP/ambiente de teste, ajuste antes de ir para a bancada.
- **Correção recomendada** (confirmar as políticas disponíveis para o firmware do seu S7-1500):
  1. Habilitar apenas **Sign & Encrypt** com política moderna (família `Basic256Sha256`/`Aes128_Sha256_RsaOaep` ou superior); desabilitar o modo `None` e políticas legadas.
  2. Desabilitar acesso anônimo/guest; usuário dedicado de mínimo privilégio ou certificado de cliente (no Milo: `OpcUaClientConfigBuilder` com `SecurityPolicy`, `MessageSecurityMode` e `IdentityProvider`).
  3. Certificados X.509 próprios e **lista de confiança** explícita; sem "aceitar qualquer certificado" em produção.
  4. A Pi deve **escrever apenas o resultado da inspeção**; o CLP valida e decide o acionamento (ver SEC-16).
  5. CLP em rede segmentada (VLAN/firewall), separada do Wi-Fi dos celulares.
  6. Mover o PoC para fora de `src/main` (ver SEC-16) e guardar chaves/senhas fora do repositório (SEC-06).

### SEC-09 — Ausência de logging, auditoria e alertas de segurança
- **Severidade:** Média · **Tipo:** [Código] + [Projeto] · **OWASP:** A09 Security Logging & Alerting Failures
- **Evidência:** nenhum log/telemetria no app; o DER não tem tabela de auditoria nem registro de tentativas de login.
- **Impacto:** impossível saber quem alterou a velocidade, quem acessou o histórico, ou detectar tentativas de força bruta e acessos indevidos.
- **Correção recomendada:**
  1. Tabela `auditoria` (quem, quando, ação, alvo, origem/IP, resultado) para: login/falha de login, alterações de velocidade/status, acesso a fotos e relatórios, mudanças de perfil.
  2. Logs estruturados no Spring (JSON) **sem** senha, token ou dados pessoais desnecessários; correlação por `requestId`.
  3. Alertas: picos de login falho, comandos fora de faixa, perda de conexão OPC-UA, queda de replicação para a nuvem.
  4. Retenção e proteção dos logs contra alteração.

### SEC-10 — Modelo de dados: perfil sem restrição, caminho de imagens e dados pessoais (LGPD)  *(risco de projeto)*
- **Severidade:** Média · **Tipo:** [Projeto] · **OWASP:** A01, A04
- **Evidência:** `docs/der.txt`.
- **Pontos de atenção:**
  1. `usuarios.perfil varchar` aceita qualquer texto → usar `CHECK (perfil IN ('OPERADOR','ADMIN'))` ou enum, e validar também no backend.
  2. `pecas_recusadas.imagem_url` guarda um caminho (`/storage/recusadas/...`). Se o endpoint servir arquivos por esse caminho, há risco de *path traversal* e de acesso a fotos de qualquer peça (IDOR). Servir imagens por **id**, com checagem de autorização, e gerar nomes de arquivo no servidor (UUID), sem aceitar caminho do cliente.
  3. Os exemplos do DER usam hashes `$2a$10$…` (bcrypt), o que é bom. Exigir custo adequado (≥ 10–12) ou migrar para Argon2id; nunca armazenar senha reversível.
  4. `usuarios` guarda nome e e-mail (dados pessoais): aplicar princípios da **LGPD** (finalidade, minimização, retenção, direitos do titular) e cifrar backups.
  5. Faltam campos de ciclo de vida: `criado_em`, `ativo`/`bloqueado_em`, `ultimo_login`, e uma estrutura para refresh tokens revogáveis.

### SEC-11 — Integridade do modelo de IA e da cadeia de entrega
- **Severidade:** Média · **Tipo:** [Código] (rev. 2) · **OWASP:** A08 Software or Data Integrity Failures
- **Evidência:** `ia-modelo/main.py:33` carrega o modelo a partir de `MODEL_PATH` (variável de ambiente) e `main.py:62-65` só verifica se o arquivo existe; não há hash, assinatura nem versão. O modelo `modelo_inspecao.tflite` (16 MB) está no Git sem checksum. `requirements.txt` sem versões (SEC-05).
- **Ponto positivo:** o formato **TFLite (FlatBuffers) não executa código ao ser carregado**, o que evita o risco clássico de modelos serializados com `pickle`/`torch.load`. Mantenha esse formato.
- **Riscos:** um modelo adulterado ou trocado por engano altera silenciosamente a decisão de aprovar/reprovar; sem versão registrada em cada inferência é impossível auditar qual modelo decidiu.
- **Correção recomendada:**
  1. Registrar o **SHA-256** do modelo e verificá-lo na inicialização (`MODEL_SHA256`); recusar iniciar se divergir. O hash do modelo atual está em `ia-modelo/modelo_inspecao.tflite.sha256` nesta entrega: `b5df3399…ca4a07`.
  2. Incluir `modelo_versao` (e hash) na resposta da API e no registro de cada inferência.
  3. Pipeline com revisão obrigatória, artefatos versionados e deploy a partir de tags; nada de `curl | sh` na Pi.
  4. Validar a entrada (formato/tamanho/dimensões, ver SEC-14) e tratar baixa confiança como revisão manual (ver SEC-15).

### SEC-12 — `ExternalLink` abre qualquer `href` recebido (informativo)
- **Severidade:** Informativa · **Tipo:** [Código] · **OWASP:** A01 / A05
- **Evidência:** `src/components/external-link.tsx:18` abre o `href` no navegador interno. Hoje todos os usos passam URLs literais fixas do Expo (`explore.tsx`, `app-tabs.web.tsx`), então não há risco atual. Código de template que provavelmente será removido.
- **Recomendação:** se algum dia o `href` vier de dados do servidor, validar o esquema (`https:` apenas) e uma lista de domínios permitidos antes de abrir.

### SEC-13 — API de IA exposta em `0.0.0.0:8000`, sem autenticação
- **Severidade:** Alta · **Tipo:** [Código] · **OWASP:** A02 Security Misconfiguration, A07 Authentication Failures, A01 Broken Access Control
- **Evidência:** `ia-modelo/main.py:4-5` documenta a execução como `uvicorn main:app --host 0.0.0.0 --port 8000`; não há nenhum mecanismo de autenticação no arquivo. Verificado em execução: `/predict`, `/predict/base64`, `/health`, **`/docs` e `/openapi.json` respondem 200 sem credencial**.
- **Descrição:** pela arquitetura (`doc_pastas.txt`: `PythonMlClient` → HTTP), **só o backend Spring, na mesma Raspberry Pi, precisa falar com essa API**. Mesmo assim ela escuta em todas as interfaces. Qualquer dispositivo na rede local (inclusive celulares pessoais, BYOD) pode enviar imagens para inferência, consumindo CPU de um Raspberry Pi e deixando a classificação indisponível para a esteira, além de mapear a API pela documentação interativa.
- **Correção recomendada:**
  1. Executar com `--host 127.0.0.1` (ou socket Unix); a Spring chama `http://127.0.0.1:8000`.
  2. Se precisar escutar na rede: chave de API/token compartilhado via cabeçalho (`Depends` do FastAPI) e firewall liberando só o IP do backend.
  3. Desabilitar `/docs`/`/openapi.json` em produção (`FastAPI(docs_url=None, redoc_url=None, openapi_url=None)`).
  4. No `WebClientConfig` da Spring, definir timeouts e *circuit breaker*.
- **Teste que comprova:** `ia-modelo/tests/test_main.py::test_exige_autenticacao` (`xfail strict`).

### SEC-14 — Entradas não tratadas na API de IA: imagem "bomba" gera HTTP 500
- **Severidade:** Média · **Tipo:** [Código] (medido) · **OWASP:** A10 Mishandling of Exceptional Conditions, A05 (validação de entrada)
- **Evidência:** `ia-modelo/main.py:97-100` captura apenas `UnidentifiedImageError` e `OSError`. Em teste real, um PNG de 48 KB declarando 20000×20000 px (400 Mpx) faz o Pillow lançar `DecompressionBombError`, e **ambos os endpoints respondem HTTP 500**. Já o corpo de `/predict/base64` é lido integralmente e decodificado antes da checagem de 10 MB (`main.py:146-154`); um payload de 30 MB foi aceito para decodificação e só então recusado com 413 (não há limite de corpo no servidor).
- **Impacto:** erro 500 e consumo de memória em um dispositivo pequeno; com SEC-13, é um vetor de negação de serviço contra a inspeção.
- **Correção recomendada:** definir `Image.MAX_IMAGE_PIXELS` condizente com a câmera (ex.: poucos Mpx) e capturar `Image.DecompressionBombError`/`DecompressionBombWarning` → HTTP 413/400; limitar o tamanho do corpo na borda (`--limit-max-requests`/proxy reverso) e usar `max_length` no campo base64 do Pydantic; validar dimensões antes de decodificar por completo.
- **Teste que comprova:** `test_imagem_com_dimensoes_absurdas_retorna_4xx` (`xfail strict`).

### SEC-15 — O modelo aprova peça reprovada e não há limiar de confiança (falha não segura)
- **Severidade:** Alta · **Tipo:** [Código] (medido) · **OWASP:** A06 Insecure Design, A10, A08
- **Evidência (medida com o modelo real):**

| Imagem do dataset (classe real) | Resultado da API | Confiança |
|---|---|---|
| `aprovada_azul.jpg` (aprovada) | aprovadas | 95,75% |
| `aprovada_branca.jpg` (aprovada) | aprovadas | 75,41% |
| `recusada_azul.jpg` (reprovada) | reprovadas | 95,69% |
| **`recusada_branca.jpg` (reprovada)** | **aprovadas (errado)** | **54,70%** |

  Essas são imagens **do próprio treino**; em dados novos o desempenho tende a ser pior.

  Entradas que **não são peças** também recebem classificação, e a pior é "aprovada" (confirmado por execução):

  | Entrada sintética | Resultado | Confiança |
  |---|---|---|
  | imagem totalmente preta (câmera tampada) | **aprovadas** | 50,7% |
  | imagem totalmente branca | **aprovadas** | 70,5% |
  | ruído aleatório | **aprovadas** | — |

- **Causas observadas:**
  1. `main.py:116-123` usa `argmax` e devolve a ação correspondente, **sem limiar de confiança** e sem estado "revisão manual".
  2. O dataset tem **32 arquivos, mas só 4 imagens distintas** (verificado por hash: 4 conteúdos × 8 cópias "- Copia"). O notebook divide 26/6 entre treino e validação (`image_dataset_from_directory`, `validation_split=0.2`), então cópias idênticas caem nos dois lados (**vazamento de dados**) e a métrica de validação não mede generalização. Na saída salva do notebook, a validação oscila (1,0 → 0,5 na última época).
  3. Não há métricas por classe (recall de "reprovadas"), conjunto de teste separado nem critério de aceite do modelo; `obsoleto.ipynb` indica que o treino atual não está documentado em outro lugar.
- **Impacto:** é o requisito central do sistema, desviar automaticamente as peças reprovadas, e hoje uma peça defeituosa pode seguir como aprovada, silenciosamente.
- **Correção recomendada:**
  0. Tratar também a entrada "sem peça": uma checagem prévia (brilho/variância mínima) ou uma terceira classe "sem peça/inválida" evita que câmera tampada ou esteira vazia virem "aprovada".
  1. **Limiar de confiança**: se `confianca < LIMIAR` (ex.: 85–90%, calibrado no conjunto de validação), retornar `revisao_manual`; o backend/CLP trata essa saída como **não aprovada** (desvia/sinaliza).
  2. Coletar muito mais imagens **distintas** (variações de peça, iluminação, posição, defeitos reais) e **remover duplicatas** antes de dividir; separar treino/validação/teste por peça, não por arquivo.
  3. Definir critério de aceite (ex.: recall de "reprovadas" ≥ meta; limite de falso-aprovado) e um teste de regressão que o verifique a cada novo modelo.
  4. Registrar versão/hash do modelo por inferência (SEC-11).
- **Testes que comprovam (`xfail strict`):** `test_baixa_confianca_nao_aprova_automaticamente`, `test_imagem_sem_peca_nao_vira_aprovada` e a falha esperada de `test_modelo_classifica_imagens_do_proprio_treino[recusada_branca]`.

### SEC-16 — PoC de acionamento (`ClpOpcUaPoc`) dentro de `src/main`, com pulso temporizado no cliente e sem intertravamento
- **Severidade:** Média (promover para Alta se esse padrão for para produção) · **Tipo:** [Código] · **OWASP:** A06 Insecure Design
- **Evidência:** `ClpOpcUaPoc.java:41,124-126`: o método `pulso()` escreve `true` na saída do cilindro, dorme `PULSO_MS = 1000` ms e escreve `false` no `finally`. As opções 3 e 4 do menu ligam avançar/recuar escrevendo as duas saídas em sequência, sem checagem de estado. A classe está em `src/main/java/.../poc/`, ou seja, **é empacotada no artefato da aplicação**.
- **Riscos:**
  1. Se o processo, a Raspberry Pi ou a conexão de rede cair entre as duas escritas, o `finally` pode não ser executado e **a saída permanece energizada** (o cilindro fica acionado até alguém intervir).
  2. Temporização e exclusão mútua (avançar × recuar) feitas no cliente, e não no CLP, contradizem o princípio documentado de que o CLP é a camada determinística e de segurança.
  3. Um `main` interativo que escreve em atuadores dentro do artefato de produção amplia a superfície de risco.
- **Correção recomendada:** o CLP implementa o pulso (temporizador TON) e o intertravamento; a Pi escreve **um único bit de comando/resultado** que o CLP consome e zera. Mover o PoC para `src/test`, um módulo separado ou um perfil Maven que não entre no *build* de produção; remover o menu "ler qualquer NodeId" fora de ambiente de laboratório.

---

## 5. Análise por categoria (inclui o que **não** foi encontrado)

**A01 — Broken Access Control.** Ver SEC-02, SEC-03, SEC-07, SEC-10. Princípio para o backend: negar por padrão, autorizar por recurso no servidor, sem confiar em `perfil` enviado pelo cliente.

**A02 — Security Misconfiguration.** Ver SEC-01, SEC-06, SEC-07, SEC-08. Não há `usesCleartextTraffic`, permissões nativas excessivas ou flags de debug em `app.json` (ponto positivo). Atenção: o app usa Continuous Native Generation (sem pastas `ios/`/`android/`); qualquer permissão nativa futura (câmera, notificações) deve ser a mínima necessária.

**A03 — Software Supply Chain Failures.** Ver SEC-05 e SEC-06. Ponto positivo: lockfile versionado. Falta automação (Dependabot/CI) e inventário (SBOM).

**A04 — Cryptographic Failures.** Ver SEC-01, SEC-07, SEC-08, SEC-10. Positivo: o DER já prevê `senha_hash` com bcrypt. Atenção para o armazenamento de tokens no aparelho (`expo-secure-store`).

**A05 — Injection.** **Nenhum ponto de injeção no código atual** (app, Spring ou Python): não há consultas SQL (a entidade JPA usa o mapeamento padrão, sem JPQL/SQL nativo), `WebView`, `eval`, `dangerouslySetInnerHTML`, `subprocess` nem desserialização insegura (a única ocorrência de shell/`fs` é `scripts/reset-project.js`, script de desenvolvimento). A API Python valida o tipo de arquivo via Pillow e não usa o nome do arquivo enviado em nenhum caminho do disco, o que é correto. Preventivo para o backend: JPA/Spring Data com consultas parametrizadas (nunca concatenar SQL), Bean Validation (`@Valid`) em todo DTO, sanitização de nomes de arquivo (relaciona-se com SEC-10).

**A06 — Insecure Design.** Ver SEC-03 e SEC-04. Recomenda-se um **modelo de ameaças** curto (STRIDE) por fronteira de confiança: celular ↔ Pi, Pi ↔ CLP, Pi ↔ nuvem. O plano de remediação inclui um roteiro.

**A07 — Authentication Failures.** Ver SEC-02 e SEC-08. Requisitos mínimos: política de senha, bloqueio progressivo, MFA para `ADMIN` (desejável), expiração e revogação de tokens, logout que invalida o refresh token.

**A08 — Software or Data Integrity Failures.** Ver SEC-06, SEC-11 e SEC-15 (dados de treino duplicados). Não há atualização OTA (`expo-updates`) configurada, então não há risco atual nesse vetor; se for ativada, usar code signing do EAS Update.

**A09 — Security Logging & Alerting Failures.** Ver SEC-09.

**A10 — Mishandling of Exceptional Conditions.** Ver SEC-04. Regra geral: em caso de exceção, **falhar de forma segura** (negar/pedir revisão), com mensagens genéricas ao usuário e detalhe só no log.

---

## 6. Pontos positivos observados

- Decisão de arquitetura correta: o **CLP é o único a acionar atuadores**; a Raspberry Pi e o app nunca comandam diretamente.
- Escolha de OPC-UA (com possibilidade real de autenticação e cifra) em vez de protocolo sem segurança.
- DER já prevê hash de senha, `email` único e perfis de acesso.
- `package-lock.json` versionado; TypeScript em modo `strict`.
- Nenhum segredo, token, chave ou credencial encontrado no código versionado (app, Spring, Python e notebook).
- API Python bem estruturada: carrega o modelo uma vez no `lifespan`, serializa a inferência com lock (o interpretador TFLite não é thread-safe), limita upload a 10 MB, valida o conteúdo como imagem, valida base64 com `validate=True` e não usa o nome do arquivo recebido. Os testes que escrevi cobrem 98% do `main.py`.
- Formato do modelo (TFLite) não executa código ao carregar; `pip-audit` limpo hoje; `.gitignore` do projeto Maven adequado; o código do PoC desliga a saída no `finally` e verifica `status.isBad()` ao escrever.
- O próprio README identifica a autenticação como pendência, o que mostra consciência do risco.

---

## 7. Observações de qualidade da documentação (não são vulnerabilidades)

- O README está em `docs/README.md` (não na raiz, onde o GitHub o exibiria) e há um `main.txt` de 4 bytes na raiz. A pasta `/ia-modelo` citada no README agora existe.
- **Arquitetura x código:** o README e o `esquematico.jpg` mostram o Spring orquestrando câmera e IA, mas o código criou uma **API Python separada na porta 8000** (nova fronteira de confiança Spring ↔ Python, não representada no diagrama). `backend-spring/doc_pastas.txt` planeja o pacote `com.sistema.inspecao`, enquanto o projeto real usa `com.pi.demo`. O README diz Java 17+; o `pom.xml` usa Java 21.
- O teste `DemoApplicationTests.contextLoads` carrega o contexto completo com `spring-boot-starter-data-jpa`, mas não há driver/banco configurado (`application.properties` só tem o nome da aplicação) e `spring.datasource.*` não existe; **provavelmente o teste falha** ao iniciar. Não foi possível confirmar (Maven bloqueado neste ambiente); rode `./mvnw test` localmente.
- `obsoleto.ipynb` é o único registro do treino: se o notebook é obsoleto, falta um script/notebook atual e reproduzível.
- `docs/listaMateriais.txt` cita ESP32/Arduino/motor CC/webcam, enquanto a arquitetura atual usa CLP Siemens S7-1500 e Raspberry Pi. Convém alinhar a lista de materiais à arquitetura v2.
- O `esquematico.jpg` está citado como atualizado (v2); vale versionar também a fonte editável do diagrama.

---

## 8. Próximos passos

Veja `PLANO_DE_REMEDIACAO.md` (priorização em sprints) e `REQUISITOS_DE_SEGURANCA.md` (checklist por componente para usar quando backend, Docker e CLP forem implementados). Quando houver backend em execução, repetir esta análise com testes dinâmicos (OWASP ZAP, testes de autorização por perfil) e revisão de configuração do Spring/Docker.
