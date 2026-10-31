# Requisitos de Segurança por Componente

Checklist para usar **quando** cada componente for implementado (hoje só o app existe). Cada item referencia a categoria OWASP Top 10:2025 e o achado do relatório. Marque os itens e guarde a evidência (print, teste, configuração) no PR correspondente.

## 1. App React Native (`app-react-native/`)

- [ ] URL da API vinda de configuração de ambiente; HTTPS obrigatório (A04 · SEC-01)
- [ ] Tokens guardados com `expo-secure-store`, nunca em `AsyncStorage` ou em log (A04/A07 · SEC-02)
- [ ] Login, logout (invalida refresh token no servidor) e expiração de sessão (A07)
- [ ] Rotas protegidas por perfil; botões sensíveis ocultos para quem não tem permissão (e bloqueados no servidor) (A01 · SEC-02/03)
- [ ] Estados de rede explícitos: carregando, sem conexão, dado desatualizado, erro (A10 · SEC-04)
- [ ] Mensagens de erro genéricas ao usuário; nenhum `console.log` de dados sensíveis em produção (A09/A10)
- [ ] Links externos validados (`https:` e domínios permitidos) se o `href` vier do servidor (A01/A05 · SEC-12)
- [ ] Permissões nativas mínimas (câmera, notificações) declaradas apenas quando usadas (A02)
- [ ] Notificações push sem dados sensíveis no corpo (A04)
- [ ] `npm audit` e `expo-doctor` limpos no CI (A03 · SEC-05)
- [ ] Se usar `expo-updates`: code signing habilitado (A08)

## 2. Backend Spring Boot (`backend-spring/`)

- [ ] Spring Security: negar por padrão; autorização por endpoint e por recurso (A01)
- [ ] Senhas: bcrypt (custo ≥ 10–12) ou Argon2id; política de senha; bloqueio progressivo (A07)
- [ ] JWT curto (≈ 15 min) + refresh token rotativo e revogável; segredo/chave fora do repositório (A07/A04)
- [ ] Bean Validation (`@Valid`) em todo DTO; limites de tamanho no corpo e no upload de imagem (A05/A06)
- [ ] Consultas parametrizadas (JPA/Spring Data); nenhuma concatenação de SQL (A05)
- [ ] Imagens servidas por id com checagem de autorização; nomes gerados no servidor (UUID); sem caminho vindo do cliente (A01/A05 · SEC-10)
- [ ] Comando de velocidade/estado: validação de faixa, passo máximo e estado da esteira; rate limit (A06 · SEC-03)
- [ ] Tratamento global de exceções (`@ControllerAdvice`) sem *stack trace* na resposta (A10)
- [ ] Timeouts e *circuit breaker* nas chamadas OPC-UA e câmera; falha ⇒ peça **não aprovada** (A10 · SEC-04)
- [ ] CORS restrito; cabeçalhos de segurança; `spring.profiles` separados para dev/prod; Actuator desabilitado ou protegido (A02)
- [ ] Logs JSON sem dados sensíveis, com `requestId`; tabela de auditoria (A09 · SEC-09)
- [ ] Dependency-check/Dependabot para Maven; SBOM (A03)
- [ ] Testes: 401/403/200 para cada endpoint; testes de abuso (valor inválido, token expirado) (A01/A07)

## 3. OPC-UA (Raspberry Pi ↔ CLP S7-1500)

*(Confirme as opções disponíveis no firmware e no TIA Portal da versão utilizada.)*

- [ ] Modo de segurança **Sign & Encrypt**; política moderna (família `Basic256Sha256`/`Aes128_Sha256_RsaOaep` ou superior); modo `None` e políticas legadas desabilitados (A04/A02 · SEC-08)
- [ ] Acesso anônimo/guest desabilitado; usuário dedicado de mínimo privilégio ou certificado de cliente (A07)
- [ ] Certificados X.509 do cliente (Eclipse Milo) com lista de confiança explícita; não aceitar certificado desconhecido em produção (A02)
- [ ] Somente os nós necessários expostos; escrita limitada às variáveis de resultado da inspeção (A01)
- [ ] O CLP valida o resultado e decide o acionamento do cilindro; limites e intertravamentos no programa do CLP (A06)
- [ ] Parada de emergência cabeada, independente de rede e do app (A06)
- [ ] CLP em rede segmentada (VLAN/firewall), sem rota direta dos celulares (A02)
- [ ] Chaves e senhas fora do repositório; rotação planejada (A02/A04)

## 4. Banco de dados e Docker (`cloud-docker/`)

- [ ] Porta do banco **não** publicada (ou vinculada a `127.0.0.1`); rede Docker interna (A02 · SEC-07)
- [ ] O app nunca acessa o banco; só a API (A01/A04)
- [ ] TLS entre Pi e nuvem (ideal: mTLS ou VPN) (A04)
- [ ] Usuário da aplicação com privilégios mínimos; sem `root`/`postgres` na aplicação (A01)
- [ ] Segredos via Docker secrets/variáveis de ambiente; `.env` fora do Git (A02 · SEC-06)
- [ ] Imagens com versão fixa (sem `latest`), execução como usuário não-root, sistema de arquivos somente leitura quando possível (A02/A03)
- [ ] Scan de imagem (Trivy) no CI (A03)
- [ ] Backups cifrados e teste de restauração; retenção definida (A04/LGPD)
- [ ] Migrations versionadas (Flyway/Liquibase) com `CHECK` em `perfil`, tabela `auditoria`, refresh tokens (A01/A09 · SEC-09/10)

## 5. IA e processamento de imagem (`ia-modelo/`, Raspberry Pi)

- [ ] Formato de modelo sem execução de código (TFLite/ONNX/`safetensors`); `torch.load` só com `weights_only=True` (A08 · SEC-11)
- [ ] SHA-256 e versão do modelo registrados; backend verifica na inicialização e grava a versão em cada inferência (A08)
- [ ] Validação de imagem (formato, dimensões, tamanho) antes da inferência (A05/A06)
- [ ] Baixa confiança ⇒ "revisão manual", nunca "aprovada" (A06/A10)
- [ ] Conjunto de validação fixo e testes de regressão a cada novo modelo (A08)

## 5B. API de IA (FastAPI em `ia-modelo/main.py`)

- [ ] Escuta só em `127.0.0.1` (ou socket Unix); apenas o Spring a chama (A01/A02 · SEC-13)
- [ ] Se for exposta na rede: token/chave de API e firewall limitando o IP do backend (A07)
- [ ] `/docs`, `/redoc` e `/openapi.json` desabilitados em produção (A02)
- [ ] Limite de pixels (`Image.MAX_IMAGE_PIXELS`) e captura de `DecompressionBombError` → 4xx; limite do corpo base64 antes de decodificar (A10 · SEC-14)
- [ ] **Limiar de confiança**: abaixo dele retorna `revisao_manual`, tratado como não aprovada (A06 · SEC-15)
- [ ] Imagem "sem peça" (câmera tampada/esteira vazia) nunca vira "aprovada" (A06/A10 · SEC-15)
- [ ] Resposta inclui versão e SHA-256 do modelo; hash verificado na inicialização (A08 · SEC-11)
- [ ] `requirements.txt` com versões fixadas e hashes; `pip-audit` no CI (A03)
- [ ] Dataset sem duplicatas, com conjunto de teste independente e meta de recall de "reprovadas" documentada (A08/A06)
- [ ] Executa como usuário sem privilégios (systemd), com limite de memória (A02)

## 6. Repositório e CI

- [ ] `.gitignore` na raiz; secret scanning/push protection ativos (A02/A03 · SEC-06)
- [ ] Branch `main` protegida: PR obrigatório, revisão, checks obrigatórios (A08)
- [ ] CI: `tsc`, testes com cobertura, `npm audit`, `gitleaks` (A03/A08)
- [ ] Dependabot ativo para npm, GitHub Actions (e Maven/Docker quando existirem) (A03)
- [ ] `SECURITY.md` com canal de reporte (A09)
