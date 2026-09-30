# Sistema Automatizado de Inspeção de Peças com IA e IoT

Projeto integrador do 4º termo do Tecnólogo em Análise e Desenvolvimento de Sistemas.

Este repositório contém o código-fonte e a documentação para o Sistema de Verificação de Peças, uma solução ponta a ponta que integra automação industrial (CLP), inteligência artificial na borda (Edge Computing), backend local, nuvem e um aplicativo móvel.

O objetivo do projeto é identificar e classificar o status de peças em uma esteira pneumática de produção em tempo real, **desviar automaticamente as peças reprovadas** por meio de um atuador pneumático, e fornecer relatórios detalhados na nuvem para administradores.

---

## Arquitetura do Sistema

O sistema foi arquitetado para garantir baixa latência na fábrica, decisão de controle segura no CLP e armazenamento seguro na nuvem, operando em quatro frentes:

### 1. Hardware & Automação Pneumática (CLP)

A esteira é automatizada por um **CLP Siemens S7-1500**, responsável pelo controle determinístico e em tempo real de todos os sensores e atuadores da bancada:

- **Sensores (24V):** óptico difuso, indutivo, capacitivo, óptico de barreira e óptico de cor — usados para detectar presença, posição e características da peça.
- **Cilindro de dupla ação:** responsável pelo transporte/avanço da peça ao longo da esteira.
- **Cilindro de simples ação (atuador de rejeição):** dedicado a desviar fisicamente peças reprovadas para uma calha/coletor de rejeito, sem intervenção manual.
- **Motor:** movimentação contínua da esteira.

O CLP é o único responsável por acionar fisicamente os cilindros — o Raspberry Pi nunca comanda atuadores diretamente. Isso mantém a lógica de segurança e tempo real no controlador industrial, que é o lugar correto para ela.

### 2. Edge Computing (Raspberry Pi)

O cérebro de decisão de qualidade. Roda uma API em Spring Boot que:

- Atua como **cliente OPC-UA** do CLP S7-1500 (protocolo nativo do S7-1500, com suporte a autenticação e criptografia — ver seção de Segurança), lendo eventos de gatilho (peça detectada) e escrevendo o resultado da inspeção de volta para o CLP.
- Aciona a câmera externa (USB) no momento indicado pelo CLP.
- Processa a imagem localmente através de um modelo de Machine Learning (IA) e classifica a peça como aprovada ou reprovada.

**Fluxo de rejeição de peça (novo):**

1. O sensor detecta a peça e o CLP sinaliza o evento ao Raspberry Pi via OPC-UA.
2. O Raspberry Pi aciona a câmera, executa a inferência de IA e obtém o resultado (aprovado/reprovado).
3. O resultado é escrito de volta em uma variável OPC-UA lida pelo CLP.
4. Se a peça for **reprovada**, o CLP aciona o cilindro de simples ação, desviando a peça da linha principal para o coletor de rejeito. Se **aprovada**, a peça segue o fluxo normal via cilindro de dupla ação/esteira.
5. O evento completo (foto, resultado, timestamp) é registrado no histórico local e replicado para a nuvem.

Essa decisão fecha o ciclo de automação do controle de qualidade: o sistema não depende mais de um operador remover manualmente a peça defeituosa.

### 3. Controle Móvel

Um aplicativo React Native na rede local permite aos operadores visualizarem a foto recém-tirada, o status da peça, o histórico diário da máquina e o estado do CLP em tempo real.

**Por que o celular do colaborador em vez de uma interface fixa:**

- **Mobilidade real:** o operador não fica preso a um painel fixo ao lado da esteira — recebe o status e alertas de rejeição onde estiver na fábrica.
- **Custo de implantação:** atende à restrição de baixo custo do projeto, evitando comprar, instalar e manter um painel/tablet dedicado por linha.
- **Escalabilidade:** permite notificar múltiplos operadores/supervisores simultaneamente caso a fábrica tenha mais de uma linha.
- **Alertas em tempo real onde a pessoa estiver:** com notificações push, o operador é avisado de uma rejeição mesmo sem estar olhando para a esteira naquele instante.
- **Aderência ao PPC do curso:** justifica tecnicamente o uso da disciplina de Aplicação Mobile com React Native no projeto.

Como contrapartida, o uso de dispositivo pessoal (BYOD) exige atenção redobrada à autenticação/autorização do app (ver Segurança) e a cuidados físicos do aparelho no ambiente industrial (poeira, quedas).

### 4. Nuvem & Big Data

Um banco de dados isolado em um container Docker no servidor disponibilizado, recebendo o histórico de inferências e os eventos de rejeição de forma assíncrona.

O diagrama esquemático completo e atualizado da arquitetura está disponível na pasta `/docs/esquematico.jpg`.

---

## Segurança

A comunicação entre Raspberry Pi e CLP via **OPC-UA** foi escolhida, entre outras razões, por já nascer com suporte nativo a autenticação e criptografia — reduzindo o risco (identificado na análise de lacunas do projeto) de comandos não autenticados trafegando na rede local. Autenticação do app mobile e do acesso administrativo continua como item a ser detalhado no backend (Spring Security).

---

## Stack Tecnológico

### Hardware Associado
- **CLP Siemens S7-1500:** controle determinístico dos sensores, do motor e dos cilindros pneumáticos (transporte e rejeição).
- **Raspberry Pi:** processamento local, hospedagem do Backend e do modelo de IA.
- **Câmera Externa:** captura padronizada de imagens.
- **Sensores industriais (24V):** óptico difuso, indutivo, capacitivo, óptico de barreira, óptico de cor.
- **Cilindros pneumáticos:** dupla ação (transporte) e simples ação (rejeição).

### Software
- **Backend (Edge):** Java 17+ e Spring Boot.
- **Comunicação Industrial:** cliente OPC-UA (ex: Eclipse Milo) para integração com o CLP S7-1500.
- **Inteligência Artificial:** Python, TensorFlow Lite / PyTorch (modelo exportado para inferência rápida).
- **Mobile:** React Native (iOS & Android).
- **Cloud / Infraestrutura:** Docker, Banco de Dados (PostgreSQL / MySQL).

---

## Estrutura do Repositório

Como este é um projeto multidisciplinar (Monorepo), os códigos estão divididos em subdiretórios específicos:

```text
├── /app-react-native   # Interface mobile (App de Controle e Admin)
├── /backend-spring     # API REST na Raspberry Pi (cliente OPC-UA, Câmera, IA e Rede)
├── /cloud-docker       # Arquivos docker-compose para subir a nuvem
├── /docs               # Documentação complementar, diagramas e assets
├── /hardware           # Programa do CLP (TIA Portal — blocos/SCL) para sensores e atuadores
└── /ia-modelo          # Notebooks de treinamento e modelo de ML final
```
