package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.dto.VentaPosRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.service.CompraService;
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

    public CompraInternoController(CompraService compraService) {
        this.compraService = compraService;
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
        Compra venta = compraService.registrarVentaPos(request, operador.id());
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

    @PostMapping("/{id}/reembolsar")
    @Operation(summary = "Reembolsar una compra pagada online",
            description = "Sólo válido para compras APROBADO (pagadas por Mercado Pago) que todavía no ingresaron; dispara el reembolso real vía la API de Mercado Pago")
    public ResponseEntity<CompraResponseDTO> reembolsar(@PathVariable @Parameter(description = "ID de la compra") Long id) {
        Compra compra = compraService.reembolsarCompra(id);
        return ResponseEntity.ok(CompraController.entityToDto(compra));
    }
}
