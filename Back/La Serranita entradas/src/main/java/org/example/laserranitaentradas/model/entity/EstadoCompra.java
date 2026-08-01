package org.example.laserranitaentradas.model.entity;

public enum EstadoCompra {
    PENDIENTE_PAGO,
    RESERVADO_EFECTIVO,
    APROBADO,
    USADO,
    CANCELADO,
    /**
     * Venta cerrada en la boletería del parque: se cobró y el visitante entró en el mismo
     * acto. Cuenta como cobrada y como ingreso validado igual que USADO — la diferencia es
     * que nunca fue una reserva anticipada, así que boletería no la lista.
     */
    VENDIDO_EN_PUERTA,
    /**
     * Compra paga online a la que se le devolvió el dinero antes de que el visitante
     * entrara. Sólo alcanzable desde APROBADO (nunca desde USADO: si ya entró, no hay
     * reembolso).
     */
    REEMBOLSADA
}
