package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetiroCajaRepository extends JpaRepository<RetiroCaja, Long> {
    List<RetiroCaja> findAllByCajaIdOrderByFechaAsc(Long cajaId);

    Optional<RetiroCaja> findByIdempotencyKey(String idempotencyKey);
}
