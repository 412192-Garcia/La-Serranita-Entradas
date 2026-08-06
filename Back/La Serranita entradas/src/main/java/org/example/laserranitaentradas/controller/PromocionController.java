package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.CrearPromocionRequest;
import org.example.laserranitaentradas.model.dto.PromocionResponseDTO;
import org.example.laserranitaentradas.model.entity.Promocion;
import org.example.laserranitaentradas.service.PromocionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/promociones")
@Tag(name = "Promociones", description = "Descuentos con nombre para la venta en puerta (ej: folletos)")
public class PromocionController {

    private final PromocionService promocionService;

    public PromocionController(PromocionService promocionService) {
        this.promocionService = promocionService;
    }

    @GetMapping
    @Operation(summary = "Obtener todas las promociones")
    public ResponseEntity<List<PromocionResponseDTO>> obtenerTodas() {
        return ResponseEntity.ok(promocionService.getAll().stream()
                .map(PromocionController::entityToDto)
                .collect(Collectors.toList()));
    }

    @PostMapping
    @Operation(summary = "Crear promoción")
    public ResponseEntity<PromocionResponseDTO> crear(@RequestBody CrearPromocionRequest request) {
        Promocion creada = promocionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(creada));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar promoción")
    public ResponseEntity<PromocionResponseDTO> actualizar(@PathVariable Long id, @RequestBody CrearPromocionRequest request) {
        return ResponseEntity.ok(entityToDto(promocionService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar promoción")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        promocionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static PromocionResponseDTO entityToDto(Promocion p) {
        return PromocionResponseDTO.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .porcentajeDescuento(p.getPorcentajeDescuento())
                .montoDescuento(p.getMontoDescuento())
                .activo(p.getActivo())
                .build();
    }
}
