package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.service.FacturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/interno/facturas")
@Tag(name = "Facturación", description = "Facturas electrónicas B emitidas vía ARCA")
public class FacturaController {

    private final FacturaService facturaService;

    public FacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    @GetMapping("/estado-servicio")
    @Operation(summary = "Si la facturación está configurada", description = "El POS lo usa para mostrar u ocultar las opciones de factura.")
    public Map<String, Boolean> estadoServicio() {
        return Map.of("habilitada", facturaService.estaHabilitada());
    }

    @GetMapping("/compra/{compraId}")
    @Operation(summary = "Factura de una compra", description = "El POS consulta esto después de vender con \"Imprimir factura\" hasta que queda EMITIDA (o ERROR). 404 si la compra no se facturó.")
    public ResponseEntity<FacturaResponseDTO> porCompra(@PathVariable @Parameter(description = "ID de la compra") Long compraId) {
        return facturaService.obtenerPorCompra(compraId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reintentar")
    @Operation(summary = "Reintentar una factura en ERROR (ADMIN)")
    public FacturaResponseDTO reintentar(@PathVariable @Parameter(description = "ID de la factura") Long id) {
        return facturaService.reintentar(id);
    }
}
