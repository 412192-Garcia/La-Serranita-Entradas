package org.example.laserranitaentradas.model.entity;

/**
 * Por dónde se confirmó el pago de una compra online. Si muchas no llegan por el webhook, el webhook está
 * fallando. Texto por el CHECK de ddl-auto.
 */
public enum ConfirmacionPago {
    /** Aviso de Mercado Pago (POST /api/pagos/webhook). */
    WEBHOOK,
    /** El navegador del cliente pidió verificar el pago (pantalla de resultado / polling). */
    VERIFICACION,
    /** El barrido de checkouts abandonados encontró el pago antes de cancelarla. */
    BARRIDO
}
