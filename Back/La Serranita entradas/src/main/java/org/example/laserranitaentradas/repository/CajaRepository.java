package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long>, JpaSpecificationExecutor<Caja> {
    Optional<Caja> findByUsuarioIdAndFechaCierreIsNull(Long usuarioId);

    /** Cajas ya cerradas dentro del rango, para el reporte de faltantes/sobrantes por turno — la más reciente primero.
     * Incluye las deshabilitadas: el reporte las filtra en memoria (ver ReporteServiceImpl) para no cambiar este OrderBy. */
    List<Caja> findAllByFechaCierreBetweenOrderByFechaCierreDesc(LocalDateTime desde, LocalDateTime hasta);

    /** Ids de las cajas deshabilitadas por un admin: para descartar sus ventas del reporte. */
    @Query("SELECT c.id FROM Caja c WHERE c.habilitada = false")
    List<Long> findIdsDeshabilitadas();

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<Caja> findAllByFechaCierreIsNullOrderByFechaAperturaAsc();
}
