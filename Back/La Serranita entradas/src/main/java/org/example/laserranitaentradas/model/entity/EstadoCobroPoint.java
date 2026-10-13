package org.example.laserranitaentradas.model.entity;

import java.util.Set;

/** Estado de un {@link CobroPoint}, traducido de los status de la orden de Mercado Pago. */
public enum EstadoCobroPoint {
    /** Creada en MP, el Point todavía no la tomó. */
    CREADO,
    /** El Point la tomó y está esperando la tarjeta (o procesándola). */
    EN_TERMINAL,
    /** El Point pide confirmar ahí mismo si salió o no (action_required). */
    REVISAR_EN_POINT,
    APROBADO,
    RECHAZADO,
    CANCELADO,
    EXPIRADO,
    DEVUELTO,
    /** No se pudo ni crear la orden (MP caído, Point mal configurado...). */
    ERROR;

    private static final Set<EstadoCobroPoint> EN_CURSO = Set.of(CREADO, EN_TERMINAL, REVISAR_EN_POINT);

    /** Todavía puede cambiar solo: hay que seguir esperando al Point. */
    public boolean enCurso() {
        return EN_CURSO.contains(this);
    }
}
