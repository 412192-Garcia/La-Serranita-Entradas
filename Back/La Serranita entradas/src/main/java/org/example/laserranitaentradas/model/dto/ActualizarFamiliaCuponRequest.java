package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;

/** Cambio en bloque sobre un lote: activo o fechaExpiracion (null = sin vencimiento). */
@Data
public class ActualizarFamiliaCuponRequest {
    private Boolean activo;
    private LocalDate fechaExpiracion;
}
