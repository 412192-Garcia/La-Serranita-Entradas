package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.OperacionRechazada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperacionRechazadaRepository extends JpaRepository<OperacionRechazada, Long> {
    List<OperacionRechazada> findAllByOrderByFechaCreacionDesc();

    List<OperacionRechazada> findAllByResueltoOrderByFechaCreacionDesc(boolean resuelto);
}
