package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.service.CompraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> confirmarPagoEfectivo(@PathVariable @Parameter(description = "ID de la compra") Long id,
                                                    @RequestBody @Parameter(description = "ID del usuario/boletero que confirma el cobro") Long usuarioId) {
        try {
            Compra compra = compraService.confirmarPagoEfectivo(id, usuarioId);
            CompraResponseDTO dto = CompraController.entityToDto(compra);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}
