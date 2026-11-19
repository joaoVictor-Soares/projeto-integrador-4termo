package com.pi.demo.dto;

/**
 * Configuracao do temporizador de acionamento do atuador.
 * O avanco e o retorno do cilindro NAO sao controlados pelo firmware do ESP (que so liga/desliga
 * o rele): quem decide por quanto tempo o rele fica energizado antes de desligar e o backend, usando
 * este valor. Trata-se de uma "variavel global" em tempo de execucao (ver AtuadorService), para poder
 * calibrar o tempo de curso do cilindro na bancada sem recompilar nada.
 */
public record AtuadorConfigDto(long tempoAcionamentoMs) {
}
