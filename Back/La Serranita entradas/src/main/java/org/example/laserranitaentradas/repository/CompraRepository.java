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
     * Aprueba la compra sólo si sigue sin pagar, en una sola sentencia: el webhook y las
     * verificaciones directas pueden llegar a la vez, y leer el estado y después guardarlo
     * dejaba pasar a las dos (dos mails, dos usos del cupón). Devuelve 0 si otra ya la aprobó.
     * Transaccional acá porque verificarPagoDirecto la alcanza sin transacción propia.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Compra c
               SET c.estado = org.example.laserranitaentradas.model.entity.EstadoCompra.APROBADO,
                   c.mpPaymentIds = :mpPaymentIds
             WHERE c.id = :id
               AND c.estado IN (org.example.laserranitaentradas.model.entity.EstadoCompra.PENDIENTE_PAGO,
                                org.example.laserranitaentradas.model.entity.EstadoCompra.CANCELADO)
            """)
    int aprobarSiSigueSinPagar(@Param("id") Long id, @Param("mpPaymentIds") String mpPaymentIds);

    /** Por dónde y cuándo se confirmó el pago (ver ConfirmacionPago). Aparte de la aprobación para
     * no tocar esa sentencia; corre en la misma transacción. */
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

    /** Pagos online confirmados desde `desde`, por camino: [pagoConfirmadoPor, cantidad]. */
    @Query("SELECT c.pagoConfirmadoPor, COUNT(c) FROM Compra c WHERE c.fechaPago >= :desde AND c.pagoConfirmadoPor IS NOT NULL GROUP BY c.pagoConfirmadoPor")
    List<Object[]> confirmacionesPorOrigen(@Param("desde") LocalDateTime desde);

    /** La compra más reciente pagada con esa forma (Estado del sistema: "último pago online"). */
    @Query("SELECT MAX(c.fechaCreacion) FROM Compra c WHERE c.formaPago = :formaPago AND c.estado IN :estados")
    LocalDateTime ultimaCompraPagada(@Param("formaPago") FormaPago formaPago, @Param("estados") Collection<EstadoCompra> estados);

    /**
     * Lock de aplicación sobre "las compras de esta fecha de visita". Serializa las dos cosas
     * que hay que mirar-y-después-escribir al crear una compra: el número del código de
     * reserva y el cupo diario. Sin él, dos compras simultáneas leen lo mismo y las dos pasan.
     *
     * No bloquea ninguna fila: es un lock por clave numérica que Postgres libera solo al
     * terminar la transacción, pase lo que pase con ella. Es re-entrante, así que pedirlo dos
     * veces en la misma transacción (una por cada chequeo) no cuesta nada.
     *
     * El cast a text es sólo para que Hibernate tenga algo que mapear: pg_advisory_xact_lock
     * devuelve void y el valor no se usa.
     */
    @Query(value = "SELECT cast(pg_advisory_xact_lock(:clave) as text)", nativeQuery = true)
    String bloquearFechaDeVisita(@Param("clave") long clave);


    /**
     * Todas las compras que "tocan" el rango del reporte por alguna de sus tres fechas: día de
     * compra (fechaCreacion), día de visita elegido (fechaVisita) o día de ingreso real
     * (fechaValidacion). El reporte después decide, por compra, qué métrica va a qué día:
     * recaudación al día de cobro, afluencia/personas al día de validación, demanda al día de
     * visita (ver ReporteServiceImpl). Es un prefiltro amplio; el filtrado fino exacto lo hace
     * el service comparando LocalDate.
     */
    @Query("SELECT c FROM Compra c WHERE " +
            "(c.fechaVisita >= :desde AND c.fechaVisita <= :hasta) " +
            "OR (c.fechaCreacion >= :desdeDt AND c.fechaCreacion < :hastaDt) " +
            "OR (c.fechaValidacion >= :desdeDt AND c.fechaValidacion < :hastaDt)")
    List<Compra> findParaReporte(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                 @Param("desdeDt") LocalDateTime desdeDt, @Param("hastaDt") LocalDateTime hastaDt);

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
     * La compra con su fila bloqueada (SELECT ... FOR UPDATE) hasta el fin de la transacción: para
     * mirar-y-después-crear su factura sin que otro pedido simultáneo haga lo mismo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compra c WHERE c.id = :id")
    Optional<Compra> findByIdBloqueando(@Param("id") Long id);

    /**
     * Anticipadas (compradas online, nunca pasan por caja — ver el comentario de Compra.caja)
     * que este boletero validó durante su turno. Aunque no cobran nada acá, si el tipo tiene
     * entregaEntrada se les entrega igual el talonario físico, así que hay que sumarlas a lo
     * entregado por esta caja (ver calcularEntradasEntregadasAnticipadas en CajaServiceImpl).
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


