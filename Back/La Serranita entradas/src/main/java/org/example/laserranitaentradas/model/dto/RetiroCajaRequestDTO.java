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

    /**
     * Clave que genera el POS antes de mandar el movimiento. Si se reintenta con la misma clave
     * (porque la respuesta original se perdió en un corte), se devuelve el movimiento ya
     * registrado en vez de duplicarlo. Null desde clientes que no usan la cola offline.
     */
    private String idempotencyKey;

    /** Cuándo se hizo de verdad el movimiento, si estuvo encolado sin conexión. Null = ahora. */
    private LocalDateTime fechaOriginal;
}
