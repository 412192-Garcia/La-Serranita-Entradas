package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.service.CompraService;
import org.example.laserranitaentradas.model.entity.Modulo;
import org.example.laserranitaentradas.service.ModulosService;
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
    private final ModulosService modulos;

    public CompraController(CompraService compraService, ModulosService modulos) {
        this.compraService = compraService;
        this.modulos = modulos;
    }

    /** Qué formas de pago ofrece hoy la compra online (las que se pueden apagar en Sistema > Módulos). */
    @GetMapping("/opciones-pago")
    @Operation(summary = "Formas de pago disponibles en la compra online")
    public Map<String, Boolean> opcionesPago() {
        return Map.of("reservaEfectivo", modulos.activo(Modulo.RESERVA_EFECTIVO));
    }

    @PostMapping("/iniciar-pago")
    @Operation(summary = "Iniciar compra con pago online", description = "Crea la compra en estado PENDIENTE y genera la preferencia de Mercado Pago")
    public ResponseEntity<CompraResponseDTO> iniciarPago(@RequestBody @Parameter(description = "Datos de la compra") CompraRequestDTO compraRequest) throws Exception {
        rechazarFormaPagoSoloInterna(compraRequest);
        if (compraRequest.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA && !modulos.activo(Modulo.RESERVA_EFECTIVO)) {
            throw new IllegalArgumentException("Por ahora no se puede reservar para pagar en el parque: elegí pagar online.");
        }
        CompraResponseDTO response = compraService.iniciarCompraConPago(compraRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** RESERVA_ADMIN y SIN_COBRO no se aceptan en el endpoint público: serían entradas sin cobro. */
    private static void rechazarFormaPagoSoloInterna(CompraRequestDTO compraRequest) {
        if (compraRequest.getFormaPago() == FormaPago.RESERVA_ADMIN
                || compraRequest.getFormaPago() == FormaPago.SIN_COBRO) {
            throw new IllegalArgumentException("Forma de pago no válida.");
        }
    }

    @PostMapping("/cotizar")
    @Operation(summary = "Cotizar precio", description = "Subtotal con descuentos, sin guardar nada")
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
            description = "Si sigue PENDIENTE_PAGO, consulta a Mercado Pago sin esperar al webhook. Lo llama el navegador al volver del pago.")
    public ResponseEntity<Map<String, String>> verificarPago(@PathVariable Long id) {
        String estado = compraService.verificarPagoDirecto(id);
        return ResponseEntity.ok(Map.of("estado", estado));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Resumen público de una compra",
            description = "Código, estado, total y DNI enmascarado. Sin datos de contacto.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Compra encontrada"),
            @ApiResponse(responseCode = "404", description = "Compra no encontrada")
    })
    public ResponseEntity<CompraResponseDTO> obtenerCompraPorId(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Optional<Compra> compra = compraService.findById(id);
        return compra.map(c -> ResponseEntity.ok(resumenPublico(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/codigo/{codigoReserva}")
    @Operation(summary = "Resumen público de una compra por código de reserva",
            description = "Mismo resumen que por ID (el código llega en external_reference).")
    public ResponseEntity<CompraResponseDTO> obtenerCompraPorCodigo(@PathVariable String codigoReserva) {
        return compraService.findByCodigoReserva(codigoReserva)
                .map(c -> ResponseEntity.ok(resumenPublico(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Endpoint anónimo con IDs correlativos: sin recortar se podían bajar los datos de todos los clientes.
     */
    private static CompraResponseDTO resumenPublico(Compra c) {
        CompraResponseDTO dto = new CompraResponseDTO();
        dto.setId(c.getId());
        dto.setCodigoReserva(c.getCodigoReserva());
        dto.setEstado(c.getEstado() != null ? c.getEstado().name() : null);
        dto.setFormaPago(c.getFormaPago());
        dto.setFechaVisita(c.getFechaVisita());
        // No es un dato personal, y la pantalla de éxito lo necesita para registrar la conversión.
        dto.setMontoTotal(c.getMontoTotal());
        if (c.getCliente() != null) {
            ClienteResponseDTO cliente = new ClienteResponseDTO();
            cliente.setDni(enmascararDocumento(c.getCliente().getDni()));
            dto.setCliente(cliente);
        }
        // En un regalo se muestra el DNI de quien lo recibe, enmascarado.
        dto.setReceptorDni(enmascararDocumento(c.getReceptorDni()));
        return dto;
    }

    private static String enmascararDocumento(String documento) {
        if (documento == null) {
            return null;
        }
        if (documento.length() <= 3) {
            return "*".repeat(documento.length());
        }
        return "*".repeat(documento.length() - 3) + documento.substring(documento.length() - 3);
    }

    /** Claves de orden del front mapeadas a propiedades JPA reales. */
    private static final Map<String, String> CAMPOS_ORDENABLES = Map.of(
            "fechaVisita", "fechaVisita",
            "codigoReserva", "codigoReserva",
            "estado", "estado",
            "monto", "montoTotal",
            "titular", "cliente.apellido"
    );

    @GetMapping("/buscar")
    @Operation(summary = "Búsqueda paginada de boletería",
            description = "Texto libre (DNI, nombre, código, email), fecha, tipo, estado y forma de pago. Pagina en la base.")
    public ResponseEntity<Page<CompraResponseDTO>> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            // Rango para la vista agrupada por día ("ver todas las fechas").
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            // Regalos (fechaVisita null). Excluyente con fecha/fechaDesde/fechaHasta.
            @RequestParam(required = false) Boolean sinFecha,
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
        // codigoReserva desempata, para un orden estable entre páginas.
        Sort orden = Sort.by(sentido, campoOrden);
        if (!"codigoReserva".equals(campoOrden)) {
            orden = orden.and(Sort.by(Sort.Direction.ASC, "codigoReserva"));
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanioPagina, orden);
        BusquedaComprasFiltroDTO filtro = new BusquedaComprasFiltroDTO(texto, fecha, fechaDesde, fechaHasta, sinFecha, tipo, estados, formaPago);
        Page<CompraResponseDTO> resultado = compraService.buscar(filtro, pageable).map(CompraController::entityToDto);
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/fechas-visita")
    @Operation(summary = "Días distintos con compras, paginados por día",
            description = "Mismos filtros que /buscar, pero pagina por días con compras. Después se piden esos días a /buscar con fechaDesde/fechaHasta.")
    public ResponseEntity<Page<LocalDate>> fechasVisita(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) TipoListadoCompra tipo,
            @RequestParam(required = false) List<EstadoCompra> estados,
            @RequestParam(required = false) FormaPago formaPago,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamanioPagina = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanioPagina);
        BusquedaComprasFiltroDTO filtro = new BusquedaComprasFiltroDTO(texto, null, fechaDesde, fechaHasta, null, tipo, estados, formaPago);
        return ResponseEntity.ok(compraService.fechasDistintas(filtro, pageable));
    }

    @PostMapping
    @Operation(summary = "Crear compra", description = "Crea una nueva compra con sus detalles")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Compra creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    public ResponseEntity<CompraResponseDTO> crearCompra(@RequestBody @Parameter(description = "Datos de la compra") CompraRequestDTO compraRequest) {
        rechazarFormaPagoSoloInterna(compraRequest);
        Compra creada = compraService.create(compraRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(creada));
    }

    @PutMapping("/{id}/validar")
    @Operation(summary = "Validar/Marcar entradas como usadas", description = "Marca la compra como usada y registra al boletero autenticado como validador")
    public ResponseEntity<CompraResponseDTO> validarCompra(@PathVariable @Parameter(description = "ID de la compra") Long id,
                                                           @AuthenticationPrincipal UsuarioAutenticado operador) {
        // El validador sale del token: si viniera en el request, un boletero podría atribuirle el ingreso a
        // otro.
        Compra actualizada = compraService.marcarEntradasComoUsadas(id, operador.id());
        return ResponseEntity.ok(entityToDto(actualizada));
    }

    @PutMapping("/{id}/deshacer-validacion")
    @Operation(summary = "Deshacer una validación reciente", description = "Vuelve al estado anterior. Sólo poco después de validar.")
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
            // El congelado al reservar/vender; las compras anteriores a ese dato usan el actual.
            dto.setPrecioUnitario(d.getPrecioUnitario() != null ? d.getPrecioUnitario() : d.getTipoEntrada().getPrecio());
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
