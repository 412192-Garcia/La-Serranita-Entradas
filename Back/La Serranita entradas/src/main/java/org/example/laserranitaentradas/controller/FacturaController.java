package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.dto.VentaFacturaDTO;
import org.example.laserranitaentradas.service.factura.VentasFacturasService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.example.laserranitaentradas.service.FacturaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interno/facturas")
@Tag(name = "Facturación", description = "Facturas electrónicas B emitidas vía ARCA")
public class FacturaController {

    private final FacturaService facturaService;
    private final VentasFacturasService ventasFacturasService;

    public FacturaController(FacturaService facturaService, VentasFacturasService ventasFacturasService) {
        this.facturaService = facturaService;
        this.ventasFacturasService = ventasFacturasService;
    }

    @GetMapping("/estado-servicio")
    @Operation(summary = "Si la facturación está configurada", description = "El POS lo usa para mostrar u ocultar las opciones de factura.")
    public Map<String, Boolean> estadoServicio() {
        return Map.of("habilitada", facturaService.estaHabilitada());
    }

    @GetMapping("/compra/{compraId}")
    @Operation(summary = "Factura de una compra", description = "El POS consulta esto después de vender con \"Imprimir factura\" hasta que queda EMITIDA (o ERROR). 404 si la compra no se facturó.")
    public ResponseEntity<FacturaResponseDTO> porCompra(@PathVariable @Parameter(description = "ID de la compra") Long compraId,
                                                        @AuthenticationPrincipal UsuarioAutenticado operador) {
        return facturaService.obtenerPorCompra(compraId)
                .map(factura -> {
                    ventasFacturasService.validarAccesoAFactura(factura.getId(), operador);
                    return ResponseEntity.ok(factura);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "PDF de una factura emitida", description = "Mismo formato que el ticket: sirve para reimprimir o mandarla por otro medio.")
    public ResponseEntity<byte[]> pdf(@PathVariable @Parameter(description = "ID de la factura") Long id,
                                      @AuthenticationPrincipal UsuarioAutenticado operador) {
        ventasFacturasService.validarAccesoAFactura(id, operador);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"factura-" + id + ".pdf\"")
                .body(facturaService.generarPdf(id));
    }

    @GetMapping("/caja/{cajaId}/ventas")
    @Operation(summary = "Ventas de un turno con su factura",
            description = "Para la ventana \"Ventas y facturas\": reimprimir, mandar por mail o facturar una venta que se cobró sin factura. Un boletero sólo puede ver su propia caja.")
    public List<VentaFacturaDTO> ventasDeCaja(@PathVariable @Parameter(description = "ID de la caja") Long cajaId,
                                              @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ventasFacturasService.ventasDeCaja(cajaId, operador);
    }

    @PostMapping("/compra/{compraId}")
    @Operation(summary = "Facturar una venta que se cobró sin factura",
            description = "Ej. cobro en efectivo con el mail vacío y el cliente después pide la factura. Mismo pedido que en el POS: imprimir o mandar por mail.")
    public FacturaResponseDTO facturar(@PathVariable @Parameter(description = "ID de la venta") Long compraId,
                                       @RequestBody FacturacionPosDTO pedido,
                                       @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ventasFacturasService.facturar(compraId, pedido, operador);
    }

    @PostMapping("/{id}/enviar-mail")
    @Operation(summary = "Mandar una factura emitida a un email",
            description = "Para cuando el ticket no salió o el cliente la quiere por mail. A diferencia de reenviar-mail, acepta un email nuevo y lo puede usar un boletero.")
    public ResponseEntity<Void> enviarMail(@PathVariable @Parameter(description = "ID de la factura") Long id,
                                           @RequestBody Map<String, String> cuerpo,
                                           @AuthenticationPrincipal UsuarioAutenticado operador) {
        ventasFacturasService.enviarPorMail(id, cuerpo.get("email"), operador);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reenviar-mail")
    @Operation(summary = "Reenviar la factura por mail (ADMIN)", description = "Para cuando el envío automático falló (queda como rechazo FACTURA_EMAIL).")
    public ResponseEntity<Void> reenviarMail(@PathVariable @Parameter(description = "ID de la factura") Long id) {
        facturaService.reenviarMail(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reintentar")
    @Operation(summary = "Reintentar una factura en ERROR (ADMIN)")
    public FacturaResponseDTO reintentar(@PathVariable @Parameter(description = "ID de la factura") Long id) {
        return facturaService.reintentar(id);
    }
}
