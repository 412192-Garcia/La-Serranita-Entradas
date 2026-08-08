package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long> {
    Optional<Caja> findByUsuarioIdAndFechaCierreIsNull(Long usuarioId);

    /** Cajas ya cerradas dentro del rango, para el reporte de faltantes/sobrantes por turno. */
    List<Caja> findAllByFechaCierreBetweenOrderByFechaCierreAsc(LocalDateTime desde, LocalDateTime hasta);

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<Caja> findAllByFechaCierreIsNullOrderByFechaAperturaAsc();
}
