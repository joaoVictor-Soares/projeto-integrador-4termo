package com.pi.demo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do co-processador de I/O: um ESP8266 NodeMCU (ESP-12E) conectado a
 * Raspberry Pi por USB Serial. Substitui o antigo ArduinoProperties.
 *
 * Protocolo do firmware "mesa-de-lorenzo": texto a 115200 bps. A rejeicao e o comando
 * "NOK\n"; o tempo de acionamento e fixo no firmware (5 s). Nao ha frames de entrada.
 * Pinagem: saida de rejeicao no D1 (GPIO5). D1/D2 sao SCL/SDA nesta placa.
 */
@ConfigurationProperties(prefix = "esp")
public record EspProperties(
        /** true = ESP simulado em memoria (sem hardware). */
        boolean simulado,
        /** Porta serial, ex.: /dev/ttyUSB0 (ESP com chip CP2102/CH340) ou /dev/ttyACM0. */
        String porta,
        int baudRate,
        /** Intervalo entre tentativas de reconexao quando a porta nao responde. */
        Duration intervaloReconexao,
        /**
         * Tempo (em ms) que a saida do cilindro fica acionada antes do retorno automatico.
         * Valor INICIAL da "variavel global" controlada em runtime por AtuadorService
         * (GET/PUT /api/atuador/configuracao).
         */
        long tempoAcionamentoInicialMs,
        /** Endereco de bit (lado ENTRADA) do sensor capacitivo. */
        int enderecoSensorCapacitivo,
        /** Endereco de bit (lado SAIDA) do cilindro de rejeicao. */
        int enderecoCilindroRejeicao) {
}
