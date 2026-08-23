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

    /** Ver el mismo campo en VentaPosRequestDTO: sólo true en un reintento en segundo plano de
     * la cola offline, para decidir si el rechazo amerita quedar registrado para un admin. */
    private Boolean esReintentoEncolado;

    /** Caja del boletero en el momento de hacer el movimiento (la conoce siempre: es la que
     * tiene abierta en su POS). No se usa para resolver a qué caja aplicar el movimiento — eso
     * sigue siendo la caja abierta del usuario autenticado — pero si esto se rechaza porque esa
     * caja ya no está abierta, queda guardado en el rechazo para que un admin sepa exactamente
     * cuál reabrir y reintentar (ver RechazoOperacionService.reabrirYReintentar). */
    private Long cajaId;
}
