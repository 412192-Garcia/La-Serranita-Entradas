package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.service.CompraService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

    /** Claves de orden que expone el frontend, mapeadas a la propiedad JPA real (evita inyectar cualquier campo). */
    private static final Map<String, String> CAMPOS_ORDENABLES = Map.of(
            "fechaVisita", "fechaVisita",
            "codigoReserva", "codigoReserva",
            "estado", "estado",
            "monto", "montoTotal",
            "titular", "cliente.apellido"
    );

    @GetMapping("/buscar")
    @Operation(summary = "Búsqueda paginada de boletería",
            description = "Texto libre (DNI/nombre/apellido/código de reserva/email), fecha, boletería vs. anticipada, estado(s) y forma de pago. " +
                    "Filtra, ordena y pagina en la base, así que soporta días con miles de compras sin traer todo a memoria.")
    public ResponseEntity<Page<CompraResponseDTO>> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) TipoListadoCompra tipo,
            @RequestParam(required = false) List<EstadoCompra> estados,
            @RequestParam(required = false) FormaPago formaPago,
            @RequestParam(defaultValue = "fechaVisita") String ordenarPor,
            @RequestParam(defaultValue = "ASC") String direccion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int tamanioPagina = Math.min(Math.max(size, 1), 200);
        String campoOrden = CAMPOS_ORDENABLES.getOrDefault(ordenarPor, "fechaVisita");
        Sort.Direction sentido = "DESC".equalsIgnoreCase(direccion) ? Sort.Direction.DESC : Sort.Direction.ASC;
        // codigoReserva como desempate: mismo orden estable entre páginas aunque el campo elegido tenga empates.
        Sort orden = Sort.by(sentido, campoOrden);
        if (!"codigoReserva".equals(campoOrden)) {
            orden = orden.and(Sort.by(Sort.Direction.ASC, "codigoReserva"));
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanioPagina, orden);
        BusquedaComprasFiltroDTO filtro = new BusquedaComprasFiltroDTO(texto, fecha, tipo, estados, formaPago);
        Page<CompraResponseDTO> resultado = compraService.buscar(filtro, pageable).map(CompraController::entityToDto);
        return ResponseEntity.ok(resultado);
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

    @PutMapping("/{id}/deshacer-validacion")
    @Operation(summary = "Deshacer una validación reciente", description = "Vuelve la compra al estado anterior (APROBADO o RESERVADO_EFECTIVO). Sólo funciona dentro de una ventana corta desde que se validó.")
    public ResponseEntity<CompraResponseDTO> deshacerValidacion(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Compra actualizada = compraService.deshacerValidacion(id);
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
            dto.setPrecioUnitario(d.getTipoEntrada().getPrecio());
        }
        if (d.getArticuloVario() != null) {
            dto.setArticuloVario(org.example.laserranitaentradas.model.dto.ArticuloVarioResponseDTO.builder()
                    .id(d.getArticuloVario().getId())
                    .nombre(d.getArticuloVario().getNombre())
                    .precioSugerido(d.getArticuloVario().getPrecioSugerido())
                    .activo(d.getArticuloVario().getActivo())
                    .build());
            dto.setPrecioUnitario(d.getPrecioUnitario());
        }
        if (d.getDescripcionLibre() != null) {
            dto.setDescripcionLibre(d.getDescripcionLibre());
            dto.setPrecioUnitario(d.getPrecioUnitario());
        }
        dto.setCantidad(d.getCantidad());
        return dto;
    }

}
