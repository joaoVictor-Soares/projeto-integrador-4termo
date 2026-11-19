package com.pi.demo.service;

import com.pi.demo.dto.AtuadorStatusDto;

/**
 * Comandos de alto nivel sobre o ESP (co-processador de I/O da mesa, ligado por USB serial).
 *
 * Implementacao prevista para a proxima entrega (camada Service): abre a porta serial com jSerialComm,
 * mantem uma thread dedicada lendo "SENSOR:DETECTADO" / "SENSOR:LIVRE" linha a linha, e escreve '1'/'0'
 * para acionar o rele do atuador. Esta interface e o contrato que os Controllers desta entrega usam.
 */
public interface AtuadorService {

    AtuadorStatusDto status();

    /**
     * Aciona o atuador manualmente: energiza o rele, aguarda {@link #tempoAcionamentoMs()} e desenergiza.
     * Mesmo fluxo de temporizador usado pela inspecao automatica ao reprovar uma peca.
     */
    void acionarManual();

    /** Duracao atual do ciclo avanca+recua, em milissegundos (variavel global ajustavel em runtime). */
    long tempoAcionamentoMs();

    /** Altera a duracao do ciclo. Implementacoes devem validar limites (ver AtuadorController). */
    void definirTempoAcionamentoMs(long ms);
}
