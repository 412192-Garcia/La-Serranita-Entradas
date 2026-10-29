package org.example.laserranitaentradas.model.entity;

public enum EstadoCompra {
    PENDIENTE_PAGO,
    RESERVADO_EFECTIVO,
    APROBADO,
    USADO,
    CANCELADO,
    /**
     * Venta en la boletería: cobro e ingreso en el mismo acto. Cuenta como USADO, pero boletería no la lista.
     */
    VENDIDO_EN_PUERTA,
    /** Devuelta antes de entrar. Sólo desde APROBADO. */
    REEMBOLSADA
}
