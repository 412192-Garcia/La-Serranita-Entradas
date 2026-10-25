package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.TipoNotificacion;

import java.util.List;

/**
 * Avisos del punto rojo: cada feature calcula sus candidatos y esto decide si están pendientes para un
 * usuario, según TipoNotificacion.desapareceAlVerse.
 */
public interface NotificacionService {

    /** De estos refIds, los que ya vio alguien. */
    java.util.Set<Long> vistosPorAlguien(TipoNotificacion tipo, List<Long> refIds);

    /** true si, de los candidatos vigentes, hay algo pendiente de avisarle a este usuario. */
    boolean hayPendientes(TipoNotificacion tipo, List<Long> refIdsActuales, Long usuarioId);

    /**
     * Marca los ids como vistos por el usuario. Devuelve los que estaban sin ver, para señalar las novedades.
     */
    List<Long> marcarVistas(TipoNotificacion tipo, List<Long> refIds, Long usuarioId);
}
