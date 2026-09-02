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
    List<Compra> findAllByFechaVisitaBetween(LocalDate desde, LocalDate hasta);

    /** Regalos (fechaVisita null) creados en el rango, por fecha de compra: aportan su recaudación
     * al reporte igual que cualquier venta cobrada, en el mes en que se vendieron. No tienen día
     * de visita, así que no entran en el desglose diario ni en afluencia. */
    @Query("SELECT c FROM Compra c WHERE c.fechaVisita IS NULL AND c.fechaCreacion >= :desde AND c.fechaCreacion < :hasta")
    List<Compra> findRegalosCreadosEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Regalos canjeados (validados) cuyo ingreso cayó en el rango: aportan la afluencia y las
     * personas ingresadas al reporte, por fecha de validación (cuando la persona entró) — su
     * recaudación NO se cuenta acá, ya se contó por fecha de compra en su mes. */
    @Query("SELECT c FROM Compra c WHERE c.fechaVisita IS NULL AND c.estado = :estado " +
            "AND c.fechaValidacion >= :desde AND c.fechaValidacion < :hasta")
    List<Compra> findRegalosValidadosEntre(@Param("estado") EstadoCompra estado,
                                           @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

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


