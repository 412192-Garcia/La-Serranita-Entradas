package org.example.laserranitaentradas.repository;

import jakarta.persistence.LockModeType;
import org.example.laserranitaentradas.model.entity.CobroPoint;
import org.example.laserranitaentradas.model.entity.EstadoCobroPoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /** Bloqueado, para que dos ventas no vinculen el mismo cobro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"caja", "compra"})
    @Query("SELECT c FROM CobroPoint c WHERE c.id = :id")
    Optional<CobroPoint> findParaVincular(@Param("id") Long id);

    /** La venta a la que está atado ahora, leída de la base (no de la copia en memoria). */
    @Query("SELECT c.compra.id FROM CobroPoint c WHERE c.id = :id")
    Optional<Long> compraIdActual(@Param("id") Long id);

    /** Guarda la devolución en su propia transacción: ya pasó y no se deshace. No toca la venta. */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE CobroPoint c SET c.estado = :estado, c.detalle = :detalle, c.ultimaConsultaMp = :ahora, "
            + "c.version = c.version + 1 WHERE c.id = :id")
    int asentarDevolucion(@Param("id") Long id, @Param("estado") EstadoCobroPoint estado,
                          @Param("detalle") String detalle, @Param("ahora") LocalDateTime ahora);

    /** Un cobro que se creó sin que MP confirmara la orden, y la orden aparece después (webhook). */
    Optional<CobroPoint> findByIdAndOrdenIdIsNull(Long id);

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
