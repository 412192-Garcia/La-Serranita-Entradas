package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.CierrePosnet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CierrePosnetRepository extends JpaRepository<CierrePosnet, Long> {
    List<CierrePosnet> findAllByCajaIdOrderByIdAsc(Long cajaId);

    /** Usado al corregir un cierre ya hecho: se borran los cierres viejos y se cargan los nuevos de cero. */
    void deleteAllByCajaId(Long cajaId);

    /** [idCaja, total cerrado en el/los posnet] por caja, para calcular la diferencia de Tarjeta+QR en lote. */
    @Query("SELECT c.caja.id, COALESCE(SUM(c.monto), 0) FROM CierrePosnet c WHERE c.caja.id IN :cajaIds GROUP BY c.caja.id")
    List<Object[]> sumMontoPorCaja(@Param("cajaIds") List<Long> cajaIds);
}
