package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;

import java.time.LocalDate;

public interface ReporteService {
    /** Resumen del reporte para el rango [desde, hasta]. */
    ReporteResumenDTO generarResumen(LocalDate desde, LocalDate hasta);
}
