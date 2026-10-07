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
    /** Todas las cajas sin cerrar de este usuario — puede haber más de una si quedaron cajas de
     * días anteriores sin cerrar (ver CajaServiceImpl.getCajaOperativaHoy): a diferencia de un
     * findBy...Optional, esto no rompe si hay más de una fila. */
    List<Caja> findAllByUsuarioIdAndFechaCierreIsNull(Long usuarioId);

    /** Cajas ya cerradas dentro del rango, para el reporte de faltantes/sobrantes por turno — la más reciente primero.
     * Incluye las deshabilitadas: el reporte las filtra en memoria (ver ReporteServiceImpl) para no cambiar este OrderBy. */
    List<Caja> findAllByFechaCierreBetweenOrderByFechaCierreDesc(LocalDateTime desde, LocalDateTime hasta);

    /** Nombres ("nombre apellido", el mismo texto que CajaResumenReporteDTO) de los boleteros con al
     * menos una caja cerrada y habilitada, en orden alfabético: son los chips del filtro de "Cajas
     * cerradas", que ya no depende de un rango de fechas. */
    @Query("SELECT DISTINCT CONCAT(u.nombre, ' ', u.apellido) FROM Caja c JOIN c.usuario u "
            + "WHERE c.fechaCierre IS NOT NULL AND (c.habilitada IS NULL OR c.habilitada = true) "
            + "ORDER BY CONCAT(u.nombre, ' ', u.apellido)")
    List<String> findNombresBoleterosConCajasCerradas();

    /** Ids de las cajas deshabilitadas por un admin: para descartar sus ventas del reporte. */
    @Query("SELECT c.id FROM Caja c WHERE c.habilitada = false")
    List<Long> findIdsDeshabilitadas();

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<Caja> findAllByFechaCierreIsNullOrderByFechaAperturaAsc();

    /** Cajas sin cerrar cuya apertura fue antes de "limite" (arranque del día de hoy): quedaron
     * pendientes de que un admin haga el control de cierre. Candidatas del aviso CAJA_ATRASADA. */
    List<Caja> findAllByFechaCierreIsNullAndFechaAperturaBefore(LocalDateTime limite);

    /** Cajas "sin control" (abrirSinControl) que quedaron abiertas: las cierra solas el job
     * programado a fin de día, sin pedir ningún conteo (ver CajaServiceImpl.cerrarSinControl). */
    List<Caja> findAllByFechaCierreIsNullAndControlOmitidoTrue();
}
