package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.impresion.ImpresionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interno/impresion")
@Tag(name = "Impresión", description = "Ticketeras conectadas y reimpresión de facturas")
public class ImpresionController {

    private final ImpresionService impresionService;
    private final FacturaRepository facturaRepository;

    public ImpresionController(ImpresionService impresionService, FacturaRepository facturaRepository) {
        this.impresionService = impresionService;
        this.facturaRepository = facturaRepository;
    }

    @GetMapping("/impresoras")
    @Operation(summary = "Ticketeras conectadas ahora", description = "El POS lo usa para bloquear \"Imprimir factura\" si no hay ninguna.")
    public List<ImpresionService.ImpresoraConectada> impresoras() {
        return impresionService.impresorasConectadas();
    }

    @PostMapping("/facturas/{facturaId}")
    @Operation(summary = "Imprimir (o reimprimir) el ticket de una factura emitida")
    public Map<String, Object> imprimir(@PathVariable @Parameter(description = "ID de la factura") Long facturaId,
                                        @RequestParam(required = false) String impresora) {
        var factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (factura.getEstado() != EstadoFactura.EMITIDA) {
            throw new IllegalStateException("La factura todavía no está emitida");
        }
        TrabajoImpresion t = impresionService.imprimirFactura(facturaId, impresora);
        return Map.of("trabajoId", t.getId(), "estado", t.getEstado(), "impresora", t.getImpresora());
    }
}
