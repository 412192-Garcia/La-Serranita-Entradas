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

    /** Ajustes de las cajas abiertas en el rango (con la caja ya cargada), para el reporte. */
    @Query("SELECT a FROM AjusteCaja a JOIN FETCH a.caja c WHERE c.fechaApertura >= :desde AND c.fechaApertura < :hasta")
    List<AjusteCaja> findAllDeCajasAbiertasEntre(@Param("desde") java.time.LocalDateTime desde,
                                                 @Param("hasta") java.time.LocalDateTime hasta);

    /** [idCaja, neto de ajustes] por caja: destino menos origen. */
    @Query("SELECT a.caja.id, " +
            "COALESCE(SUM(CASE WHEN a.formaDestino IN :formas THEN a.monto ELSE 0 END), 0) - " +
            "COALESCE(SUM(CASE WHEN a.formaOrigen IN :formas THEN a.monto ELSE 0 END), 0) " +
            "FROM AjusteCaja a WHERE a.caja.id IN :cajaIds GROUP BY a.caja.id")
    List<Object[]> netoPorCajaYFormas(@Param("cajaIds") List<Long> cajaIds, @Param("formas") Collection<FormaPago> formas);
}
