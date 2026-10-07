package org.example.laserranitaentradas.model.entity;

/**
 * Por dónde se enteró la app de que una compra online quedó paga (Compra.pagoConfirmadoPor). Lo
 * normal es el webhook de Mercado Pago; si muchas se confirman por los otros dos caminos, el
 * webhook no está llegando (Sistema > Estado > Ventas). Se guarda como texto, no como enum de la
 * base, por el CHECK que deja ddl-auto con la lista de valores vieja.
 */
public enum ConfirmacionPago {
    /** Aviso de Mercado Pago (POST /api/pagos/webhook). */
    WEBHOOK,
    /** El navegador del cliente pidió verificar el pago (pantalla de resultado / polling). */
    VERIFICACION,
    /** El barrido de checkouts abandonados encontró el pago antes de cancelarla. */
    BARRIDO
}
