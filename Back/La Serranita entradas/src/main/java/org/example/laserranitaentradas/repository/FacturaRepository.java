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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    /** Las facturas de un lote manual ya pedido (ver Factura.loteClave). */
    List<Factura> findAllByLoteClaveOrderByIdAsc(String loteClave);

    /**
     * Serializa dos pedidos del mismo lote (doble toque, reintento): el segundo espera y encuentra las
     * facturas del primero.
     */
    @Query(value = "SELECT cast(pg_advisory_xact_lock(hashtext(:clave)) as text)", nativeQuery = true)
    String bloquearLote(@Param("clave") String clave);

    /** El último comprobante de ese tipo de la compra (6 = la factura vigente, la más nueva). */
    Optional<Factura> findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(Long compraId, Integer tipoComprobante);

    /** Las últimas facturas manuales (sin venta), de la más nueva a la más vieja. */
    List<Factura> findTop50ByCompraIsNullAndTipoComprobanteInOrderByIdDesc(Collection<Integer> tiposComprobante);

    /**
     * Facturas del mismo punto de venta y tipo con número pedido y sin respuesta: hay que resolverlas antes
     * de que otra pida número.
     */
    @Query("SELECT f.id FROM Factura f WHERE f.puntoVenta = :pv AND f.tipoComprobante = :tipo AND f.id <> :id " +
            "AND f.numeroIntentado IS NOT NULL AND f.numero IS NULL AND f.estado IN :estados ORDER BY f.id")
    List<Long> reservasSinResolver(@Param("pv") Integer puntoVenta, @Param("tipo") Integer tipoComprobante,
                                   @Param("id") Long excepto, @Param("estados") List<EstadoFactura> estados);

    /** Para no adoptar una autorización ajena al recuperar un pedido sin respuesta. */
    boolean existsByPuntoVentaAndTipoComprobanteAndNumeroAndIdNot(Integer puntoVenta, Integer tipoComprobante, Long numero, Long id);

    /** Sólo esa columna: guardar la entidad entera pisaría una cancelación hecha durante el envío. */
    /**
     * Si todavía no se emitió, la factura online pasa a mandarse sola al autorizarse. 0 = ya estaba emitida o
     * anulada: la manda quien llamó.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Factura f SET f.destino = org.example.laserranitaentradas.model.entity.DestinoFactura.MAIL, f.email = :email " +
            "WHERE f.id = :id AND f.estado IN (org.example.laserranitaentradas.model.entity.EstadoFactura.PENDIENTE, " +
            "org.example.laserranitaentradas.model.entity.EstadoFactura.ERROR)")
    int pasarAMailSiNoEmitida(@Param("id") Long id, @Param("email") String email);

    @Modifying
    @Transactional
    @Query("UPDATE Factura f SET f.destino = org.example.laserranitaentradas.model.entity.DestinoFactura.MAIL, f.email = :email WHERE f.id = :id")
    int pasarAMail(@Param("id") Long id, @Param("email") String email);

    /** Se mandó a otro email (el que dio el cliente después): se guardan juntos, recién cuando salió. */
    @Modifying
    @Transactional
    @Query("UPDATE Factura f SET f.email = :email, f.mailEnviadoEn = :momento WHERE f.id = :id")
    int marcarMailEnviadoA(@Param("id") Long id, @Param("email") String email, @Param("momento") LocalDateTime momento);

    /** Filas [tipoComprobante, estado, cantidad, total] de las ventas de una caja. */
    @Query("SELECT f.tipoComprobante, f.estado, COUNT(f), COALESCE(SUM(f.importeTotal), 0) FROM Factura f " +
            "WHERE f.compra.caja.id = :cajaId GROUP BY f.tipoComprobante, f.estado")
    List<Object[]> totalesPorCaja(@Param("cajaId") Long cajaId);

    /** Filas [tipoComprobante, cantidad, total] con CAE emitidas en el rango. */
    @Query("SELECT f.tipoComprobante, COUNT(f), COALESCE(SUM(f.importeTotal), 0) FROM Factura f " +
            "WHERE f.estado = :estado AND f.fechaEmision BETWEEN :desde AND :hasta GROUP BY f.tipoComprobante")
    List<Object[]> totalesEmitidosEntre(@Param("estado") EstadoFactura estado,
                                        @Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta);

    /** El número más alto autorizado de ese punto de venta y tipo (null si todavía no hay ninguno). */
    @Query("SELECT MAX(f.numero) FROM Factura f WHERE f.puntoVenta = :pv AND f.tipoComprobante = :tipo AND f.numero IS NOT NULL")
    Long ultimoNumeroEmitido(@Param("pv") Integer puntoVenta, @Param("tipo") Integer tipoComprobante);

    /** Facturas en ERROR, o PENDIENTES creadas antes de `limite` (trabadas): las que hay que mirar. */
    @Query("SELECT f FROM Factura f LEFT JOIN FETCH f.compra WHERE f.estado = org.example.laserranitaentradas.model.entity.EstadoFactura.ERROR " +
            "OR (f.estado = org.example.laserranitaentradas.model.entity.EstadoFactura.PENDIENTE AND f.fechaCreacion < :limite) ORDER BY f.id DESC")
    List<Factura> conProblemas(@Param("limite") LocalDateTime limite);

    /** Comprobantes autorizados (con número) emitidos en el período, para el listado del contador. */
    @Query("SELECT f FROM Factura f LEFT JOIN FETCH f.compra LEFT JOIN FETCH f.comprobanteAsociado " +
            "WHERE f.numero IS NOT NULL AND f.fechaEmision BETWEEN :desde AND :hasta " +
            "ORDER BY f.fechaEmision, f.puntoVenta, f.tipoComprobante, f.numero")
    List<Factura> emitidasEntre(@Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta);

    /** El número más alto reservado y todavía sin resolver (se pidió el CAE y no se supo la respuesta). */
    @Query("SELECT MAX(f.numeroIntentado) FROM Factura f WHERE f.puntoVenta = :pv AND f.tipoComprobante = :tipo " +
            "AND f.numero IS NULL AND f.numeroIntentado IS NOT NULL")
    Long ultimoNumeroReservadoSinResolver(@Param("pv") Integer puntoVenta, @Param("tipo") Integer tipoComprobante);

    long countByNumeroIsNotNullAndFechaEmisionBetween(java.time.LocalDate desde, java.time.LocalDate hasta);

    @Query("SELECT DISTINCT f.puntoVenta FROM Factura f")
    List<Integer> puntosDeVentaUsados();

    /** Las facturas B de varias compras de una vez (la ventana "Ventas y facturas"). */
    List<Factura> findByCompraIdInAndTipoComprobante(Collection<Long> compraIds, Integer tipoComprobante);

    @Modifying
    @Transactional
    @Query("UPDATE Factura f SET f.mailEnviadoEn = :momento WHERE f.id = :id")
    int marcarMailEnviado(@Param("id") Long id, @Param("momento") LocalDateTime momento);

    /** Pendientes cuyo reintento ya llegó, de la más vieja a la más nueva. */
    @Query("SELECT f.id FROM Factura f WHERE f.estado = :estado " +
            "AND (f.proximoIntento IS NULL OR f.proximoIntento <= :ahora) ORDER BY f.id")
    List<Long> idsListosParaEmitir(@Param("estado") EstadoFactura estado, @Param("ahora") LocalDateTime ahora);
}
