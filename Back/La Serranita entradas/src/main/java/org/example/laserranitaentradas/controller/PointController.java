package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.service.point.CobroPointService;
import org.example.laserranitaentradas.service.point.CobroPointService.CobroPointDTO;
import org.example.laserranitaentradas.service.point.CobroPointService.CrearCobroRequest;
import org.example.laserranitaentradas.service.point.CobroPointService.TerminalDTO;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient.PointException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Cobro con el Point de Mercado Pago desde el POS (BOLETERO o ADMIN), y la configuración de los
 * Points desde Sistema > Módulos (SUPERADMIN, por la ruta /api/interno/sistema/**).
 */
@RestController
@Tag(name = "Point", description = "Cobros con tarjeta en el Point de Mercado Pago")
public class PointController {

    private final CobroPointService service;

    public PointController(CobroPointService service) {
        this.service = service;
    }

    @GetMapping("/api/interno/point/terminales")
    @Operation(summary = "Points de la cuenta de Mercado Pago", description = "Para el selector de dispositivos de cada tablet.")
    public List<TerminalDTO> terminales() {
        return service.terminales(false);
    }

    @PostMapping("/api/interno/point/cobros")
    @Operation(summary = "Mandar un cobro al Point", description = "Crea la orden en Mercado Pago. Con la misma clave devuelve el mismo cobro.")
    public CobroPointDTO crear(@RequestBody CrearCobroRequest pedido, @AuthenticationPrincipal UsuarioAutenticado operador) {
        return service.crear(pedido, operador);
    }

    @GetMapping("/api/interno/point/cobros/{id}")
    @Operation(summary = "Estado de un cobro con Point", description = "El POS lo consulta mientras espera la tarjeta.")
    public CobroPointDTO consultar(@PathVariable @Parameter(description = "ID del cobro") Long id,
                                   @AuthenticationPrincipal UsuarioAutenticado operador) {
        return service.consultar(id, operador);
    }

    @PostMapping("/api/interno/point/cobros/{id}/cancelar")
    @Operation(summary = "Cancelar un cobro que el Point todavía no cobró")
    public CobroPointDTO cancelar(@PathVariable @Parameter(description = "ID del cobro") Long id,
                                  @AuthenticationPrincipal UsuarioAutenticado operador) {
        return service.cancelar(id, operador);
    }

    @PostMapping("/api/interno/point/cobros/{id}/devolver")
    @Operation(summary = "Devolver un cobro aprobado que no terminó en venta")
    public CobroPointDTO devolver(@PathVariable @Parameter(description = "ID del cobro") Long id,
                                  @AuthenticationPrincipal UsuarioAutenticado operador) {
        return service.devolver(id, operador);
    }

    @GetMapping("/api/interno/sistema/point/terminales")
    @Operation(summary = "Points de la cuenta, recién consultados (SUPERADMIN)")
    public List<TerminalDTO> terminalesActualizadas() {
        return service.terminales(true);
    }

    @PutMapping("/api/interno/sistema/point/terminales/{id}/nombre")
    @Operation(summary = "Ponerle nombre a un Point (SUPERADMIN)", description = "Vacío = vuelve al nombre por defecto.")
    public ResponseEntity<Void> renombrar(@PathVariable String id, @RequestBody Map<String, String> body) {
        service.renombrar(id, body.get("nombre"));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/interno/point/terminales/{id}/modo")
    @Operation(summary = "Pasar un Point a integrado (PDV) o a manual (STANDALONE)",
            description = "También el boletero: si se corta internet en la boletería, pasa el Point a manual (por ejemplo desde el celular) para seguir cobrando.")
    public ResponseEntity<Void> cambiarModo(@PathVariable String id, @RequestBody Map<String, String> body) {
        service.cambiarModo(id, body.get("modo"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/interno/point/caja/{cajaId}/resumen")
    @Operation(summary = "Lo cobrado con el Point en una caja (ADMIN)", description = "Para precargar el cierre de posnet y avisar de cobros sin venta.")
    public CobroPointService.ResumenCajaPoint resumenCaja(@PathVariable Long cajaId) {
        return service.resumenCaja(cajaId);
    }

    /** Mercado Pago caído o el token mal: es un problema del servidor, no del pedido. */
    @ExceptionHandler(PointException.class)
    public ResponseEntity<String> errorDeMercadoPago(PointException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }
}
