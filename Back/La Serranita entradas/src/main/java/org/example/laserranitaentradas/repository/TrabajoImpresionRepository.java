package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.EstadoTrabajoImpresion;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrabajoImpresionRepository extends JpaRepository<TrabajoImpresion, Long> {

    List<TrabajoImpresion> findByEstadoInAndImpresoraInOrderByIdAsc(Collection<EstadoTrabajoImpresion> estados, Collection<String> impresoras);

    List<TrabajoImpresion> findByEstadoInAndFechaCreacionBefore(Collection<EstadoTrabajoImpresion> estados, LocalDateTime antes);

    Optional<TrabajoImpresion> findFirstByFacturaIdOrderByIdDesc(Long facturaId);

    boolean existsByFacturaId(Long facturaId);
}
