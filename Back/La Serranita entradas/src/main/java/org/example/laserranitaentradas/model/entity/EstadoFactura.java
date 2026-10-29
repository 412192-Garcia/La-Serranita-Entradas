package org.example.laserranitaentradas.model.entity;

public enum EstadoFactura {
    /** Esperando CAE: nueva o con una falla transitoria. El job la reintenta. */
    PENDIENTE,
    /** ARCA la autorizó: tiene número y CAE. Estado final. */
    EMITIDA,
    /** ARCA la rechazó o se agotaron los reintentos. Un admin la reintenta a mano. */
    ERROR,
    /**
     * La venta se canceló o cambió antes de autorizarse: no se emite. Si ya estaba EMITIDA se le hace una
     * nota de crédito.
     */
    ANULADA
}
