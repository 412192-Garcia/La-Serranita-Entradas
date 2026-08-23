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

    /**
     * Clave que genera el POS antes de mandar el ingreso. Si se reintenta con la misma clave
     * (porque la respuesta original se perdió en un corte), se devuelve el ingreso ya
     * registrado en vez de duplicarlo. Null desde clientes que no usan la cola offline.
     */
    private String idempotencyKey;

    /** Cuándo se hizo de verdad el ingreso, si estuvo encolado sin conexión. Null = ahora. */
    private LocalDateTime fechaOriginal;

    /** Ver el mismo campo en VentaPosRequestDTO: sólo true en un reintento en segundo plano de
     * la cola offline, para decidir si el rechazo amerita quedar registrado para un admin. */
    private Boolean esReintentoEncolado;

    /** Ver el mismo campo en RetiroCajaRequestDTO: sólo para que, si esto se rechaza, el
     * rechazo guardado sepa de qué caja reabrir y reintentar. */
    private Long cajaId;
}
