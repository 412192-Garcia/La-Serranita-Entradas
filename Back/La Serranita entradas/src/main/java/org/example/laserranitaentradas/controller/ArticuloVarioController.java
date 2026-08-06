package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.ArticuloVarioResponseDTO;
import org.example.laserranitaentradas.model.dto.CrearArticuloVarioRequest;
import org.example.laserranitaentradas.model.entity.ArticuloVario;
import org.example.laserranitaentradas.service.ArticuloVarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/articulos-varios")
@Tag(name = "Artículos Varios", description = "Catálogo de artículos (souvenirs, etc.) vendibles en la boletería")
public class ArticuloVarioController {

    private final ArticuloVarioService articuloVarioService;

    public ArticuloVarioController(ArticuloVarioService articuloVarioService) {
        this.articuloVarioService = articuloVarioService;
    }

    @GetMapping
    @Operation(summary = "Obtener todos los artículos varios")
    public ResponseEntity<List<ArticuloVarioResponseDTO>> obtenerTodos() {
        return ResponseEntity.ok(articuloVarioService.getAll().stream()
                .map(ArticuloVarioController::entityToDto)
                .collect(Collectors.toList()));
    }

    @PostMapping
    @Operation(summary = "Crear artículo vario")
    public ResponseEntity<ArticuloVarioResponseDTO> crear(@RequestBody CrearArticuloVarioRequest request) {
        ArticuloVario creado = articuloVarioService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar artículo vario")
    public ResponseEntity<ArticuloVarioResponseDTO> actualizar(@PathVariable Long id, @RequestBody CrearArticuloVarioRequest request) {
        return ResponseEntity.ok(entityToDto(articuloVarioService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar artículo vario")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        articuloVarioService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static ArticuloVarioResponseDTO entityToDto(ArticuloVario a) {
        return ArticuloVarioResponseDTO.builder()
                .id(a.getId())
                .nombre(a.getNombre())
                .precioSugerido(a.getPrecioSugerido())
                .activo(a.getActivo())
                .build();
    }
}
