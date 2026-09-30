package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;

/** Cambio en bloque sobre todos los cupones de un lote: activo (para /activo) o fechaExpiracion (para /vencimiento, null = sin vencimiento). */
@Data
public class ActualizarFamiliaCuponRequest {
    private Boolean activo;
    private LocalDate fechaExpiracion;
}
