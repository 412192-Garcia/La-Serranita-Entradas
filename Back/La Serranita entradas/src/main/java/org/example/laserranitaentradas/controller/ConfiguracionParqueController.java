package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.HorarioRequest;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/configuracion")
@Tag(name = "Configuración del Parque", description = "Operaciones para gestionar el horario general del parque")
public class ConfiguracionParqueController {

    private final ConfiguracionParqueService configuracionParqueService;

    public ConfiguracionParqueController(ConfiguracionParqueService configuracionParqueService) {
        this.configuracionParqueService = configuracionParqueService;
    }

    @GetMapping("/horario")
    @Operation(summary = "Obtener horario general", description = "Devuelve el horario por defecto del parque (rige salvo horario especial por día)")
    public ResponseEntity<ConfiguracionParque> obtenerHorarioGeneral() {
        return ResponseEntity.ok(configuracionParqueService.getHorarioGeneral());
    }

    @PutMapping("/horario")
    @Operation(summary = "Actualizar horario general", description = "Actualiza el horario por defecto del parque")
    public ResponseEntity<ConfiguracionParque> actualizarHorarioGeneral(@RequestBody HorarioRequest request) {
        ConfiguracionParque actualizado = configuracionParqueService.actualizarHorarioGeneral(
                request.getHoraApertura(), request.getHoraCierre());
        return ResponseEntity.ok(actualizado);
    }
}
