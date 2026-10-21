package org.example.laserranitaentradas.model.entity;

/**
 * Por qué quedó CANCELADO una compra (Compra.motivoCancelacion). Se guarda como texto, no como
 * enum de la base, por el CHECK que deja ddl-auto con la lista de valores vieja (igual que
 * ConfirmacionPago).
 */
public enum MotivoCancelacion {
    /** Checkout de Mercado Pago sin ningún intento de pago: abrió el checkout y no pagó. Lo
     * canceló el barrido pasadas las horas configuradas. */
    CHECKOUT_ABANDONADO,
    /** Checkout de Mercado Pago en el que intentó pagar y no se aprobó (tarjeta rechazada,
     * pago vencido o cancelado). El detalle dice qué contestó Mercado Pago en cada intento. */
    PAGO_RECHAZADO,
    /** Falló al pedirle la preferencia a Mercado Pago: el cliente nunca llegó a tener el link de
     * pago. El detalle tiene el error. */
    PAGO_NO_INICIADO,
    /** Un admin anuló una venta de caja (Cajas > operaciones). El motivo escrito (opcional) va en detalleCancelacion. */
    ANULADA_EN_CAJA,
    /** Un admin canceló una reserva generada desde Acciones (RESERVA_ADMIN): no se cobró nada
     * por la app, así que no hay nada que reembolsar. */
    RESERVA_CANCELADA
}
