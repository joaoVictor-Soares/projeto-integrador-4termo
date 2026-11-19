package com.pi.demo.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pi.demo.exception.RecursoNaoEncontradoException;
import com.pi.demo.model.DadosProducao;
import com.pi.demo.model.Esteira;
import com.pi.demo.repository.DadosProducaoRepository;

/** Contadores de producao (um registro por "turno"/rodada de producao da esteira). */
@Service
public class ProducaoService {

    private final DadosProducaoRepository repository;
    private final EsteiraService esteiraService;

    public ProducaoService(DadosProducaoRepository repository, EsteiraService esteiraService) {
        this.repository = repository;
        this.esteiraService = esteiraService;
    }

    @Transactional(readOnly = true)
    public List<DadosProducao> historico(Integer esteiraId) {
        esteiraService.buscar(esteiraId);
        return repository.findByEsteiraIdOrderByDataHoraInicioDesc(esteiraId);
    }

    @Transactional(readOnly = true)
    public DadosProducao atual(Integer esteiraId) {
        esteiraService.buscar(esteiraId);
        return repository.findFirstByEsteiraIdOrderByDataHoraInicioDesc(esteiraId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum registro de producao para a esteira " + esteiraId + "."));
    }

    /** Encerra a rodada atual (se houver) abrindo um novo registro zerado. */
    @Transactional
    public DadosProducao iniciarNovaRodada(Integer esteiraId) {
        return novo(esteiraService.buscar(esteiraId));
    }

    /** Registro corrente da esteira; cria se ainda nao existe nenhum. */
    @Transactional
    public DadosProducao obterOuIniciar(Esteira esteira) {
        return repository.findFirstByEsteiraIdOrderByDataHoraInicioDesc(esteira.getId())
                .orElseGet(() -> novo(esteira));
    }

    /** Soma 1 processada e 1 aprovada/recusada de forma atomica. */
    @Transactional
    public void contabilizar(Esteira esteira, boolean aprovada) {
        DadosProducao atual = obterOuIniciar(esteira);
        repository.incrementar(atual.getId(), aprovada ? 1 : 0, aprovada ? 0 : 1);
    }

    private DadosProducao novo(Esteira esteira) {
        DadosProducao d = new DadosProducao();
        d.setEsteira(esteira);
        d.setDataHoraInicio(LocalDateTime.now());
        d.setTotalProcessadas(0);
        d.setTotalAprovadas(0);
        d.setTotalRecusadas(0);
        return repository.save(d);
    }
}
