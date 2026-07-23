package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "descuentos_efectivo")
@Data
public class DescuentoEfectivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer cantidadPases;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioPromocionalTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_entrada", nullable = false)
    private TipoEntrada tipoEntrada;
}
