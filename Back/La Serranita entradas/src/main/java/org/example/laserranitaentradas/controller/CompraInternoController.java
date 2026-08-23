package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.dto.EditarVentaRequestDTO;
import org.example.laserranitaentradas.model.dto.VentaPosRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.FormaPago;
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

    public CompraInternoController(CompraService compraService, RechazoOperacionService rechazoService) {
        this.compraService = compraService;
        this.rechazoService = rechazoService;
    }

    @PostMapping("/{id}/confirmar-pago-efectivo")
    @Operation(summary = "Confirmar cobro en boletería", description = "El boletero confirma que recibió el efectivo de una reserva EFECTIVO_BOLETERIA; la compra pasa a USADO y habilita el ingreso")
    public ResponseEntity<CompraResponseDTO> confirmarPagoEfectivo(
            @PathVariable @Parameter(description = "ID de la compra") Long id,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        // Quién cobró sale del token, no del cuerpo del request.
        Compra compra = compraService.confirmarPagoEfectivo(id, operador.id());
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PostMapping("/venta-pos")
    @Operation(summary = "Registrar una venta presencial en boletería",
            description = "Cobra y habilita el ingreso en un solo paso: la compra queda en USADO, sin datos de cliente. " +
                    "El precio promocional por grupo sólo se aplica si el cobro es en efectivo.")
    public ResponseEntity<CompraResponseDTO> registrarVentaPos(
            @RequestBody VentaPosRequestDTO request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        // Quién vendió sale del token, no del cuerpo del request.
        try {
            Compra venta = compraService.registrarVentaPos(request, operador.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(CompraController.entityToDto(venta));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Ver el comentario en CajaController: sólo interesa si pasó en un reintento en
            // segundo plano, no en el primer intento en vivo (ya lo ve la persona ahí mismo).
            if (Boolean.TRUE.equals(request.getEsReintentoEncolado())) {
                rechazoService.registrar("VENTA", request, ex.getMessage(), request.getIdempotencyKey());
            }
            throw ex;
        }
    }

    @PostMapping("/caja/{cajaId}/venta-pos")
    @Operation(summary = "Cargar una venta que le faltó registrar a un boletero (ADMIN)",
            description = "Igual que /venta-pos, pero el admin la carga directamente en la caja indicada por id (no la propia): para cuando revisa el detalle de una caja abierta y nota que falta una venta. Queda a nombre del dueño de esa caja.")
    public ResponseEntity<CompraResponseDTO> registrarVentaPosComoAdmin(
            @PathVariable @Parameter(description = "ID de la caja") Long cajaId,
            @RequestBody VentaPosRequestDTO request) {
        Compra venta = compraService.registrarVentaPosComoAdmin(cajaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraController.entityToDto(venta));
    }

    @PostMapping("/generar-reserva")
    @Operation(summary = "Generar una reserva a mano, sin cobrar nada por acá", description = "Sólo ADMIN: para invitados o ventas por agencia (el cobro real, si lo hay, se resolvió por fuera). La forma de pago del cuerpo se ignora, siempre queda como RESERVA_ADMIN.")
    public ResponseEntity<CompraResponseDTO> generarReserva(@RequestBody @Parameter(description = "Datos de la reserva") CompraRequestDTO request) throws Exception {
        // Se pisa acá, no en el body: así ningún cliente puede pedirlo mandando "RESERVA_ADMIN" a mano.
        request.setFormaPago(FormaPago.RESERVA_ADMIN);
        CompraResponseDTO response = compraService.iniciarCompraConPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/contacto")
    @Operation(summary = "Editar datos de contacto de la reserva", description = "Corrige nombre/apellido del titular y su email/teléfono de contacto. No permite tocar fecha, entradas ni montos.")
    public ResponseEntity<CompraResponseDTO> actualizarContacto(
            @PathVariable @Parameter(description = "ID de la compra") Long id,
            @RequestBody EditarContactoRequest request) {
        Compra compra = compraService.actualizarContacto(id, request);
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
            description = "La marca CANCELADO en vez de borrarla (queda el registro para auditoría) y deja de contar para cupo diario, totales de caja y reportes. Sólo para ventas que pasaron por una caja.")
    public ResponseEntity<CompraResponseDTO> cancelarVenta(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Compra compra = compraService.cancelarVenta(id);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PutMapping("/{id}/editar-venta")
    @Operation(summary = "Corregir las entradas, artículos y/o la forma de pago de una venta de puerta (ADMIN)",
            description = "Ej. el cajero cargó de más o cobró con el método equivocado. Reemplaza entradas y artículos por completo (mandá la lista final, no un diff); el descuento ya aplicado queda igual. Revalida cupo diario y recalcula el monto.")
    public ResponseEntity<CompraResponseDTO> editarVenta(
            @PathVariable @Parameter(description = "ID de la compra") Long id,
            @RequestBody EditarVentaRequestDTO request) {
        Compra compra = compraService.editarVenta(id, request);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }

    @PostMapping("/{id}/reembolsar")
    @Operation(summary = "Reembolsar una compra pagada online",
            description = "Sólo válido para compras APROBADO (pagadas por Mercado Pago) que todavía no ingresaron; dispara el reembolso real vía la API de Mercado Pago")
    public ResponseEntity<CompraResponseDTO> reembolsar(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Compra compra = compraService.reembolsarCompra(id);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }
}
