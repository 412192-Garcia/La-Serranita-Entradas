package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.CambiarFechaRequest;
import org.example.laserranitaentradas.model.dto.CancelarVentaRequest;
import org.example.laserranitaentradas.model.dto.CompraFichaDTO;
import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.dto.EditarVentaRequestDTO;
import org.example.laserranitaentradas.model.dto.VentaPosRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCobroPoint;
import org.example.laserranitaentradas.repository.CobroPointRepository;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.afip.WsfeService;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.example.laserranitaentradas.service.CompraService;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/interno/compras")
@Tag(name = "Compras (Módulo Interno)", description = "Operaciones operativas para el personal de boletería")
public class CompraInternoController {

    private final CompraService compraService;
    private final RechazoOperacionService rechazoService;
    private final FacturaRepository facturaRepository;
    private final CobroPointRepository cobroPointRepository;

    public CompraInternoController(CompraService compraService, RechazoOperacionService rechazoService,
                                   FacturaRepository facturaRepository, CobroPointRepository cobroPointRepository) {
        this.compraService = compraService;
        this.rechazoService = rechazoService;
        this.facturaRepository = facturaRepository;
        this.cobroPointRepository = cobroPointRepository;
    }

    @PostMapping("/{id}/confirmar-pago-efectivo")
    @Operation(summary = "Confirmar cobro en boletería", description = "La reserva pasa a USADO. Un boletero necesita caja abierta; un admin puede cobrar sin caja.")
    public ResponseEntity<CompraResponseDTO> confirmarPagoEfectivo(
            @PathVariable @Parameter(description = "ID de la compra") Long id,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        // Quién cobró sale del token, no del cuerpo del request.
        boolean permitirSinCaja = operador.rol().esAdmin();
        Compra compra = compraService.confirmarPagoEfectivo(id, operador.id(), permitirSinCaja);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PostMapping("/venta-pos")
    @Operation(summary = "Registrar una venta presencial en boletería",
            description = "Cobro e ingreso en un paso, sin datos de cliente. El precio de grupo sólo en efectivo.")
    public ResponseEntity<CompraResponseDTO> registrarVentaPos(
            @RequestBody VentaPosRequestDTO request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        // Quién vendió sale del token, no del cuerpo del request.
        try {
            Compra venta = compraService.registrarVentaPos(request, operador.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(CompraController.entityToDto(venta));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Sólo se registra si pasó en un reintento en segundo plano.
            if (Boolean.TRUE.equals(request.getEsReintentoEncolado())) {
                rechazoService.registrar("VENTA", request, ex.getMessage(), request.getIdempotencyKey());
            }
            throw ex;
        }
    }

    @PostMapping("/caja/{cajaId}/venta-pos")
    @Operation(summary = "Cargar una venta que le faltó registrar a un boletero (ADMIN)",
            description = "Como /venta-pos, en la caja indicada. Queda a nombre del dueño de la caja.")
    public ResponseEntity<CompraResponseDTO> registrarVentaPosComoAdmin(
            @PathVariable @Parameter(description = "ID de la caja") Long cajaId,
            @RequestBody VentaPosRequestDTO request) {
        Compra venta = compraService.registrarVentaPosComoAdmin(cajaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraController.entityToDto(venta));
    }

    @PostMapping("/generar-reserva")
    @Operation(summary = "Generar una reserva a mano, sin cobrar nada por acá", description = "Invitados o ventas por agencia. Siempre queda como RESERVA_ADMIN.")
    public ResponseEntity<CompraResponseDTO> generarReserva(@RequestBody @Parameter(description = "Datos de la reserva") CompraRequestDTO request) throws Exception {
        // Se pisa acá, no en el body: así ningún cliente puede pedirlo mandando "RESERVA_ADMIN" a mano.
        request.setFormaPago(FormaPago.RESERVA_ADMIN);
        CompraResponseDTO response = compraService.iniciarCompraConPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/contacto")
    @Operation(summary = "Editar datos de contacto de la reserva", description = "Titular y contacto. No toca fecha, entradas ni montos.")
    public ResponseEntity<CompraResponseDTO> actualizarContacto(
            @PathVariable @Parameter(description = "ID de la compra") Long id,
            @RequestBody EditarContactoRequest request) {
        Compra compra = compraService.actualizarContacto(id, request);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PutMapping("/{id}/fecha")
    @Operation(summary = "Cambiar el día de visita de una reserva (ADMIN)",
            description = "Reservas sin ingresar (no regalos). El día tiene que estar abierto y con cupo. Opcionalmente reenvía el comprobante.")
    public ResponseEntity<CompraResponseDTO> cambiarFecha(@PathVariable @Parameter(description = "ID de la compra") Long id,
                                                          @RequestBody CambiarFechaRequest request) {
        Compra compra = compraService.cambiarFechaVisita(id, request.getFecha(), request.isAvisarPorMail());
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PostMapping("/{id}/reenviar-mail")
    @Operation(summary = "Reenviar el comprobante por mail", description = "Vuelve a enviar el mail de confirmación (y, si es un regalo, el aviso al receptor)")
    public ResponseEntity<Void> reenviarMail(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        compraService.reenviarComprobante(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/cancelar-venta")
    @Operation(summary = "Cancelar una venta de puerta mal cargada (ADMIN)",
            description = "Motivo opcional. Queda CANCELADO y deja de contar en cupo, caja y reportes. Sólo ventas de caja.")
    public ResponseEntity<CompraResponseDTO> cancelarVenta(@PathVariable @Parameter(description = "ID de la compra") Long id,
                                                           @RequestBody(required = false) CancelarVentaRequest request) {
        Compra compra = compraService.cancelarVenta(id, request != null ? request.getMotivo() : null);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Compra completa, con sus entradas y artículos (ADMIN)",
            description = "Para corregir una venta. El GET público de /api/compras sólo da un resumen.")
    public ResponseEntity<CompraResponseDTO> obtenerCompra(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        return compraService.findById(id)
                .map(c -> ResponseEntity.ok(CompraController.entityToDto(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/ficha")
    @Operation(summary = "Todos los datos de una compra",
            description = "Para el \"Ver detalle\" de Control de Accesos: la compra más pago, caja, auditoría y cancelación.")
    public ResponseEntity<CompraFichaDTO> obtenerFicha(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        return compraService.findById(id)
                .map(c -> ResponseEntity.ok(aFicha(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private CompraFichaDTO aFicha(Compra c) {
        CompraFichaDTO f = new CompraFichaDTO();
        f.setCompra(CompraController.entityToDto(c));
        f.setFechaCompra(c.getFechaCreacion());
        cobroPointRepository.findAllByCompraId(c.getId()).stream()
                .filter(cp -> cp.getEstado() == EstadoCobroPoint.APROBADO)
                .findFirst()
                .ifPresent(cp -> {
                    f.setPointMedioPago(cp.getMedioPago());
                    f.setPointTipoMedio(cp.getTipoMedio());
                    f.setPointCuotas(cp.getCuotas());
                });
        facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(c.getId(), WsfeService.CBTE_TIPO_FACTURA_B)
                .or(() -> facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(c.getId(), WsfeService.CBTE_TIPO_FACTURA_A))
                .ifPresent(fa -> {
                    String nombre = WsfeService.nombre(fa.getTipoComprobante());
                    f.setFactura(fa.getNumero() != null
                            ? String.format("%s %04d-%08d", nombre, fa.getPuntoVenta(), fa.getNumero()) : nombre);
                    f.setFacturaEstado(fa.getEstado() != null ? fa.getEstado().name() : null);
                });
        if (c.getCliente() != null) {
            f.setClienteLocalidad(c.getCliente().getLocalidad());
            f.setClienteEdad(c.getCliente().getEdad());
        }
        f.setPromocionNombre(c.getPromocion() != null ? c.getPromocion().getNombre() : null);
        f.setFechaPago(c.getFechaPago());
        f.setPagoConfirmadoPor(c.getPagoConfirmadoPor());
        f.setPagosMercadoPago(c.pagosMercadoPago().stream().map(String::valueOf).toList());
        f.setDispositivo(c.getDispositivo());
        f.setEnIframe(c.getEnIframe());
        if (c.getCaja() != null) {
            f.setCajaId(c.getCaja().getId());
            f.setCajaUsuario(c.getCaja().getUsuario() != null ? c.getCaja().getUsuario().getUsername() : null);
            f.setCajaFechaApertura(c.getCaja().getFechaApertura());
        }
        f.setFormaPagoSecundaria(c.getFormaPagoSecundaria());
        f.setMontoFormaPagoSecundaria(c.getMontoFormaPagoSecundaria());
        f.setCotizacionDolar(c.getCotizacionDolar());
        f.setDolaresRecibidos(c.getDolaresRecibidos());
        f.setMotivoCancelacion(c.getMotivoCancelacion());
        f.setDetalleCancelacion(c.getDetalleCancelacion());
        f.setFechaCancelacion(c.getFechaCancelacion());
        f.setCanceladaPor(c.getCanceladaPor());
        return f;
    }

    @PutMapping("/{id}/editar-venta")
    @Operation(summary = "Corregir las entradas, artículos y/o la forma de pago de una venta de puerta (ADMIN)",
            description = "Reemplaza entradas y artículos (la lista final, no un diff). El descuento queda igual; revalida cupo y recalcula el monto.")
    public ResponseEntity<CompraResponseDTO> editarVenta(
            @PathVariable @Parameter(description = "ID de la compra") Long id,
            @RequestBody EditarVentaRequestDTO request) {
        Compra compra = compraService.editarVenta(id, request);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PostMapping("/{id}/cancelar-reserva")
    @Operation(summary = "Cancelar una reserva generada desde Acciones (ADMIN)",
            description = "RESERVA_ADMIN sin ingresar. Sin reembolso.")
    public ResponseEntity<CompraResponseDTO> cancelarReserva(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Compra compra = compraService.cancelarReservaAdmin(id);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PostMapping("/{id}/reembolsar")
    @Operation(summary = "Reembolsar una compra pagada online",
            description = "Sólo APROBADO sin ingresar. Reembolsa por Mercado Pago.")
    public ResponseEntity<CompraResponseDTO> reembolsar(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Compra compra = compraService.reembolsarCompra(id);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }
}
