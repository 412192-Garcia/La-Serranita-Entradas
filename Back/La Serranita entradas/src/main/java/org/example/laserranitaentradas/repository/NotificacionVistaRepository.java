package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.NotificacionVista;
import org.example.laserranitaentradas.model.entity.NotificacionVistaId;
import org.example.laserranitaentradas.model.entity.TipoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionVistaRepository extends JpaRepository<NotificacionVista, NotificacionVistaId> {
    /** De estos refIds, cuáles ya vio este usuario para este tipo — para calcular pendientes o
     * filtrar cuáles faltan marcar como vistas (ver NotificacionServiceImpl). */
    List<NotificacionVista> findAllByTipoAndUsuarioIdAndRefIdIn(TipoNotificacion tipo, Long usuarioId, List<Long> refIds);

    /** De estos refIds, cuáles ya vio alguien (cualquier usuario). */
    @Query("SELECT DISTINCT v.refId FROM NotificacionVista v WHERE v.tipo = :tipo AND v.refId IN :refIds")
    List<Long> refIdsVistosPorAlguien(@Param("tipo") TipoNotificacion tipo, @Param("refIds") List<Long> refIds);
}
