package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Otras facturas del mismo punto de venta y tipo que pidieron un número a ARCA y todavía no
     * saben si quedó autorizado. Antes de que otra pida número hay que resolverlas: si no, las dos
     * pueden terminar con el mismo número y una quedarse con la autorización de la otra.
     */
    @Query("SELECT f.id FROM Factura f WHERE f.puntoVenta = :pv AND f.tipoComprobante = :tipo AND f.id <> :id " +
            "AND f.numeroIntentado IS NOT NULL AND f.numero IS NULL AND f.estado IN :estados ORDER BY f.id")
    List<Long> reservasSinResolver(@Param("pv") Integer puntoVenta, @Param("tipo") Integer tipoComprobante,
                                   @Param("id") Long excepto, @Param("estados") List<EstadoFactura> estados);

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
