package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.monitoreo.AlertasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Para el healthcheck de Docker y el monitor externo. A diferencia de /api/ping, prueba la base. Sólo OK
 * (200) o SIN_BASE (503).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Health Check", description = "Operaciones para verificar el estado de la API")
public class SaludController {

    private final AlertasService alertasService;

    public SaludController(AlertasService alertasService) {
        this.alertasService = alertasService;
    }

    @GetMapping("/salud")
    @Operation(summary = "Salud del backend y la base", description = "200 si el backend y la base responden; 503 si la base no.")
    public ResponseEntity<Map<String, String>> salud() {
        return alertasService.baseResponde()
                ? ResponseEntity.ok(Map.of("estado", "OK"))
                : ResponseEntity.status(503).body(Map.of("estado", "SIN_BASE"));
    }
}
