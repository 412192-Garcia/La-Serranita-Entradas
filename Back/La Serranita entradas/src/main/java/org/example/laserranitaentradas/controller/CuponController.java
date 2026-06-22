package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.CrearCuponRequest;
import org.example.laserranitaentradas.model.dto.CrearFamiliaCuponRequest;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.FamiliaCupon;
import org.example.laserranitaentradas.service.CuponService;
import org.example.laserranitaentradas.service.FamiliaCuponService;
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
@RequestMapping("/api/cupones")
@Tag(name = "Cupones", description = "Operaciones para gestionar cupones de descuento")
public class CuponController {

    private final CuponService cuponService;
    private final FamiliaCuponService familiaService;

    public CuponController(CuponService cuponService, FamiliaCuponService familiaService) {
        this.cuponService = cuponService;
        this.familiaService = familiaService;
    }

    @PostMapping
    @Operation(summary = "Crear cupón", description = "Crea un nuevo cupón de descuento (individual)")
    @ApiResponse(responseCode = "201", description = "Cupón creado exitosamente")
    public ResponseEntity<Cupon> crearCupon(@RequestBody CrearCuponRequest request) {
        Cupon nuevoCupon = cuponService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoCupon);
    }

    @PostMapping("/familias/generar")
    @Operation(summary = "Generar cupones en una familia", description = "Genera N cupones con un prefijo y los agrupa en una familia de cupones")
    public ResponseEntity<FamiliaCupon> generarFamilia(@RequestBody CrearFamiliaCuponRequest request) {
        FamiliaCupon guardada = familiaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cupón por ID", description = "Obtiene un cupón específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cupón encontrado"),
            @ApiResponse(responseCode = "404", description = "Cupón no encontrado")
    })
    public ResponseEntity<Cupon> obtenerCuponPorId(@PathVariable @Parameter(description = "ID del cupón") Long id) {
        Optional<Cupon> cupon = cuponService.getById(id);
        return cupon.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Obtener cupón por código", description = "Obtiene un cupón específico por su código")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cupón encontrado"),
            @ApiResponse(responseCode = "404", description = "Cupón no encontrado")
    })
    public ResponseEntity<Cupon> obtenerCuponPorCodigo(@PathVariable @Parameter(description = "Código del cupón") String codigo) {
        Optional<Cupon> cupon = cuponService.getByCode(codigo);
        return cupon.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Obtener todos los cupones", description = "Obtiene la lista de todos los cupones")
    @ApiResponse(responseCode = "200", description = "Lista de cupones obtenida exitosamente")
    public ResponseEntity<List<Cupon>> obtenerTodosCupones() {
        List<Cupon> cupones = cuponService.getAll();
        return ResponseEntity.ok(cupones);
    }

    @GetMapping("/activos")
    @Operation(summary = "Obtener cupones activos", description = "Obtiene la lista de cupones que están activos y no han expirado")
    @ApiResponse(responseCode = "200", description = "Lista de cupones activos obtenida exitosamente")
    public ResponseEntity<List<Cupon>> obtenerCuponesActivos() {
        List<Cupon> cupones = cuponService.getAllActive();
        return ResponseEntity.ok(cupones);
    }




}
