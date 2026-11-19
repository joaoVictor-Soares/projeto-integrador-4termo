package com.pi.demo.service;

import java.time.Duration;

/** Controle de teste do ESP simulado (sem hardware), por endereco de bit. Substitui ArduinoSimuladoControl. */
public interface EspSimuladoControl {

    /** Liga/desliga a entrada no endereco informado "no bruto" (fica assim ate ser alterada de novo). */
    void definirBit(int endereco, boolean ativo);

    /** Liga a entrada no endereco informado por {@code duracao} e solta sozinha (simula uma peca passando). */
    void pulsarBit(int endereco, Duration duracao);
}
