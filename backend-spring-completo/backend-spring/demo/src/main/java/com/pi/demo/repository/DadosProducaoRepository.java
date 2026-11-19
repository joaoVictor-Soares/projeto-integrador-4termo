package com.pi.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pi.demo.model.DadosProducao;

public interface DadosProducaoRepository extends JpaRepository<DadosProducao, Integer> {

    List<DadosProducao> findByEsteiraIdOrderByDataHoraInicioDesc(Integer esteiraId);

    Optional<DadosProducao> findFirstByEsteiraIdOrderByDataHoraInicioDesc(Integer esteiraId);

    boolean existsByEsteiraId(Integer esteiraId);

    /** Incremento atomico no banco (evita perda de contagem em acessos concorrentes). */
    @Modifying
    @Query("""
            update DadosProducao d
               set d.totalProcessadas = coalesce(d.totalProcessadas, 0) + 1,
                   d.totalAprovadas = coalesce(d.totalAprovadas, 0) + :aprovadas,
                   d.totalRecusadas = coalesce(d.totalRecusadas, 0) + :recusadas
             where d.id = :id
            """)
    int incrementar(@Param("id") Integer id,
                    @Param("aprovadas") int aprovadas,
                    @Param("recusadas") int recusadas);
}
