package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.TipoNotificacion;

import java.util.List;

/**
 * Sistema genérico de avisos (punto rojo en la cabecera interna): cada feature (cajas atrasadas,
 * rechazos pendientes, lo que se agregue a futuro) calcula su propia lista de "candidatos"
 * vigentes (ids de la entidad que sea) y este servicio decide si eso cuenta como pendiente para
 * un usuario puntual, según si el tipo se apaga con sólo verlo (TipoNotificacion.
 * desapareceAlVerse) o exige una acción explícita sobre la entidad referida.
 */
public interface NotificacionService {

    /** true si, de los candidatos vigentes, hay algo pendiente de avisarle a este usuario. */
    boolean hayPendientes(TipoNotificacion tipo, List<Long> refIdsActuales, Long usuarioId);

    /** Marca estos ids como vistos por este usuario para este tipo. Sólo tiene efecto real en
     * tipos con desapareceAlVerse=true; en los demás no rompe nada, simplemente no se consulta.
     *
     * Devuelve los ids que hasta este momento estaban SIN ver para este usuario, o sea los que
     * estaban prendiendo el aviso: la pantalla los usa para señalar cuáles son las novedades entre
     * todos los candidatos (ej. cuál de las cajas atrasadas es la nueva). Vacío si no había nada
     * nuevo o el tipo no se apaga al verse. */
    List<Long> marcarVistas(TipoNotificacion tipo, List<Long> refIds, Long usuarioId);
}
