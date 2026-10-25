package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;

/** Reemplaza los valores tal cual: null = sin límite o sin vencimiento. */
@Data
public class ActualizarCuponRequest {
    private Integer usosMaximos;
    private LocalDate fechaExpiracion;
    private Boolean activo;
}
