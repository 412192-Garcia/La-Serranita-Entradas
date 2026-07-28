package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;

import java.time.LocalDate;

public interface ReporteService {
    /** Recaudación, afluencia diaria y desglose por tipo de entrada para el rango [desde, hasta] de fechaVisita. */
    ReporteResumenDTO generarResumen(LocalDate desde, LocalDate hasta);
}
