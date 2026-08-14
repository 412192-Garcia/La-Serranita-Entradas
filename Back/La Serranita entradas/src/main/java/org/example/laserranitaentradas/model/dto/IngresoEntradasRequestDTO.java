package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IngresoEntradasRequestDTO {
    private Integer cantidad;

    /**
     * Clave que genera el POS antes de mandar el ingreso. Si se reintenta con la misma clave
     * (porque la respuesta original se perdió en un corte), se devuelve el ingreso ya
     * registrado en vez de duplicarlo. Null desde clientes que no usan la cola offline.
     */
    private String idempotencyKey;

    /** Cuándo se hizo de verdad el ingreso, si estuvo encolado sin conexión. Null = ahora. */
    private LocalDateTime fechaOriginal;
}
