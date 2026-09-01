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

    /** Todo lo cobrado durante ese turno de caja, para calcular el efectivo esperado al cerrar. */
    List<Compra> findAllByCajaId(Long cajaId);

    /** [idCaja, total vendido con esas formas de pago, sin canceladas] por caja, en lote. */
    @Query("SELECT c.caja.id, COALESCE(SUM(c.montoTotal), 0) FROM Compra c " +
            "WHERE c.caja.id IN :cajaIds AND c.formaPago IN :formas AND c.estado <> :cancelado GROUP BY c.caja.id")
    List<Object[]> sumMontoPorCajaYFormas(@Param("cajaIds") List<Long> cajaIds,
                                          @Param("formas") Collection<FormaPago> formas,
                                          @Param("cancelado") EstadoCompra cancelado);
}


