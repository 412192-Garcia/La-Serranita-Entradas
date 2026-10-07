package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Incidente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {

    /** El mismo error sin resolver (para agruparlo en vez de crear otro). */
    Optional<Incidente> findFirstByFirmaAndResueltoFalseOrderByIdDesc(String firma);

    Page<Incidente> findByResueltoOrderByUltimaVezDesc(boolean resuelto, Pageable pagina);

    Page<Incidente> findByResueltoAndAreaOrderByUltimaVezDesc(boolean resuelto, String area, Pageable pagina);

    Page<Incidente> findAllByOrderByUltimaVezDesc(Pageable pagina);

    Page<Incidente> findByAreaOrderByUltimaVezDesc(String area, Pageable pagina);

    long countByResueltoFalse();

    long countByResueltoFalseAndArea(String area);

    List<Incidente> findTop3ByResueltoFalseOrderByUltimaVezDesc();

    List<Incidente> findTop3ByResueltoFalseAndAreaOrderByUltimaVezDesc(String area);

    /** Se borran los resueltos viejos (los pendientes nunca: alguien los tiene que ver). */
    @Modifying
    @Transactional
    @Query("DELETE FROM Incidente i WHERE i.resuelto = true AND i.resueltoEn < :limite")
    int borrarResueltosAntesDe(@Param("limite") LocalDateTime limite);
}
