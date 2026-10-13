package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.CobroPoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CobroPointRepository extends JpaRepository<CobroPoint, Long> {
    Optional<CobroPoint> findByClaveIdempotencia(String claveIdempotencia);

    Optional<CobroPoint> findByOrdenId(String ordenId);

    /** Con la caja, su dueño y la venta ya cargados: el service trabaja sin transacción abierta. */
    @EntityGraph(attributePaths = {"caja", "caja.usuario", "compra"})
    @Query("SELECT c FROM CobroPoint c WHERE c.id = :id")
    Optional<CobroPoint> findConCajaById(@Param("id") Long id);

    List<CobroPoint> findAllByCompraId(Long compraId);

    /** Lo que cobró el Point en esta caja: aprobados de ventas que siguen en pie, por forma de pago. */
    @Query("SELECT c.formaPago, COALESCE(SUM(c.monto), 0), COUNT(c) FROM CobroPoint c WHERE c.caja.id = :cajaId "
            + "AND c.estado = org.example.laserranitaentradas.model.entity.EstadoCobroPoint.APROBADO "
            + "AND c.compra IS NOT NULL AND c.compra.estado <> org.example.laserranitaentradas.model.entity.EstadoCompra.CANCELADO "
            + "GROUP BY c.formaPago")
    List<Object[]> totalesAprobadosPorForma(@Param("cajaId") Long cajaId);

    /** Aprobados que nunca terminaron en venta: plata cobrada sin venta registrada. */
    @Query("SELECT c FROM CobroPoint c WHERE c.caja.id = :cajaId AND c.compra IS NULL "
            + "AND c.estado = org.example.laserranitaentradas.model.entity.EstadoCobroPoint.APROBADO ORDER BY c.id")
    List<CobroPoint> aprobadosSinVenta(@Param("cajaId") Long cajaId);
}
