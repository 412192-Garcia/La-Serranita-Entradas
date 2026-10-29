package org.example.laserranitaentradas.model.entity;

/**
 * Tipo de aviso (punto rojo en la cabecera). desapareceAlVerse: si verlo alcanza para apagarlo para ese
 * usuario, o hace falta resolverlo.
 */
public enum TipoNotificacion {
    /**
     * Caja abierta de un día anterior. Se apaga al ver "Cajas abiertas ahora"; una nueva vuelve a prenderlo.
     */
    CAJA_ATRASADA(true),
    /** Operación del POS rechazada. Sigue hasta que un admin la marca resuelta. */
    RECHAZO_OPERACION(false),
    /**
     * Facturas en ERROR o trabadas, certificado por vencer o numeración desfasada. Sigue hasta que se
     * resuelva.
     */
    FACTURACION(false),
    /** Alguna tarjeta de Sistema > Estado en rojo. Sigue hasta que se resuelva. */
    SISTEMA(false);

    private final boolean desapareceAlVerse;

    TipoNotificacion(boolean desapareceAlVerse) {
        this.desapareceAlVerse = desapareceAlVerse;
    }

    public boolean isDesapareceAlVerse() {
        return desapareceAlVerse;
    }
}
