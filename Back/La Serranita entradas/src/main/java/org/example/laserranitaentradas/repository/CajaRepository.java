package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long>, JpaSpecificationExecutor<Caja> {

    /** Cajas cerradas desde `desde` (no borradas) cuya diferencia supera el umbral, en más o en menos. */
    @Query("SELECT c FROM Caja c JOIN FETCH c.usuario WHERE c.fechaCierre >= :desde " +
            "AND (c.habilitada IS NULL OR c.habilitada = true) AND c.diferencia IS NOT NULL " +
            "AND (c.diferencia >= :umbral OR c.diferencia <= -:umbral) ORDER BY c.fechaCierre DESC")
    List<Caja> cerradasConDiferencia(@Param("desde") LocalDateTime desde, @Param("umbral") java.math.BigDecimal umbral);
    /** Puede haber más de una si quedaron cajas de días anteriores. */
    List<Caja> findAllByUsuarioIdAndFechaCierreIsNull(Long usuarioId);

    /**
     * Cajas cerradas por día de apertura, la más reciente primero. Incluye las deshabilitadas: el reporte las
     * filtra.
     */
    List<Caja> findAllByFechaAperturaBetweenAndFechaCierreIsNotNullOrderByFechaAperturaDesc(LocalDateTime desde, LocalDateTime hasta);

    /** Boleteros ("nombre apellido") con alguna caja cerrada habilitada, para el filtro. */
    @Query("SELECT DISTINCT CONCAT(u.nombre, ' ', u.apellido) FROM Caja c JOIN c.usuario u "
            + "WHERE c.fechaCierre IS NOT NULL AND (c.habilitada IS NULL OR c.habilitada = true) "
            + "ORDER BY CONCAT(u.nombre, ' ', u.apellido)")
    List<String> findNombresBoleterosConCajasCerradas();

    /** Ids de las cajas deshabilitadas por un admin: para descartar sus ventas del reporte. */
    @Query("SELECT c.id FROM Caja c WHERE c.habilitada = false")
    List<Long> findIdsDeshabilitadas();

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<Caja> findAllByFechaCierreIsNullOrderByFechaAperturaAsc();

    /** Cajas abiertas antes de hoy (aviso CAJA_ATRASADA). */
    List<Caja> findAllByFechaCierreIsNullAndFechaAperturaBefore(LocalDateTime limite);

    /** Cajas sin control abiertas, para el cierre automático. */
    List<Caja> findAllByFechaCierreIsNullAndControlOmitidoTrue();
}
