package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaAperturaRepository extends JpaRepository<DiaApertura, Long> {
    Optional<DiaApertura> findByFecha(LocalDate fecha);

    List<DiaApertura> getAllByFechaBetweenAndAbierto(LocalDate fechaAfter, LocalDate fechaBefore, Boolean abierto);
}

