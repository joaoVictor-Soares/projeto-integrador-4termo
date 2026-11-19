
package com.pi.demo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pecas_recusadas")
@NoArgsConstructor
@AllArgsConstructor
public class PecasRecusadas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "esteira_id", nullable = false)
    private Esteira esteira;

    @ManyToOne
    @JoinColumn(name = "operador_id")
    private Usuario operador;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;

    @Column(name = "imagem_url")
    private String imagemUrl;

    @Column(name = "erro_identificado")
    private String erroIdentificado;

    @Column(name = "nivel_acuracia", precision = 5, scale = 4)
    private BigDecimal nivelAcuracia;
}
