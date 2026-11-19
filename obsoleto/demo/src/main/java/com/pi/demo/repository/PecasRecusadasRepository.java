
package com.pi.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pi.demo.model.PecasRecusadas;

import java.util.List;

public interface PecasRecusadasRepository extends JpaRepository<PecasRecusadas, Integer> {

    List<PecasRecusadas> findByEsteiraId(Integer esteiraId);

    List<PecasRecusadas> findByOperadorId(Integer operadorId);

}
