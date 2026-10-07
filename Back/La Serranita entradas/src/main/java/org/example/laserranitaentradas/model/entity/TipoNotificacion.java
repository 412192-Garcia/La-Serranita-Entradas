package org.example.laserranitaentradas.model.entity;

/**
 * Tipo de aviso del sistema de notificaciones genérico (punto rojo en la cabecera interna).
 * desapareceAlVerse decide si con que un usuario VEA la lista de candidatos alcanza para apagar
 * el aviso PARA ESE usuario (ver NotificacionService.hayPendientes/marcarVistas), o si hace falta
 * una acción explícita sobre la entidad referida.
 */
public enum TipoNotificacion {
    /** Caja sin cerrar de un día anterior a hoy (ver CajaService.getIdsCajasAtrasadas): se apaga
     * para un admin en cuanto ve la lista de "Cajas abiertas ahora". Si aparece una caja atrasada
     * nueva, vuelve a prender para los admins que todavía no la vieron. */
    CAJA_ATRASADA(true),
    /** Operación del POS rechazada por el servidor (ver RechazoOperacionService.getIdsPendientes):
     * sigue prendida hasta que un admin la marca resuelta explícitamente (OperacionRechazada.
     * resuelto) — verla en la lista no alcanza. */
    RECHAZO_OPERACION(false),
    /** Facturación con algo para resolver (ver FacturaService.idsAlertasFacturacion): facturas en
     * ERROR o trabadas, certificado de ARCA por vencer o numeración desfasada. Sigue prendida hasta
     * que se resuelva. Como no se apaga al verse, nunca se guarda en notificaciones_vistas. */
    FACTURACION(false),
    /** Alguna tarjeta de Sistema > Estado en rojo (ver EstadoSistemaService.idsAlertas). Sigue
     * prendida hasta que se resuelva; nunca se guarda en notificaciones_vistas. */
    SISTEMA(false);

    private final boolean desapareceAlVerse;

    TipoNotificacion(boolean desapareceAlVerse) {
        this.desapareceAlVerse = desapareceAlVerse;
    }

    public boolean isDesapareceAlVerse() {
        return desapareceAlVerse;
    }
}
