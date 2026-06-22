package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.service.CompraService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/compras")
@Tag(name = "Compras", description = "Operaciones para gestionar compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }


    @GetMapping("/{id}")
    @Operation(summary = "Obtener compra por ID", description = "Obtiene una compra específica por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Compra encontrada"),
            @ApiResponse(responseCode = "404", description = "Compra no encontrada")
    })
    public ResponseEntity<CompraResponseDTO> obtenerCompraPorId(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Optional<Compra> compra = compraService.findById(id);
        return compra.map(c -> ResponseEntity.ok(entityToDto(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/dni/{dni}")
    @Operation(summary = "Obtener todas las compras por DNI", description = "Obtiene todas las compras de un cliente por su DNI")
    @ApiResponse(responseCode = "200", description = "Lista de compras obtenida exitosamente")
    public ResponseEntity<List<CompraResponseDTO>> obtenerComprasPorDni(@PathVariable @Parameter(description = "DNI del comprador") String dni) {
        List<CompraResponseDTO> compras = compraService.getAllByDni(dni).stream().map(this::entityToDto).collect(Collectors.toList());
        return ResponseEntity.ok(compras);
    }

    @GetMapping
    @Operation(summary = "Obtener todas las compras", description = "Obtiene la lista de todas las compras realizadas")
    @ApiResponse(responseCode = "200", description = "Lista de compras obtenida exitosamente")
    public ResponseEntity<List<CompraResponseDTO>> obtenerTodasCompras() {
        List<CompraResponseDTO> compras = compraService.getAll().stream().map(this::entityToDto).collect(Collectors.toList());
        return ResponseEntity.ok(compras);
    }

    @PostMapping
    @Operation(summary = "Crear compra", description = "Crea una nueva compra con sus detalles")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Compra creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    public ResponseEntity<?> crearCompra(@RequestBody @Parameter(description = "Datos de la compra") CompraRequestDTO compraRequest) {
        try {
            Compra creada = compraService.create(compraRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(creada));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PutMapping("/{id}/validar")
    @Operation(summary = "Validar/Marcar entradas como usadas", description = "Marca la compra como usada y registra el usuario validador")
    public ResponseEntity<?> validarCompra(@PathVariable @Parameter(description = "ID de la compra") Long id,
                                           @RequestBody Long userId) {
        try {
            Compra actualizada = compraService.marcarEntradasComoUsadas(id, userId);
            return ResponseEntity.ok(entityToDto(actualizada));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    private CompraResponseDTO entityToDto(Compra c) {
        CompraResponseDTO dto = new CompraResponseDTO();
        dto.setId(c.getId());
        if (c.getCliente() != null) {
            ClienteResponseDTO cr = new ClienteResponseDTO();
            cr.setId(c.getCliente().getId());
            cr.setDni(c.getCliente().getDni());
            cr.setNombre(c.getCliente().getNombre());
            cr.setApellido(c.getCliente().getApellido());
            dto.setCliente(cr);
        }
        dto.setFechaVisita(c.getFechaVisita());
        dto.setContactEmail(c.getContactEmail());
        dto.setContactPhone(c.getContactPhone());
        dto.setMontoTotal(c.getMontoTotal());
        dto.setDescuentoAplicado(c.getDescuentoAplicado());
        dto.setEstado(c.getEstado() != null ? c.getEstado().name() : null);
        dto.setCuponCodigo(c.getCupon() != null ? c.getCupon().getCodigo() : null);
        if (c.getDetalles() != null) {
            List<CompraDetalleResponseDTO> detalles = c.getDetalles().stream().map(this::detalleEntityToDto).collect(Collectors.toList());
            dto.setDetalles(detalles);
        }
        return dto;
    }

    private CompraDetalleResponseDTO detalleEntityToDto(CompraDetalle d) {
        CompraDetalleResponseDTO dto = new CompraDetalleResponseDTO();
        dto.setId(d.getId());
        if (d.getTipoEntrada() != null) {
            TipoEntradaResponseDTO t = new TipoEntradaResponseDTO();
            t.setId(d.getTipoEntrada().getId());
            t.setNombre(d.getTipoEntrada().getNombre());
            t.setDescripcion(d.getTipoEntrada().getDescripcion());
            t.setPrecio(d.getTipoEntrada().getPrecio());
            dto.setTipoEntrada(t);
        }
        dto.setCantidad(d.getCantidad());
        return dto;
    }

}
