Sistema Automatizado de Inspeção de Peças com IA e IoT, um projeto integrador do 4º termo do Tecnólogo em Análise e Desenvolvimento de Sistemas.

Este repositório contém o código-fonte e a documentação para o Sistema de Verificação de Peças, uma solução ponta a ponta que integra hardware (IoT), inteligência artificial na borda (Edge Computing), backend local, nuvem e um aplicativo móvel.

O objetivo do projeto é identificar e classificar o status de peças em uma esteira de produção em tempo real, fornecendo também o controle motorizado da linha e relatórios detalhados na nuvem para administradores.

---

Arquitetura do Sistema

O sistema foi arquitetado para garantir baixa latência na fábrica e armazenamento seguro na nuvem, operando em quatro frentes:

1. Hardware & Sensores: Uma esteira automatizada por um microcontrolador. O sensor (presença/capacitivo) atua como gatilho, indicando à Raspberry Pi o momento exato de acionar a câmera externa.
2. Edge Computing (Raspberry Pi):O cérebro local. Roda uma API em Spring Boot responsável por orquestrar o hardware (via GPIO/USB) e processar a imagem localmente através de um modelo de Machine Learning (IA).
3. Controle Móvel: Um aplicativo React Native na rede local. Permite aos operadores visualizarem a foto recém-tirada, o status da peça, o histórico diário da máquina e ajustarem a velocidade da esteira em tempo real. 
4. Nuvem & Big Data: Um banco de dados isolado em um container Docker no servidor disponibilizado, recebendo o histórico de inferências de forma assíncrona.

O diagrama esquemático completo da arquitetura está disponível na pasta `/docs/esquematico.jpg`.

---

Stack Tecnológico

Hardware Associado
 Raspberry Pi: Processamento local, hospedagem do Backend e do modelo de IA.
 Microcontrolador (Ex: ESP32 / Arduino): Controle PWM do motor da esteira.
 Câmera Externa: Captura padronizada de imagens.
 Sensor Capacitivo / Presença: Acionamento do pipeline de verificação.

Software
 Backend (Edge): Java 17+ e Spring Boot.
 Inteligência Artificial: Python, TensorFlow Lite / PyTorch (Modelo exportado para inferência rápida).
 Mobile: React Native (iOS & Android).
 Cloud / Infraestrutura: Docker, Banco de Dados (PostgreSQL / MySQL).

---

Estrutura do Repositório

Como este é um projeto multidisciplinar (Monorepo), os códigos estão divididos em subdiretórios específicos:

```text
├── /app-react-native   # Interface mobile (App de Controle e Admin)
├── /backend-spring     # API REST na Raspberry Pi (Integração de Câmera, IA e Rede)
├── /cloud-docker       # Arquivos docker-compose para subir a nuvem
├── /docs               # Documentação complementar, diagramas e assets
├── /hardware           # Scripts em C/C++ do microcontrolador da esteira
└── /ia-modelo          # Notebooks de treinamento e modelo de ML final