package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.CorreccionVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CorreccionVentaRepository extends JpaRepository<CorreccionVenta, Long> {

    @Query("SELECT c FROM CorreccionVenta c JOIN FETCH c.compra WHERE c.caja.id = :cajaId ORDER BY c.fecha ASC")
    List<CorreccionVenta> findAllByCajaIdConCompra(@Param("cajaId") Long cajaId);

    @Query("SELECT c FROM CorreccionVenta c JOIN FETCH c.compra WHERE c.compra.id IN :compraIds ORDER BY c.fecha ASC")
    List<CorreccionVenta> findAllByCompraIdInConCompra(@Param("compraIds") Collection<Long> compraIds);
}
