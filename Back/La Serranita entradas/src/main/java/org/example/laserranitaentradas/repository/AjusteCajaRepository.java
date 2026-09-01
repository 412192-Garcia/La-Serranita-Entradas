package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.AjusteCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AjusteCajaRepository extends JpaRepository<AjusteCaja, Long> {

    List<AjusteCaja> findAllByCajaIdOrderByFechaAsc(Long cajaId);

    Optional<AjusteCaja> findByIdAndCajaId(Long id, Long cajaId);
}
