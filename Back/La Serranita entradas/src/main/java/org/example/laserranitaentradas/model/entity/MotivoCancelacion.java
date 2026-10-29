package org.example.laserranitaentradas.model.entity;

/** Por qué quedó CANCELADO una compra. Se guarda como texto por el CHECK viejo que deja ddl-auto. */
public enum MotivoCancelacion {
    /** Abrió el checkout de MP y no intentó pagar. */
    CHECKOUT_ABANDONADO,
    /** Intentó pagar y no se aprobó. El detalle tiene la respuesta de MP. */
    PAGO_RECHAZADO,
    /** Falló la preferencia de MP: nunca tuvo el link. El detalle tiene el error. */
    PAGO_NO_INICIADO,
    /** Un admin anuló una venta de caja. El motivo va en detalleCancelacion. */
    ANULADA_EN_CAJA,
    /** Un admin canceló una RESERVA_ADMIN. No hay reembolso. */
    RESERVA_CANCELADA
}
