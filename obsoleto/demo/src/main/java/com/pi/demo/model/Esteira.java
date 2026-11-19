
package com.pi.demo.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@Entity
@Table(name = "esteiras")
@NoArgsConstructor
@AllArgsConstructor
public class Esteira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nome_identificador")
    private String nomeIdentificador;

    @Column(name = "velocidade_atual")
    private Integer velocidadeAtual;

    private String status;

    @Column(name = "data_alteracao")
    private LocalDateTime dataAlteracao;

    @OneToMany(mappedBy = "esteira")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DadosProducao> dadosProducao;

    @OneToMany(mappedBy = "esteira")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PecasRecusadas> pecasRecusadas;
}
