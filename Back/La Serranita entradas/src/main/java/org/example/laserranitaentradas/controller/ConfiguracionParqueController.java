package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.HorarioRequest;
import org.example.laserranitaentradas.model.dto.HorarioResponseDTO;
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
    @Operation(summary = "Obtener horario general", description = "Devuelve el horario por defecto del parque (rige salvo horario especial por día) — endpoint público, sin autenticación")
    public ResponseEntity<HorarioResponseDTO> obtenerHorarioGeneral() {
        ConfiguracionParque config = configuracionParqueService.getHorarioGeneral();
        return ResponseEntity.ok(new HorarioResponseDTO(config.getHoraApertura(), config.getHoraCierre(), config.getMinutosLimiteCompra()));
    }

    @PutMapping("/horario")
    @Operation(summary = "Actualizar horario general", description = "Actualiza el horario por defecto del parque y, opcionalmente, cuántos minutos antes del cierre se corta la compra online del día (minutosLimiteCompra, 0 a 720; null lo deja como estaba)")
    public ResponseEntity<ConfiguracionParque> actualizarHorarioGeneral(@RequestBody HorarioRequest request) {
        ConfiguracionParque actualizado = configuracionParqueService.actualizarHorarioGeneral(
                request.getHoraApertura(), request.getHoraCierre(), request.getMinutosLimiteCompra());
        return ResponseEntity.ok(actualizado);
    }
}
