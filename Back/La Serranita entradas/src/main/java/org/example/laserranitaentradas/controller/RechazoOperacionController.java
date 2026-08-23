package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.OperacionRechazadaResponseDTO;
import org.example.laserranitaentradas.model.dto.ResolverRechazoRequestDTO;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/** ADMIN-only (gateado en SecurityConfig): operaciones del POS que el servidor rechazó, para
 * que cualquier admin las revise sin depender de mirar el navegador del boletero que las hizo. */
@RestController
@RequestMapping("/api/interno/rechazos")
@Tag(name = "Rechazos", description = "Operaciones del POS rechazadas por el servidor, para revisión de un admin")
public class RechazoOperacionController {

    private final RechazoOperacionService rechazoService;

    public RechazoOperacionController(RechazoOperacionService rechazoService) {
        this.rechazoService = rechazoService;
    }

    @GetMapping
    @Operation(summary = "Listar operaciones rechazadas", description = "resuelto=true/false filtra; sin el parámetro trae todas")
    public ResponseEntity<List<OperacionRechazadaResponseDTO>> listar(@RequestParam(required = false) Boolean resuelto) {
        return ResponseEntity.ok(rechazoService.listar(resuelto));
    }

    @PutMapping("/{id}/resolver")
    @Operation(summary = "Marcar un rechazo como resuelto", description = "Con una nota opcional de qué se hizo al respecto")
    public ResponseEntity<OperacionRechazadaResponseDTO> resolver(@PathVariable Long id, @RequestBody ResolverRechazoRequestDTO request) {
        return ResponseEntity.ok(rechazoService.resolver(id, request.getNota()));
    }

    @PostMapping("/{id}/reabrir-y-reintentar")
    @Operation(summary = "Reabrir la caja de origen y reintentar un rechazo",
            description = "Sólo para RETIRO_APORTE e INGRESO_ENTRADAS rechazados porque la caja ya no estaba abierta: la reabre, aplica el movimiento, y la vuelve a cerrar con el mismo conteo que ya tenía.")
    public ResponseEntity<OperacionRechazadaResponseDTO> reabrirYReintentar(@PathVariable Long id) {
        return ResponseEntity.ok(rechazoService.reabrirYReintentar(id));
    }

    @PostMapping("/reabrir-y-reintentar-lote")
    @Operation(summary = "Reabrir la caja de origen y reintentar varios rechazos a la vez",
            description = "Igual que /{id}/reabrir-y-reintentar, pero para varios rechazos de la MISMA caja en un solo ciclo de reabrir/cerrar (ej. se encolaron 5 operaciones sin conexión y la caja se cerró antes de que se reintentaran).")
    public ResponseEntity<List<OperacionRechazadaResponseDTO>> reabrirYReintentarLote(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(rechazoService.reabrirYReintentarLote(ids));
    }
}
