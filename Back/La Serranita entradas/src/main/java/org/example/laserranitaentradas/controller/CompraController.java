package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.service.CompraService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    @PostMapping("/iniciar-pago")
    @Operation(summary = "Iniciar compra con pago online", description = "Crea la compra en estado PENDIENTE y genera la preferencia de Mercado Pago")
    public ResponseEntity<CompraResponseDTO> iniciarPago(@RequestBody @Parameter(description = "Datos de la compra") CompraRequestDTO compraRequest) throws Exception {
        CompraResponseDTO response = compraService.iniciarCompraConPago(compraRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cotizar")
    @Operation(summary = "Cotizar precio", description = "Calcula el subtotal aplicando descuentos por grupo/forma de pago, sin persistir nada")
    public ResponseEntity<CotizacionResponseDTO> cotizar(@RequestBody @Parameter(description = "Entradas y forma de pago a cotizar") CotizacionRequestDTO cotizacionRequest) {
        return ResponseEntity.ok(compraService.cotizar(cotizacionRequest));
    }

    @GetMapping("/{id}/estado")
    public ResponseEntity<Map<String, String>> consultarEstadoCompra(@PathVariable Long id) {
        return compraService.consultarEstadoCompra(id)
                .map(estado -> ResponseEntity.ok(Map.of("estado", estado)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/verificar-pago")
    @Operation(summary = "Verificar el pago directamente contra Mercado Pago",
            description = "Si la compra sigue PENDIENTE_PAGO, consulta la API de Mercado Pago por external_reference en vez de esperar al webhook. " +
                    "Pensado para que el navegador del cliente lo dispare al volver del pago, sin depender de que el webhook haya llegado.")
    public ResponseEntity<Map<String, String>> verificarPago(@PathVariable Long id) {
        String estado = compraService.verificarPagoDirecto(id);
        return ResponseEntity.ok(Map.of("estado", estado));
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
        List<CompraResponseDTO> compras = compraService.getAllByDni(dni).stream().map(CompraController::entityToDto).collect(Collectors.toList());
        return ResponseEntity.ok(compras);
    }

    @GetMapping("/fecha/{fecha}")
    @Operation(summary = "Obtener compras por fecha de visita", description = "Obtiene todas las reservas para un día de visita puntual (yyyy-MM-dd), ordenadas por código de reserva")
    @ApiResponse(responseCode = "200", description = "Lista de compras obtenida exitosamente")
    public ResponseEntity<List<CompraResponseDTO>> obtenerComprasPorFecha(@PathVariable @Parameter(description = "Fecha de visita (yyyy-MM-dd)") LocalDate fecha) {
        List<CompraResponseDTO> compras = compraService.getAllByFechaVisita(fecha).stream().map(CompraController::entityToDto).collect(Collectors.toList());
        return ResponseEntity.ok(compras);
    }

    @GetMapping
    @Operation(summary = "Obtener todas las compras", description = "Obtiene la lista de todas las compras realizadas")
    @ApiResponse(responseCode = "200", description = "Lista de compras obtenida exitosamente")
    public ResponseEntity<List<CompraResponseDTO>> obtenerTodasCompras() {
        List<CompraResponseDTO> compras = compraService.getAll().stream().map(CompraController::entityToDto).collect(Collectors.toList());
        return ResponseEntity.ok(compras);
    }

    @PostMapping
    @Operation(summary = "Crear compra", description = "Crea una nueva compra con sus detalles")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Compra creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    public ResponseEntity<CompraResponseDTO> crearCompra(@RequestBody @Parameter(description = "Datos de la compra") CompraRequestDTO compraRequest) {
        Compra creada = compraService.create(compraRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(creada));
    }

    @PutMapping("/{id}/validar")
    @Operation(summary = "Validar/Marcar entradas como usadas", description = "Marca la compra como usada y registra al boletero autenticado como validador")
    public ResponseEntity<CompraResponseDTO> validarCompra(@PathVariable @Parameter(description = "ID de la compra") Long id,
                                                           @AuthenticationPrincipal UsuarioAutenticado operador) {
        // El validador sale del token, no del cuerpo del request: si lo mandara el
        // cliente, cualquier boletero podría atribuirle el ingreso a un compañero.
        Compra actualizada = compraService.marcarEntradasComoUsadas(id, operador.id());
        return ResponseEntity.ok(entityToDto(actualizada));
    }

    static CompraResponseDTO entityToDto(Compra c) {
        CompraResponseDTO dto = new CompraResponseDTO();
        dto.setId(c.getId());
        dto.setCodigoReserva(c.getCodigoReserva());
        if (c.getCliente() != null) {
            ClienteResponseDTO cr = new ClienteResponseDTO();
            cr.setId(c.getCliente().getId());
            cr.setDni(c.getCliente().getDni());
            cr.setNombre(c.getCliente().getNombre());
            cr.setApellido(c.getCliente().getApellido());
            cr.setEdad(c.getCliente().getEdad());
            cr.setLocalidad(c.getCliente().getLocalidad());
            dto.setCliente(cr);
        }
        dto.setFechaVisita(c.getFechaVisita());
        dto.setContactEmail(c.getContactEmail());
        dto.setContactPhone(c.getContactPhone());
        dto.setMontoTotal(c.getMontoTotal());
        dto.setDescuentoAplicado(c.getDescuentoAplicado());
        dto.setEstado(c.getEstado() != null ? c.getEstado().name() : null);
        dto.setCuponCodigo(c.getCupon() != null ? c.getCupon().getCodigo() : null);
        dto.setFormaPago(c.getFormaPago());
        dto.setFechaValidacion(c.getFechaValidacion());
        dto.setUsuarioValidador(c.getUsuarioValidador() != null ? c.getUsuarioValidador().getUsername() : null);
        dto.setReceptorNombre(c.getReceptorNombre());
        dto.setReceptorEmail(c.getReceptorEmail());
        dto.setReceptorDni(c.getReceptorDni());
        dto.setReceptorTelefono(c.getReceptorTelefono());
        if (c.getDetalles() != null) {
            List<CompraDetalleResponseDTO> detalles = c.getDetalles().stream().map(CompraController::detalleEntityToDto).collect(Collectors.toList());
            dto.setDetalles(detalles);
        }
        return dto;
    }

    static CompraDetalleResponseDTO detalleEntityToDto(CompraDetalle d) {
        CompraDetalleResponseDTO dto = new CompraDetalleResponseDTO();
        dto.setId(d.getId());
        if (d.getTipoEntrada() != null) {
            TipoEntradaResponseDTO t = new TipoEntradaResponseDTO();
            t.setId(d.getTipoEntrada().getId());
            t.setNombre(d.getTipoEntrada().getNombre());
            t.setDescripcion(d.getTipoEntrada().getDescripcion());
            t.setPrecio(d.getTipoEntrada().getPrecio());
            t.setTipo(d.getTipoEntrada().getTipo());
            dto.setTipoEntrada(t);
        }
        dto.setCantidad(d.getCantidad());
        return dto;
    }

}
