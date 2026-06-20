package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/dias-apertura")
@Tag(name = "Días de Apertura", description = "Operaciones para gestionar días de apertura del parque")
public class DiaAperturaController {

    private final DiaAperturaService diaAperturaService;

    public DiaAperturaController(DiaAperturaService diaAperturaService) {
        this.diaAperturaService = diaAperturaService;
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Obtener día de apertura por ID", description = "Obtiene un día de apertura específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Día de apertura encontrado"),
            @ApiResponse(responseCode = "404", description = "Día de apertura no encontrado")
    })
    public ResponseEntity<DiaApertura> obtenerDiaAperturaPorId(@PathVariable @Parameter(description = "ID del día de apertura") Long id) {
        Optional<DiaApertura> diaApertura = diaAperturaService.findById(id);
        return diaApertura.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/fecha/{fecha}")
    @Operation(summary = "Obtener día de apertura por fecha", description = "Obtiene un día de apertura específico por su fecha")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Día de apertura encontrado"),
            @ApiResponse(responseCode = "404", description = "Día de apertura no encontrado")
    })
    public ResponseEntity<DiaApertura> obtenerDiaAperturaPorFecha(@PathVariable @Parameter(description = "Fecha del día de apertura (YYYY-MM-DD)") LocalDate fecha) {
        Optional<DiaApertura> diaApertura = diaAperturaService.findByDate(fecha);
        return diaApertura.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mes")
    @Operation(summary = "Estado de todos los días del mes", description = "Devuelve un objeto por cada día del mes indicando si está abierto o cerrado. Días sin registro se consideran cerrados.")
    @ApiResponse(responseCode = "200", description = "Lista de días de apertura obtenida exitosamente")
    public ResponseEntity<List<DiaApertura>> getMonth(
            @RequestParam @Parameter(description = "Mes (1-12)") int month,
            @RequestParam @Parameter(description = "Año (YYYY)") int year) {
        List<DiaApertura> dias = diaAperturaService.getMonthStatus(year, month);
        return ResponseEntity.ok(dias);
    }

    @PutMapping("/fecha/{fecha}")
    @Operation(summary = "Establecer abierto/cerrado por fecha", description = "Crea o actualiza el registro del día para marcarlo como abierto o cerrado")
    public ResponseEntity<DiaApertura> setAbiertoByDate(@PathVariable @Parameter(description = "Fecha (YYYY-MM-DD)") LocalDate fecha,
                                                        @RequestParam @Parameter(description = "Indicar true para abrir, false para cerrar") boolean abierto) {
        DiaApertura dia = diaAperturaService.setAbiertoByDate(fecha, abierto);
        return ResponseEntity.ok(dia);
    }


}
