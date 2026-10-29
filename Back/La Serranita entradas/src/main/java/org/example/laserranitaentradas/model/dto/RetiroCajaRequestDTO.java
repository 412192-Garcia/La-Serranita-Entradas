package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RetiroCajaRequestDTO {
    private BigDecimal monto;
    private String motivo;
    /** Null = RETIRO (compatibilidad con quien no lo mande). */
    private TipoMovimientoCaja tipo;

    /** Clave del POS para que un reintento no duplique. Null fuera de la cola offline. */
    private String idempotencyKey;

    /** Cuándo se hizo de verdad el movimiento, si estuvo encolado sin conexión. Null = ahora. */
    private LocalDateTime fechaOriginal;

    /** True si viene de un reintento en segundo plano: sólo esos rechazos se registran para el admin. */
    private Boolean esReintentoEncolado;

    /**
     * Caja del POS al hacer el movimiento. No decide dónde se aplica: si se rechaza, sirve para saber cuál
     * reabrir.
     */
    private Long cajaId;
}
