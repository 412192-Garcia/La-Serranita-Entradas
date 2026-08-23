package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RetiroCajaRepository extends JpaRepository<RetiroCaja, Long> {
    List<RetiroCaja> findAllByCajaIdOrderByFechaAsc(Long cajaId);

    Optional<RetiroCaja> findByIdempotencyKey(String idempotencyKey);

    /** Retiros netos (aportes restan) de TODAS las cajas cerradas que matchean el filtro, no sólo
     * la página actual — para la tarjeta KPI del listado paginado de Cajas cerradas. */
    @Query("SELECT COALESCE(SUM(CASE WHEN r.tipo = :aporte THEN -r.monto ELSE r.monto END), 0) FROM RetiroCaja r JOIN r.caja c JOIN c.usuario u " +
            "WHERE c.fechaCierre BETWEEN :desde AND :hasta AND (:usuarioNombre IS NULL OR CONCAT(u.nombre, ' ', u.apellido) = :usuarioNombre)")
    BigDecimal sumRetirosDeCajasCerradas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                          @Param("usuarioNombre") String usuarioNombre, @Param("aporte") TipoMovimientoCaja aporte);
}
