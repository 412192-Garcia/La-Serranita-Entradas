package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import org.example.laserranitaentradas.model.entity.AplicacionDescuento;

import java.time.LocalDate;
import java.util.Set;
import java.math.BigDecimal;

@Data
public class CrearCuponRequest {
    private String codigo;
    private Integer usosMaximos;
    private LocalDate fechaExpiracion;
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
    private LocalDate fechaDesde;
    private AplicacionDescuento aplicaPor;
    private Set<Long> tiposEntradaIds;
    private Integer minEntradas;
    private Integer maxEntradasAfectadas;
    private BigDecimal topeDescuento;
}
