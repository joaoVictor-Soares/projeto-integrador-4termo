
package com.pi.demo.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "dados_producao")
@NoArgsConstructor
@AllArgsConstructor
public class DadosProducao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "esteira_id", nullable = false)
    private Esteira esteira;

    @Column(name = "data_hora_inicio")
    private LocalDateTime dataHoraInicio;

    @Column(name = "total_processadas")
    private Integer totalProcessadas;

    @Column(name = "total_aprovadas")
    private Integer totalAprovadas;

    @Column(name = "total_recusadas")
    private Integer totalRecusadas;
}

