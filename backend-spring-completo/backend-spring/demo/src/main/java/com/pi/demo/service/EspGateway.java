package com.pi.demo.service;

import com.pi.demo.exception.EspException;

/**
 * Acesso ao ESP (co-processador de I/O via USB Serial). Substitui o antigo ArduinoGateway.
 *
 * O controle e por ENDERECAMENTO DE BITS: entradas e saidas sao identificadas por um
 * numero inteiro (endereco), e nao por um unico "sensor"/"rele" fixos — ver EspProperties
 * para os enderecos usados hoje (sensor capacitivo e cilindro de rejeicao).
 *
 * O ESP tem saida fisica BAIXO-ATIVA, mas isso e um detalhe do FIRMWARE: os valores
 * trocados aqui (em lerBit/escreverBit) sao sempre LOGICOS (true = ativo/acionado). A
 * conversao para o nivel eletrico real (LOW=ativo) acontece dentro do ESP, nunca neste
 * lado Java.
 *
 * Implementacoes:
 *  - EspSerialGateway   : porta serial real (jSerialComm), esp.simulado=false
 *  - EspSimuladoGateway : em memoria, sem hardware, esp.simulado=true
 */
public interface EspGateway {

    /** true se a porta serial esta aberta e o firmware respondendo. */
    boolean conectado();

    boolean simulado();

    /** Identificador da porta (ex.: /dev/ttyUSB0) ou "SIMULADO". */
    String porta();

    /** Ultimo estado LOGICO conhecido da entrada no endereco de bit informado. */
    boolean lerBit(int endereco);

    /**
     * Define o estado LOGICO da saida no endereco de bit informado (true = ativar). O ESP
     * converte isso para o nivel fisico baixo-ativo internamente.
     *
     * @throws EspException se a porta nao estiver conectada.
     */
    void escreverBit(int endereco, boolean ativar);
}
