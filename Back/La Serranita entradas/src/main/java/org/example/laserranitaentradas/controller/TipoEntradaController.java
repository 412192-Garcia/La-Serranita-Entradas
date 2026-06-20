package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.service.TipoEntradaService;
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
@RequestMapping("/api/tipos-entrada")
@Tag(name = "Tipos de Entrada", description = "Operaciones para gestionar tipos de entrada")
public class TipoEntradaController {

    private final TipoEntradaService tipoEntradaService;

    public TipoEntradaController(TipoEntradaService tipoEntradaService) {
        this.tipoEntradaService = tipoEntradaService;
    }

    @PostMapping
    @Operation(summary = "Crear tipo de entrada", description = "Crea un nuevo tipo de entrada")
    @ApiResponse(responseCode = "201", description = "Tipo de entrada creado exitosamente")
    public ResponseEntity<TipoEntrada> crearTipoEntrada(@RequestBody TipoEntrada tipoEntrada) {
        TipoEntrada nuevoTipoEntrada = tipoEntradaService.create(tipoEntrada);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoTipoEntrada);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener tipo de entrada por ID", description = "Obtiene un tipo de entrada específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tipo de entrada encontrado"),
            @ApiResponse(responseCode = "404", description = "Tipo de entrada no encontrado")
    })
    public ResponseEntity<TipoEntrada> obtenerTipoEntradaPorId(@PathVariable @Parameter(description = "ID del tipo de entrada") Long id) {
        Optional<TipoEntrada> tipoEntrada = tipoEntradaService.findById(id);
        return tipoEntrada.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/nombre/{nombre}")
    @Operation(summary = "Obtener tipo de entrada por nombre", description = "Obtiene un tipo de entrada específico por su nombre")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tipo de entrada encontrado"),
            @ApiResponse(responseCode = "404", description = "Tipo de entrada no encontrado")
    })
    public ResponseEntity<TipoEntrada> obtenerTipoEntradaPorNombre(@PathVariable @Parameter(description = "Nombre del tipo de entrada") String nombre) {
        Optional<TipoEntrada> tipoEntrada = tipoEntradaService.findByName(nombre);
        return tipoEntrada.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Obtener todos los tipos de entrada", description = "Obtiene la lista de todos los tipos de entrada disponibles")
    @ApiResponse(responseCode = "200", description = "Lista de tipos de entrada obtenida exitosamente")
    public ResponseEntity<List<TipoEntrada>> obtenerTodosTiposEntrada() {
        List<TipoEntrada> tiposEntrada = tipoEntradaService.getAll();
        return ResponseEntity.ok(tiposEntrada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar tipo de entrada", description = "Actualiza los datos de un tipo de entrada existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tipo de entrada actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Tipo de entrada no encontrado")
    })
    public ResponseEntity<TipoEntrada> actualizarTipoEntrada(@PathVariable @Parameter(description = "ID del tipo de entrada") Long id, @RequestBody TipoEntrada tipoEntrada) {
        if (tipoEntradaService.findById(id).isPresent()) {
            tipoEntrada.setId(id);
            TipoEntrada tipoEntradaActualizado = tipoEntradaService.update(tipoEntrada);
            return ResponseEntity.ok(tipoEntradaActualizado);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar tipo de entrada", description = "Borra de manera logica un tipo de entrada del sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tipo de entrada eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Tipo de entrada no encontrado")
    })
    public ResponseEntity<Void> eliminarTipoEntrada(@PathVariable @Parameter(description = "ID del tipo de entrada") Long id) {
        if (tipoEntradaService.findById(id).isPresent()) {
            tipoEntradaService.delete(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
