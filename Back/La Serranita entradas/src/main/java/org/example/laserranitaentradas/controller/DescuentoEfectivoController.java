package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.CrearDescuentoEfectivoRequest;
import org.example.laserranitaentradas.model.dto.DescuentoEfectivoResponseDTO;
import org.example.laserranitaentradas.model.entity.DescuentoEfectivo;
import org.example.laserranitaentradas.service.DescuentoEfectivoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/descuentos-efectivo")
@Tag(name = "Descuentos por Grupo", description = "Precios promocionales por cantidad de pases al pagar en efectivo en boletería")
public class DescuentoEfectivoController {

    private final DescuentoEfectivoService descuentoEfectivoService;

    public DescuentoEfectivoController(DescuentoEfectivoService descuentoEfectivoService) {
        this.descuentoEfectivoService = descuentoEfectivoService;
    }

    @GetMapping
    @Operation(summary = "Obtener todos los precios de grupo", description = "Obtiene la lista de precios promocionales por cantidad de pases")
    public ResponseEntity<List<DescuentoEfectivoResponseDTO>> obtenerTodos() {
        List<DescuentoEfectivoResponseDTO> dtos = descuentoEfectivoService.getAll().stream()
                .map(DescuentoEfectivoController::entityToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Operation(summary = "Crear precio de grupo", description = "Crea un precio promocional para una cantidad puntual de pases de un tipo de entrada")
    public ResponseEntity<DescuentoEfectivoResponseDTO> crear(@RequestBody CrearDescuentoEfectivoRequest request) {
        DescuentoEfectivo creado = descuentoEfectivoService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar precio de grupo", description = "Actualiza un precio promocional existente")
    public ResponseEntity<DescuentoEfectivoResponseDTO> actualizar(@PathVariable @Parameter(description = "ID del precio de grupo") Long id,
                                                                    @RequestBody CrearDescuentoEfectivoRequest request) {
        DescuentoEfectivo actualizado = descuentoEfectivoService.update(id, request);
        return ResponseEntity.ok(entityToDto(actualizado));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar precio de grupo", description = "Elimina un precio promocional por cantidad de pases")
    public ResponseEntity<Void> eliminar(@PathVariable @Parameter(description = "ID del precio de grupo") Long id) {
        descuentoEfectivoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static DescuentoEfectivoResponseDTO entityToDto(DescuentoEfectivo d) {
        DescuentoEfectivoResponseDTO dto = new DescuentoEfectivoResponseDTO();
        dto.setId(d.getId());
        dto.setTipoEntradaId(d.getTipoEntrada().getId());
        dto.setTipoEntradaNombre(d.getTipoEntrada().getNombre());
        dto.setCantidadPases(d.getCantidadPases());
        dto.setPrecioPromocionalTotal(d.getPrecioPromocionalTotal());
        return dto;
    }
}
