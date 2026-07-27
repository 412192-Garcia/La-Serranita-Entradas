package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.service.CompraService;
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
}
