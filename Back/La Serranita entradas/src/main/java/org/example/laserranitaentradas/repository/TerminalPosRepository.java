package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.TerminalPos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TerminalPosRepository extends JpaRepository<TerminalPos, String> {

    /** Las que dieron señales desde `desde`, o que quedaron con algo sin subir (aunque sean viejas). */
    @Query("SELECT t FROM TerminalPos t WHERE t.ultimaVez >= :desde OR t.pendientes > 0 OR t.conError > 0 ORDER BY t.ultimaVez DESC")
    List<TerminalPos> recientesOConPendientes(@Param("desde") LocalDateTime desde);

    @Modifying
    @Transactional
    @Query("DELETE FROM TerminalPos t WHERE t.ultimaVez < :limite AND t.pendientes = 0 AND t.conError = 0")
    int borrarSinPendientesAntesDe(@Param("limite") LocalDateTime limite);

    @Modifying
    @Transactional
    @Query("DELETE FROM TerminalPos t WHERE t.ultimaVez < :limite")
    int borrarAntesDe(@Param("limite") LocalDateTime limite);
}
