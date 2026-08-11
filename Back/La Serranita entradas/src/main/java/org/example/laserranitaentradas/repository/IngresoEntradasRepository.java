package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.IngresoEntradas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngresoEntradasRepository extends JpaRepository<IngresoEntradas, Long> {
    List<IngresoEntradas> findAllByCajaIdOrderByFechaAsc(Long cajaId);

    Optional<IngresoEntradas> findByIdempotencyKey(String idempotencyKey);
}
