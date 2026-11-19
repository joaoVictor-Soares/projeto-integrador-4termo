package com.pi.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pi.demo.model.PecasRecusadas;

public interface PecasRecusadasRepository extends JpaRepository<PecasRecusadas, Integer> {

    List<PecasRecusadas> findByEsteiraId(Integer esteiraId);

    List<PecasRecusadas> findByOperadorId(Integer operadorId);

    List<PecasRecusadas> findAllByOrderByDataHoraDesc();

    List<PecasRecusadas> findByEsteiraIdOrderByDataHoraDesc(Integer esteiraId);

    boolean existsByEsteiraId(Integer esteiraId);

    boolean existsByOperadorId(Integer operadorId);
}
