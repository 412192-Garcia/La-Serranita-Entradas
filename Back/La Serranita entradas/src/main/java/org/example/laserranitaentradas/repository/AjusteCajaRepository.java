package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.AjusteCaja;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AjusteCajaRepository extends JpaRepository<AjusteCaja, Long> {

    List<AjusteCaja> findAllByCajaIdOrderByFechaAsc(Long cajaId);

    Optional<AjusteCaja> findByIdAndCajaId(Long id, Long cajaId);

    /** [idCaja, neto de ajustes hacia esas formas] por caja, en lote: lo que entró (formaDestino) menos lo que salió (formaOrigen). */
    @Query("SELECT a.caja.id, " +
            "COALESCE(SUM(CASE WHEN a.formaDestino IN :formas THEN a.monto ELSE 0 END), 0) - " +
            "COALESCE(SUM(CASE WHEN a.formaOrigen IN :formas THEN a.monto ELSE 0 END), 0) " +
            "FROM AjusteCaja a WHERE a.caja.id IN :cajaIds GROUP BY a.caja.id")
    List<Object[]> netoPorCajaYFormas(@Param("cajaIds") List<Long> cajaIds, @Param("formas") Collection<FormaPago> formas);
}
