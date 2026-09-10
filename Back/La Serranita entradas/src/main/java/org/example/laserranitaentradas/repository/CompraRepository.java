package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {
    Optional<Compra> findByClienteDniAndFechaVisita(String dni, LocalDate fechaVisita);

    Optional<Compra> findByIdempotencyKey(String idempotencyKey);
    List<Compra> findAllByFechaVisitaOrderByCodigoReservaAsc(LocalDate fechaVisita);
    long countByFechaVisita(LocalDate fechaVisita);
    long countByFechaVisitaIsNull();

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

    /** [idCaja, total vendido con esas formas de pago, sin canceladas] por caja, en lote. */
    @Query("SELECT c.caja.id, COALESCE(SUM(c.montoTotal), 0) FROM Compra c " +
            "WHERE c.caja.id IN :cajaIds AND c.formaPago IN :formas AND c.estado <> :cancelado GROUP BY c.caja.id")
    List<Object[]> sumMontoPorCajaYFormas(@Param("cajaIds") List<Long> cajaIds,
                                          @Param("formas") Collection<FormaPago> formas,
                                          @Param("cancelado") EstadoCompra cancelado);
}


