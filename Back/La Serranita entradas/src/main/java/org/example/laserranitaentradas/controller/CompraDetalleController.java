package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.service.CompraDetalleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/compras-detalles")
@Tag(name = "Detalles de Compra", description = "Operaciones para gestionar detalles de compra")
public class CompraDetalleController {

    private final CompraDetalleService compraDetalleService;

    public CompraDetalleController(CompraDetalleService compraDetalleService) {
        this.compraDetalleService = compraDetalleService;
    }

    @PostMapping
    @Operation(summary = "Crear detalle de compra", description = "Crea un nuevo detalle de compra")
    @ApiResponse(responseCode = "201", description = "Detalle de compra creado exitosamente")
    public ResponseEntity<CompraDetalle> crearCompraDetalle(@RequestBody CompraDetalle compraDetalle) {
        CompraDetalle nuevoCompraDetalle = compraDetalleService.crearCompraDetalle(compraDetalle);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoCompraDetalle);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de compra por ID", description = "Obtiene un detalle de compra específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detalle de compra encontrado"),
            @ApiResponse(responseCode = "404", description = "Detalle de compra no encontrado")
    })
    public ResponseEntity<CompraDetalle> obtenerCompraDetallePorId(@PathVariable @Parameter(description = "ID del detalle de compra") Long id) {
        Optional<CompraDetalle> compraDetalle = compraDetalleService.obtenerCompraDetallePorId(id);
        return compraDetalle.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/compra/{compraId}")
    @Operation(summary = "Obtener detalles por compra", description = "Obtiene todos los detalles de una compra específica")
    @ApiResponse(responseCode = "200", description = "Lista de detalles obtenida exitosamente")
    public ResponseEntity<List<CompraDetalle>> obtenerDetallesPorCompra(@PathVariable @Parameter(description = "ID de la compra") Long compraId) {
        Compra compra = new Compra();
        compra.setId(compraId);
        List<CompraDetalle> detalles = compraDetalleService.obtenerDetallesPorCompra(compra);
        return ResponseEntity.ok(detalles);
    }

    @GetMapping
    @Operation(summary = "Obtener todos los detalles de compra", description = "Obtiene la lista de todos los detalles de compra")
    @ApiResponse(responseCode = "200", description = "Lista de detalles obtenida exitosamente")
    public ResponseEntity<List<CompraDetalle>> obtenerTodosCompraDetalles() {
        List<CompraDetalle> compraDetalles = compraDetalleService.obtenerTodosCompraDetalles();
        return ResponseEntity.ok(compraDetalles);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar detalle de compra", description = "Actualiza los datos de un detalle de compra existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detalle de compra actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Detalle de compra no encontrado")
    })
    public ResponseEntity<CompraDetalle> actualizarCompraDetalle(@PathVariable @Parameter(description = "ID del detalle de compra") Long id, @RequestBody CompraDetalle compraDetalle) {
        if (compraDetalleService.obtenerCompraDetallePorId(id).isPresent()) {
            compraDetalle.setId(id);
            CompraDetalle compraDetalleActualizado = compraDetalleService.actualizarCompraDetalle(compraDetalle);
            return ResponseEntity.ok(compraDetalleActualizado);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar detalle de compra", description = "Elimina un detalle de compra del sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Detalle de compra eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Detalle de compra no encontrado")
    })
    public ResponseEntity<Void> eliminarCompraDetalle(@PathVariable @Parameter(description = "ID del detalle de compra") Long id) {
        if (compraDetalleService.obtenerCompraDetallePorId(id).isPresent()) {
            compraDetalleService.eliminarCompraDetalle(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

