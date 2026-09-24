package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.HorarioRequest;
import org.example.laserranitaentradas.model.dto.HorarioResponseDTO;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/configuracion")
@Tag(name = "Configuración del Parque", description = "Operaciones para gestionar el horario general del parque")
public class ConfiguracionParqueController {

    private final ConfiguracionParqueService configuracionParqueService;
    private final DiaAperturaService diaAperturaService;

    public ConfiguracionParqueController(ConfiguracionParqueService configuracionParqueService,
                                         DiaAperturaService diaAperturaService) {
        this.configuracionParqueService = configuracionParqueService;
        this.diaAperturaService = diaAperturaService;
    }

    @GetMapping("/horario")
    @Operation(summary = "Obtener horario del parque",
            description = "Sin fecha, el horario por defecto del parque. Con fecha, el que rige ese día: su horario especial si tiene uno, "
                    + "si no el general. Endpoint público, sin autenticación.")
    public ResponseEntity<HorarioResponseDTO> obtenerHorario(
            @RequestParam(required = false) @Parameter(description = "Fecha (YYYY-MM-DD), opcional") LocalDate fecha) {
        ConfiguracionParque config = configuracionParqueService.getHorarioGeneral();
        DiaApertura dia = fecha != null ? diaAperturaService.findByDate(fecha).orElse(null) : null;
        // Campo por campo, igual que el límite de compra (DiaAperturaService.getLimiteDeCompra):
        // lo que el día no define lo toma del horario general.
        LocalTime apertura = dia != null && dia.getHoraApertura() != null ? dia.getHoraApertura() : config.getHoraApertura();
        LocalTime cierre = dia != null && dia.getHoraCierre() != null ? dia.getHoraCierre() : config.getHoraCierre();
        boolean especial = !apertura.equals(config.getHoraApertura()) || !cierre.equals(config.getHoraCierre());
        return ResponseEntity.ok(new HorarioResponseDTO(apertura, cierre, config.getMinutosLimiteCompra(), especial));
    }

    @PutMapping("/horario")
    @Operation(summary = "Actualizar horario general", description = "Actualiza el horario por defecto del parque y, opcionalmente, cuántos minutos antes del cierre se corta la compra online del día (minutosLimiteCompra, 0 a 720; null lo deja como estaba)")
    public ResponseEntity<ConfiguracionParque> actualizarHorarioGeneral(@RequestBody HorarioRequest request) {
        ConfiguracionParque actualizado = configuracionParqueService.actualizarHorarioGeneral(
                request.getHoraApertura(), request.getHoraCierre(), request.getMinutosLimiteCompra());
        return ResponseEntity.ok(actualizado);
    }
}
