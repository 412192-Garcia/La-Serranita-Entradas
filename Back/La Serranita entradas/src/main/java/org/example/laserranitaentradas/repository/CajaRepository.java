package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long>, JpaSpecificationExecutor<Caja> {
    Optional<Caja> findByUsuarioIdAndFechaCierreIsNull(Long usuarioId);

    /** Cajas ya cerradas dentro del rango, para el reporte de faltantes/sobrantes por turno — la más reciente primero. */
    List<Caja> findAllByFechaCierreBetweenOrderByFechaCierreDesc(LocalDateTime desde, LocalDateTime hasta);

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<Caja> findAllByFechaCierreIsNullOrderByFechaAperturaAsc();

    /** Faltante total (diferencia negativa, en positivo) de TODAS las cajas que matchean el filtro,
     * no sólo la página actual — para la tarjeta KPI del listado paginado de Cajas cerradas.
     * diferencia ya es una columna persistida (se calcula una sola vez al cerrar), así que esto es
     * una sola query agregada, no un loop por caja. */
    @Query("SELECT COALESCE(SUM(CASE WHEN c.diferencia < 0 THEN -c.diferencia ELSE 0 END), 0) FROM Caja c JOIN c.usuario u " +
            "WHERE c.fechaCierre BETWEEN :desde AND :hasta AND (:usuarioNombre IS NULL OR CONCAT(u.nombre, ' ', u.apellido) = :usuarioNombre)")
    BigDecimal sumFaltantes(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, @Param("usuarioNombre") String usuarioNombre);

    /** Igual que sumFaltantes pero para el sobrante total. */
    @Query("SELECT COALESCE(SUM(CASE WHEN c.diferencia > 0 THEN c.diferencia ELSE 0 END), 0) FROM Caja c JOIN c.usuario u " +
            "WHERE c.fechaCierre BETWEEN :desde AND :hasta AND (:usuarioNombre IS NULL OR CONCAT(u.nombre, ' ', u.apellido) = :usuarioNombre)")
    BigDecimal sumSobrantes(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, @Param("usuarioNombre") String usuarioNombre);
}
