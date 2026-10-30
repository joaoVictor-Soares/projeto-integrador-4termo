package com.pi.demo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@Entity 
@Table (name = "pecas_recusadas")
@NoArgsConstructor 
@AllArgsConstructor 
public class PecasRecusadas {
 
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column (name = "esteira_id", nullable = false)
    private Long esteiraId;

    @Column (name = "operador_id")
    private Long operadorId;

    @Column (name = "data_hora")
    private LocalDateTime dataHora;

    @Column (name = "imagem_url")
    private String imagemUrl;

    @Column(name = "erro_identificado")
    private String erroIdentificado;

    @Column(name = "nivel_acuracia")
    private BigDecimal nivelAcuracia;
    

}
