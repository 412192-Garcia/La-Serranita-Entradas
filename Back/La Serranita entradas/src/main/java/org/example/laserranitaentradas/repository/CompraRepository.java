package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {
    Optional<Compra> findByClienteDniAndFechaVisita(String dni, LocalDate fechaVisita);

    Optional<Compra> findByIdempotencyKey(String idempotencyKey);

    Optional<Compra> findByCodigoReserva(String codigoReserva);

    /**
     * Aprueba sólo si sigue sin pagar, en una sentencia: webhook y verificación directa pueden llegar a la
     * vez. 0 si otra ya la aprobó. Transaccional porque verificarPagoDirecto llega sin transacción.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Compra c
               SET c.estado = org.example.laserranitaentradas.model.entity.EstadoCompra.APROBADO,
                   c.mpPaymentIds = :mpPaymentIds,
                   c.motivoCancelacion = null,
                   c.detalleCancelacion = null,
                   c.fechaCancelacion = null,
                   c.canceladaPor = null
             WHERE c.id = :id
               AND c.estado IN (org.example.laserranitaentradas.model.entity.EstadoCompra.PENDIENTE_PAGO,
                                org.example.laserranitaentradas.model.entity.EstadoCompra.CANCELADO)
            """)
    int aprobarSiSigueSinPagar(@Param("id") Long id, @Param("mpPaymentIds") String mpPaymentIds);

    /** Por dónde y cuándo se confirmó el pago. Aparte de la aprobación, en la misma transacción. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Compra c SET c.pagoConfirmadoPor = :origen, c.fechaPago = :fecha WHERE c.id = :id")
    int registrarConfirmacionPago(@Param("id") Long id, @Param("origen") String origen, @Param("fecha") LocalDateTime fecha);

    Optional<Compra> findFirstByClienteIdAndIdNotAndFormaPagoAndEstadoInOrderByFechaCreacionDesc(
            Long clienteId, Long compraId, FormaPago formaPago, Collection<EstadoCompra> estados);
    List<Compra> findAllByFechaVisitaOrderByCodigoReservaAsc(LocalDate fechaVisita);
    long countByFechaVisita(LocalDate fechaVisita);
    long countByFechaVisitaIsNull();

    /** Para Estado del sistema: checkouts que siguen sin pagar mucho después de lo normal. */
    long countByEstadoAndFechaCreacionBefore(EstadoCompra estado, LocalDateTime limite);

    // ---------- Sistema > Estado > Ventas (ver AnomaliasVentas) ----------

    long countByFormaPagoAndEstadoInAndFechaCreacionBetween(FormaPago formaPago, Collection<EstadoCompra> estados,
                                                            LocalDateTime desde, LocalDateTime hasta);

    long countByFormaPagoAndFechaCreacionBetween(FormaPago formaPago, LocalDateTime desde, LocalDateTime hasta);

    long countByEstadoAndFechaCreacionBetween(EstadoCompra estado, LocalDateTime desde, LocalDateTime hasta);

    long countByEstadoAndFechaValidacionBetween(EstadoCompra estado, LocalDateTime desde, LocalDateTime hasta);

    /** Anticipadas para un día (ej. las pagadas que todavía no entraron). */
    long countByEstadoAndFechaVisita(EstadoCompra estado, java.time.LocalDate fechaVisita);

    /** Pagos online confirmados desde `desde`, por camino: [pagoConfirmadoPor, cantidad]. */
    @Query("SELECT c.pagoConfirmadoPor, COUNT(c) FROM Compra c WHERE c.fechaPago >= :desde AND c.pagoConfirmadoPor IS NOT NULL GROUP BY c.pagoConfirmadoPor")
    List<Object[]> confirmacionesPorOrigen(@Param("desde") LocalDateTime desde);

    /** La compra más reciente pagada con esa forma (Estado del sistema: "último pago online"). */
    @Query("SELECT MAX(c.fechaCreacion) FROM Compra c WHERE c.formaPago = :formaPago AND c.estado IN :estados")
    LocalDateTime ultimaCompraPagada(@Param("formaPago") FormaPago formaPago, @Param("estados") Collection<EstadoCompra> estados);

    /**
     * Lock de aplicación sobre las compras de una fecha de visita: serializa el número del código de reserva
     * y el cupo diario. Postgres lo libera al terminar la transacción y es re-entrante.
     *
     * El cast a text es para que Hibernate tenga algo que mapear.
     */
    @Query(value = "SELECT cast(pg_advisory_xact_lock(:clave) as text)", nativeQuery = true)
    String bloquearFechaDeVisita(@Param("clave") long clave);


    /**
     * Compras que tocan el rango por fechaCreacion, fechaVisita o fechaValidacion. Prefiltro amplio: el
     * service decide qué métrica va a cada día.
     */
    @Query("SELECT c FROM Compra c WHERE " +
            "(c.fechaVisita >= :desde AND c.fechaVisita <= :hasta) " +
            "OR (c.fechaCreacion >= :desdeDt AND c.fechaCreacion < :hastaDt) " +
            "OR (c.fechaValidacion >= :desdeDt AND c.fechaValidacion < :hastaDt) " +
            "OR (c.fechaPago >= :desdeDt AND c.fechaPago < :hastaDt)")
    List<Compra> findParaReporte(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                 @Param("desdeDt") LocalDateTime desdeDt, @Param("hastaDt") LocalDateTime hastaDt);

    /** Checkouts de MP sin pagar desde `desde`, para reusar. */
    @Query("SELECT c FROM Compra c WHERE c.estado = org.example.laserranitaentradas.model.entity.EstadoCompra.PENDIENTE_PAGO "
            + "AND c.formaPago = org.example.laserranitaentradas.model.entity.FormaPago.MERCADO_PAGO "
            + "AND c.fechaCreacion > :desde")
    List<Compra> findCheckoutsPendientesDesde(@Param("desde") LocalDateTime desde);

    /** Ids de checkouts que quedaron sin pagarse: para expirarlos (ver CompraServiceImpl). */
    @Query("SELECT c.id FROM Compra c WHERE c.estado = :estado AND c.fechaCreacion < :limite")
    List<Long> findIdsByEstadoAndFechaCreacionBefore(@Param("estado") EstadoCompra estado,
                                                     @Param("limite") LocalDateTime limite);

    /** Todo lo cobrado durante ese turno de caja, para calcular el efectivo esperado al cerrar. */
    List<Compra> findAllByCajaId(Long cajaId);

    /** Igual que findAllByCajaId pero con los detalles ya cargados (una consulta, no una por venta). */
    @Query("SELECT DISTINCT c FROM Compra c LEFT JOIN FETCH c.detalles WHERE c.caja.id = :cajaId")
    List<Compra> findAllByCajaIdConDetalles(@Param("cajaId") Long cajaId);

    /**
     * La compra bloqueada hasta el fin de la transacción, para crear su factura sin que otro pedido haga lo
     * mismo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compra c WHERE c.id = :id")
    Optional<Compra> findByIdBloqueando(@Param("id") Long id);

    /**
     * Anticipadas que este boletero validó en su turno: no pasan por caja pero consumen talonario si el tipo
     * entrega entrada.
     */
    @Query("SELECT c FROM Compra c WHERE c.caja IS NULL AND c.usuarioValidador.id = :usuarioId " +
            "AND c.estado = org.example.laserranitaentradas.model.entity.EstadoCompra.USADO " +
            "AND c.fechaValidacion >= :desde AND c.fechaValidacion <= :hasta")
    List<Compra> findAnticipadasValidadasPorUsuarioYFecha(@Param("usuarioId") Long usuarioId,
                                                           @Param("desde") LocalDateTime desde,
                                                           @Param("hasta") LocalDateTime hasta);

    /** [idCaja, total vendido con esas formas de pago, sin canceladas] por caja, en lote. */
    @Query("SELECT c.caja.id, COALESCE(SUM(c.montoTotal), 0) FROM Compra c " +
            "WHERE c.caja.id IN :cajaIds AND c.formaPago IN :formas AND c.estado <> :cancelado GROUP BY c.caja.id")
    List<Object[]> sumMontoPorCajaYFormas(@Param("cajaIds") List<Long> cajaIds,
                                          @Param("formas") Collection<FormaPago> formas,
                                          @Param("cancelado") EstadoCompra cancelado);
}


