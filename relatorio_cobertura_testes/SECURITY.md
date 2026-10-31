# Política de Segurança

## Reportar uma vulnerabilidade

Não abra uma *issue* pública para vulnerabilidades. Envie os detalhes por e-mail ao responsável pelo projeto (preencha: `seu-email@exemplo.com`) ou use "Report a vulnerability" na aba **Security** do GitHub.

Inclua: descrição, passos para reproduzir, componente afetado e impacto estimado. Respondemos em até 7 dias corridos.

## Escopo

App React Native, backend Spring Boot, configuração Docker/banco, integração OPC-UA e modelo de IA deste repositório.

## Boas práticas para contribuidores

- Nunca commitar segredos (`.env`, chaves, certificados, dumps). Use `.env.example`.
- Todo PR passa por CI (tipos, testes, `npm audit`, varredura de segredos) e por revisão.
- Consulte `docs/seguranca/REQUISITOS_DE_SEGURANCA.md` e a definição de pronto em `docs/seguranca/PLANO_DE_REMEDIACAO.md`.
