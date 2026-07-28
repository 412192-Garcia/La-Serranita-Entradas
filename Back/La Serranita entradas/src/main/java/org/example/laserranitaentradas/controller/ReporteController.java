package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;
import org.example.laserranitaentradas.service.ReporteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes", description = "Métricas de recaudación, afluencia y ventas por tipo de entrada (solo ADMIN)")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen de reportes para un rango de fechas",
            description = "Recaudación total, afluencia diaria (vendidas vs. validadas por DNI) y desglose por tipo de entrada, filtrado por fecha de visita")
    public ResponseEntity<ReporteResumenDTO> resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(description = "Fecha de visita desde (YYYY-MM-DD, inclusive)") LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(description = "Fecha de visita hasta (YYYY-MM-DD, inclusive)") LocalDate hasta) {
        return ResponseEntity.ok(reporteService.generarResumen(desde, hasta));
    }
}
