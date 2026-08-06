package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.IngresoEntradas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngresoEntradasRepository extends JpaRepository<IngresoEntradas, Long> {
    List<IngresoEntradas> findAllByCajaIdOrderByFechaAsc(Long cajaId);
}
