package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.RegistroAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /**
     * Búsqueda del historial. Los filtros vacíos no filtran. `texto` busca en la descripción, el
     * detalle y la entidad (en minúsculas).
     */
    @Query("""
            SELECT r FROM RegistroAuditoria r
             WHERE r.fecha >= :desde AND r.fecha < :hasta
               AND (:usuario IS NULL OR LOWER(r.usuario) = :usuario)
               AND (:accion IS NULL OR r.accion = :accion)
               AND (:texto IS NULL OR LOWER(r.descripcion) LIKE :texto OR LOWER(COALESCE(r.detalle, '')) LIKE :texto
                    OR LOWER(COALESCE(r.entidadId, '')) LIKE :texto)
             ORDER BY r.fecha DESC, r.id DESC
            """)
    Page<RegistroAuditoria> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                   @Param("usuario") String usuario, @Param("accion") String accion,
                                   @Param("texto") String texto, Pageable pagina);

    /** Logins fallidos desde `desde`, por usuario intentado: [usuario, cantidad]. */
    @Query("SELECT r.usuario, COUNT(r) FROM RegistroAuditoria r WHERE r.accion = 'LOGIN_FALLIDO' AND r.fecha >= :desde " +
            "GROUP BY r.usuario ORDER BY COUNT(r) DESC")
    List<Object[]> loginsFallidosPorUsuario(@Param("desde") LocalDateTime desde);

    @Query("SELECT DISTINCT r.accion FROM RegistroAuditoria r ORDER BY r.accion")
    List<String> accionesRegistradas();

    @Query("SELECT DISTINCT r.usuario FROM RegistroAuditoria r WHERE r.usuario IS NOT NULL ORDER BY r.usuario")
    List<String> usuariosRegistrados();

    @Modifying
    @Transactional
    @Query("DELETE FROM RegistroAuditoria r WHERE r.fecha < :limite")
    int borrarAntesDe(@Param("limite") LocalDateTime limite);
}
