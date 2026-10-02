package org.example.laserranitaentradas.repository;

import jakarta.persistence.LockModeType;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    /** El último comprobante de ese tipo de la compra (6 = la factura vigente, la más nueva). */
    Optional<Factura> findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(Long compraId, Integer tipoComprobante);

    /**
     * La factura con su fila bloqueada hasta el fin de la transacción (SELECT ... FOR UPDATE).
     * Todo cambio de estado pasa por acá: así la cancelación de una venta y la emisión contra ARCA
     * (que corren en hilos distintos) no se pisan. Sin esto, cancelar justo mientras se pedía el
     * número podía terminar autorizando la factura de una venta cancelada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Factura f WHERE f.id = :id")
    Optional<Factura> bloquear(@Param("id") Long id);

    /** ¿Ese número ya lo tiene otra factura de acá? Para no adoptar una autorización ajena al
     * recuperar un pedido que quedó sin respuesta. */
    boolean existsByPuntoVentaAndTipoComprobanteAndNumeroAndIdNot(Integer puntoVenta, Integer tipoComprobante, Long numero, Long id);

    /** Sólo esa columna: el envío del mail tarda y, mientras, la venta se puede cancelar. Guardar
     * la entidad leída antes del envío pisaría esa cancelación. */
    @Modifying
    @Transactional
    @Query("UPDATE Factura f SET f.mailEnviadoEn = :momento WHERE f.id = :id")
    int marcarMailEnviado(@Param("id") Long id, @Param("momento") LocalDateTime momento);

    /** Pendientes cuyo próximo intento ya llegó, de la más vieja a la más nueva (así se respeta
     * el orden de venta en la numeración, dentro de lo posible). */
    @Query("SELECT f.id FROM Factura f WHERE f.estado = :estado " +
            "AND (f.proximoIntento IS NULL OR f.proximoIntento <= :ahora) ORDER BY f.id")
    List<Long> idsListosParaEmitir(@Param("estado") EstadoFactura estado, @Param("ahora") LocalDateTime ahora);
}
