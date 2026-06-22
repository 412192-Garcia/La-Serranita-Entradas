package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class CrearFamiliaCuponRequest {
    private String nombre;
    private String prefijo;
    private String descripcion;
    private Integer cantidad;
    private Integer usosMaximos;
    private LocalDate fechaExpiracion;
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
}
