package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoMovimientoEntradas;

import java.time.LocalDateTime;

@Data
public class IngresoEntradasRequestDTO {
    private Integer cantidad;

    /** Null = INGRESO (compatibilidad con quien no lo mande). */
    private TipoMovimientoEntradas tipo;

    /** Opcional (tiene sentido completarlo en un RETIRO, pero no es obligatorio). */
    private String motivo;

    /** Clave del POS para que un reintento no duplique. Null fuera de la cola offline. */
    private String idempotencyKey;

    /** Cuándo se hizo de verdad el ingreso, si estuvo encolado sin conexión. Null = ahora. */
    private LocalDateTime fechaOriginal;

    /** True si viene de un reintento en segundo plano: sólo esos rechazos se registran para el admin. */
    private Boolean esReintentoEncolado;

    /** Si se rechaza, para saber qué caja reabrir. */
    private Long cajaId;
}
