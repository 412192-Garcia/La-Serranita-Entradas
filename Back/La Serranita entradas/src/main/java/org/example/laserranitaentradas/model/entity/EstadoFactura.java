package org.example.laserranitaentradas.model.entity;

public enum EstadoFactura {
    /**
     * Esperando CAE: recién creada, o el último intento falló por algo transitorio (ARCA caída,
     * timeout, sin internet). El job de emisión la vuelve a intentar sola.
     */
    PENDIENTE,
    /** ARCA la autorizó: tiene número y CAE. Estado final. */
    EMITIDA,
    /**
     * ARCA la rechazó (Resultado "R") o se agotaron los reintentos. No se reintenta sola:
     * necesita que un admin revise el error y la reintente a mano.
     */
    ERROR
}
