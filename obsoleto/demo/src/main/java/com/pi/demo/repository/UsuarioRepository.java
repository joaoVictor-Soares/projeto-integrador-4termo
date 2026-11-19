
package com.pi.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pi.demo.model.Usuario;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmail(String email);

}

