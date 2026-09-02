package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RetiroCajaRepository extends JpaRepository<RetiroCaja, Long> {
    List<RetiroCaja> findAllByCajaIdOrderByFechaAsc(Long cajaId);

    Optional<RetiroCaja> findByIdempotencyKey(String idempotencyKey);

    /** [idCaja, neto de retiros] por caja, en lote: los RETIRO suman y los APORTE restan (un
     * aporte es, en la fórmula del esperado, un retiro negativo). Evita un query por caja al
     * armar el reporte. */
    @Query("SELECT r.caja.id, COALESCE(SUM(CASE WHEN r.tipo = :aporte THEN -r.monto ELSE r.monto END), 0) " +
            "FROM RetiroCaja r WHERE r.caja.id IN :cajaIds GROUP BY r.caja.id")
    List<Object[]> netoRetirosPorCaja(@Param("cajaIds") Collection<Long> cajaIds,
                                      @Param("aporte") TipoMovimientoCaja aporte);
}
