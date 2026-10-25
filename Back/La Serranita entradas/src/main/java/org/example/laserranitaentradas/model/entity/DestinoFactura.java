package org.example.laserranitaentradas.model.entity;

/** Qué se hace con la factura emitida. Mail sin email = no facturar: ni se crea la Factura. */
public enum DestinoFactura {
    IMPRIMIR,
    MAIL,
    /** Sin envío: notas de crédito y refacturaciones de un admin. Quedan en PDF. */
    NINGUNO
}
