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
    RECHAZO_OPERACION(false);

    private final boolean desapareceAlVerse;

    TipoNotificacion(boolean desapareceAlVerse) {
        this.desapareceAlVerse = desapareceAlVerse;
    }

    public boolean isDesapareceAlVerse() {
        return desapareceAlVerse;
    }
}
