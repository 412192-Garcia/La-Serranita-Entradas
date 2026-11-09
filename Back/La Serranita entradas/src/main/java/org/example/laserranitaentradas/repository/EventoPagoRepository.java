package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.EventoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EventoPagoRepository extends JpaRepository<EventoPago, Long> {
    long countByCompraId(Long compraId);

    List<EventoPago> findByCompraIdInOrderByFechaAsc(Collection<Long> compraIds);
}
