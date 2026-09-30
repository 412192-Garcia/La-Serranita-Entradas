package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Lo que se puede cambiar de un cupón ya creado. Reemplaza los tres valores tal cual vienen:
 * usosMaximos null = sin límite, fechaExpiracion null = sin vencimiento.
 */
@Data
public class ActualizarCuponRequest {
    private Integer usosMaximos;
    private LocalDate fechaExpiracion;
    private Boolean activo;
}
