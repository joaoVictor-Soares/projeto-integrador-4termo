# Plano de Remediação

Complementa o `RELATORIO_OWASP_TOP10.md` (OWASP Top 10:2025). Prioriza os 12 achados por risco e por momento ideal de correção. Os prazos são estimativas de esforço, não compromissos; ajuste à capacidade da equipe.

**Princípio:** o que é barato agora (higiene do repositório, dependências, decisões de arquitetura) deve ser feito **antes** de escrever o backend; o que depende de código futuro (autenticação, OPC-UA, auditoria) entra como requisito de aceite da respectiva tarefa.

## Fase 0 — Imediato (≈ 1 dia)

| # | Ação | Achado | Esforço | Pronto quando |
|---|---|---|---|---|
| 1 | Adicionar `.gitignore` na raiz (modelo incluso) e `.env.example` | SEC-06 | 0,5 h | `.env`, `*.pem`, `*.key` ignorados em todo o monorepo |
| 2 | Habilitar *secret scanning* + *push protection* no GitHub e proteger a branch `main` (PR + revisão + checks) | SEC-06 | 0,5 h | Configurações ativas |
| 3 | Adicionar `.github/workflows/ci.yml` e `.github/dependabot.yml` (inclusos) | SEC-05, SEC-06 | 1 h | CI verde no primeiro PR |
| 4 | Criar `SECURITY.md` (canal de reporte, versões suportadas) | SEC-06 | 0,5 h | Arquivo na raiz |

## Fase 1 — Antes da integração app ↔ backend (≈ 1 semana)

| # | Ação | Achado | Esforço | Pronto quando |
|---|---|---|---|---|
| 5 | Atualizar `axios`, `expo`, `expo-router` (patches do SDK 57) e regenerar o lockfile; **não** aplicar o `npm audit fix --force` (sugere downgrade para `expo@51`) | SEC-05 | 2–4 h | `npm audit --audit-level=high` sem itens de runtime |
| 6 | Remover o código de template não utilizado (`explore`, `hint-row`, `web-badge`, `collapsible`, animações) | SEC-05, SEC-12 | 2 h | Cobertura e superfície de ataque reduzidas |
| 7 | Externalizar a URL da API (`EXPO_PUBLIC_API_URL`) e **trocar para HTTPS** | SEC-01 | 2 h | `api.test.js`: converter o `it.failing` de HTTPS em `it` e passar |
| 8 | Adotar os testes e o `jest.config.js` da entrega; script `npm test` | — | 1–2 h | CI executa testes com cobertura |
| 9 | Definir o **modelo de ameaças** (STRIDE) das 3 fronteiras: celular↔Pi, Pi↔CLP, Pi↔nuvem; registrar decisões em `docs/seguranca/` | SEC-03, SEC-07, SEC-08 | 0,5–1 dia | Documento revisado pelo grupo/orientador |
| 10 | Definir a topologia de rede: segmentação CLP / Pi / Wi-Fi dos celulares; decidir a camada de API na nuvem (o app não acessa o banco) | SEC-07, SEC-08 | 0,5 dia | Diagrama atualizado (`esquematico`) |

## Fase 1B — Novos achados da revisão 2 (API de IA, PoC do CLP, backend) — fazer cedo

Estes itens vêm do código que entrou depois da 1ª análise. Os mais urgentes são os do modelo, porque ele decide o destino físico da peça.

| # | Ação | Achado | Esforço | Pronto quando |
|---|---|---|---|---|
| A | **Limiar de confiança + estado `revisao_manual`**; tratar "sem peça" (checagem de brilho/variância ou 3ª classe); backend/CLP tratam isso como **não aprovada** | SEC-15 | 0,5–1 dia | `xfail` de baixa confiança e de imagem sem peça convertidos em testes normais e passando |
| B | **Refazer o dataset**: dezenas+ de imagens *distintas* por classe/luz/cor, remover duplicatas, separar treino/validação/teste por peça, registrar matriz de confusão e recall de "reprovadas" | SEC-15 | dias (coleta) | Relatório de avaliação em `ia-modelo/` com meta de recall definida e atingida |
| C | API de IA em `127.0.0.1` (ou socket), token se sair da máquina, `/docs` desligado em produção | SEC-13 | 1–2 h | `xfail` de autenticação convertido; porta 8000 inacessível de outro dispositivo |
| D | Tratar `DecompressionBombError`, limitar pixels (`Image.MAX_IMAGE_PIXELS`) e tamanho do corpo do base64 | SEC-14 | 1–2 h | `xfail` da imagem "bomba" convertido; resposta 4xx |
| E | Fixar versões em `requirements.txt` (com hashes) e registrar SHA-256 do modelo, verificado na inicialização; incluir versão do modelo na resposta | SEC-05, SEC-11 | 2 h | Backend/API recusam modelo com hash divergente |
| F | Mover `ClpOpcUaPoc` para fora de `src/main`; o CLP implementa pulso e intertravamento; Pi escreve um único bit de resultado | SEC-16, SEC-08 | 0,5 dia | Nenhum `main` que escreva em atuador no artefato de produção |
| G | OPC-UA com Sign&Encrypt e sem anônimo no `OpcUaService` definitivo | SEC-08 | 0,5–1 dia | Conexão anônima recusada em teste de bancada |
| H | Spring: adicionar `spring-boot-starter-security`/`validation`, datasource por variável de ambiente, banco de teste (H2/Testcontainers) para o `contextLoads` passar; adicionar JaCoCo | SEC-02, SEC-16 | 0,5 dia | `./mvnw verify` verde com relatório JaCoCo |
| I | `git rm --cached ia-modelo/__pycache__`; `.gitignore` da raiz (incluso); limpar saídas do notebook (`nbstripout`) | SEC-06 | 0,5 h | Sem `.pyc` no Git |

## Fase 2 — Durante a implementação do backend (requisitos de aceite)

| # | Ação | Achado | Pronto quando |
|---|---|---|---|
| 11 | Autenticação (Spring Security): senha com bcrypt/Argon2id, JWT curto + refresh rotativo, limite de tentativas | SEC-02 | Testes de integração: sem token = 401; perfil errado = 403; bloqueio após N falhas |
| 12 | App: tela de login, `expo-secure-store` para tokens, rotas protegidas por perfil | SEC-02 | `it.failing` do A01 convertido em teste real |
| 13 | Autorização por recurso e perfil **no servidor** para todos os endpoints; negar por padrão | SEC-02, SEC-10 | Teste de autorização para 100% dos endpoints |
| 14 | Comando de velocidade: validação de faixa/passo/estado no backend e limites + intertravamento no CLP; parada de emergência cabeada | SEC-03 | Teste de bancada documentado + teste de API com valores inválidos |
| 15 | Estados `CARREGANDO/SEM CONEXÃO/DESATUALIZADO` na UI; falha segura no backend (timeout OPC-UA/câmera ⇒ peça não aprovada) | SEC-04 | Testes cobrindo erro de rede e dado velho |
| 16 | OPC-UA endurecido: Sign&Encrypt, sem anônimo, certificados com lista de confiança, nós mínimos | SEC-08 | Checklist de `REQUISITOS_DE_SEGURANCA.md` §3 cumprido e evidenciado |
| 17 | Banco: não publicar porta, rede Docker interna, usuário de aplicação com privilégio mínimo, TLS Pi→nuvem | SEC-07 | Varredura de portas externa não encontra a porta do banco |
| 18 | Ajustes do DER: `CHECK` em `perfil`, `criado_em`, `ativo`, tabela `auditoria`, tabela de refresh tokens; imagens por id com autorização | SEC-09, SEC-10 | Migrations versionadas (Flyway/Liquibase) |
| 19 | Logging estruturado sem dados sensíveis + alertas (login falho, comando fora de faixa, perda de OPC-UA/replicação) | SEC-09 | Alertas disparados em teste |
| 20 | Modelo de IA: formato sem execução de código, hash SHA-256 verificado na inicialização, versão registrada por inferência | SEC-11 | Backend recusa modelo com hash divergente |

## Fase 3 — Antes da entrega / produção

| # | Ação | Pronto quando |
|---|---|---|
| 21 | DAST com OWASP ZAP no backend em ambiente de teste; testes de autorização por perfil | Relatório sem achados Alta/Crítica em aberto |
| 22 | Scan de imagens Docker (Trivy) e de dependências Maven no CI; SBOM gerado | Pipeline falha em severidade Alta |
| 23 | Revisão LGPD: finalidade, retenção e descarte dos dados de `usuarios` e das fotos | Registro simples de tratamento de dados |
| 24 | Reexecutar o relatório OWASP com o sistema completo | Novo relatório anexado |

## Definição de pronto (DoD) de segurança para cada tarefa nova

- [ ] Entradas validadas no servidor (tipo, tamanho, faixa, formato).
- [ ] Autorização verificada no servidor para o recurso e o perfil.
- [ ] Nenhum segredo no código ou no histórico; configuração por variável de ambiente.
- [ ] Erros tratados de forma segura (falha = negar/pedir revisão) e sem vazar detalhes ao usuário.
- [ ] Ação relevante registrada em auditoria.
- [ ] Teste automatizado para o caminho feliz **e** para o caminho de abuso (sem token, perfil errado, valor inválido).
- [ ] `npm audit`/dependency check sem item alto de runtime.
