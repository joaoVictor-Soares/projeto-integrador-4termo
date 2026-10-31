# Entrega — Relatórios de Segurança e Qualidade (revisão 2)

Gerado em 30/10/2026 a partir do commit `ded01bd` de `joaoVictor-Soares/projeto-integrador-4termo` (a revisão 1 analisou `0df7b3e`, só com o app).

A estrutura espelha o repositório: copie as pastas para a raiz do projeto (mesclando) e faça o commit.

| Arquivo | Para que serve |
|---|---|
| `docs/seguranca/RELATORIO_OWASP_TOP10.md` | **Relatório OWASP Top 10:2025**: 16 achados (7 Altos, 8 Médios, 1 Informativo), evidências e correções |
| `docs/qualidade/RELATORIO_COBERTURA_TESTES.md` | **Relatório de cobertura**: API de IA 98% (medido), app 100% do código próprio (medido), backend Java não medido |
| `docs/seguranca/PLANO_DE_REMEDIACAO.md` | Plano priorizado em fases (inclui a Fase 1B com os achados novos) |
| `docs/seguranca/REQUISITOS_DE_SEGURANCA.md` | Checklist por componente (app, backend, OPC-UA, Docker/BD, API de IA, IA, CI) |
| `docs/seguranca/npm-audit-2026-10-30.json`, `python-versoes-resolvidas-2026-10-30.txt` | Evidências de dependências |
| `docs/qualidade/evidencias/` | Cobertura bruta: `app-react-native/` (lcov + HTML) e `python/` (JSON + HTML) |
| `ia-modelo/tests/`, `pytest.ini`, `requirements-dev.txt`, `modelo_inspecao.tflite.sha256` | **19 testes** da API de IA (14 passam, 5 falhas esperadas) e hash do modelo |
| `app-react-native/jest.config.js`, `__mocks__/`, `**/__tests__/` | Configuração do Jest e 4 suítes (25 casos) |
| `.github/workflows/ci.yml`, `.github/dependabot.yml` | CI (app, API de IA, backend Java) e atualização automática de dependências (npm, pip, maven) |
| `.gitignore` (raiz), `SECURITY.md` | Higiene de segredos e canal de reporte (preencha o e-mail) |

## Como rodar os testes

API de IA (`ia-modelo/`):
```bash
pip install fastapi uvicorn python-multipart numpy pillow ai-edge-litert -r requirements-dev.txt
python -m pytest --cov=main --cov-branch --cov-report=term-missing
```
App (`app-react-native/`): veja a seção 7 do relatório de cobertura.

## Observações importantes

- **Backend Java não foi compilado nem executado** (Maven Central bloqueado no ambiente de análise): sua cobertura não foi medida e as afirmações sobre ele são por leitura do código. Rode `./mvnw verify` localmente.
- Os testes `xfail`/`failing` descrevem comportamentos corretos que o sistema **ainda não tem**. Quando você corrigir o problema, o teste quebra de propósito: remova a marcação.
- O CI começa com a auditoria de dependências do app e o job Java como *não bloqueantes*; remova `continue-on-error` depois de resolver os achados.
- Análise estática e execução local da API de IA; sem pentest do sistema integrado.
