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

/** Cobro con Point desde el POS y configuración de los Points. */
@RestController
@Tag(name = "Point", description = "Cobros con tarjeta en el Point de Mercado Pago")
public class PointController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PointController.class);

    private final CobroPointService service;

    public PointController(CobroPointService service) {
        this.service = service;
    }

    @GetMapping("/api/interno/point/terminales")
    @Operation(summary = "Points habilitados para las tablets", description = "Para el selector de dispositivos de cada tablet: sólo los activados en Configuración > Points.")
    public List<TerminalDTO> terminales() {
        return service.terminalesHabilitados();
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

    @GetMapping("/api/interno/point/terminales/actualizadas")
    @Operation(summary = "Points de la cuenta, recién consultados a Mercado Pago (ADMIN, Configuración > Points)")
    public List<TerminalDTO> terminalesActualizadas() {
        return service.terminales(true);
    }

    @PutMapping("/api/interno/point/terminales/{id}/nombre")
    @Operation(summary = "Ponerle nombre a un Point (ADMIN, Configuración > Points)", description = "Vacío = vuelve al nombre por defecto.")
    public ResponseEntity<Void> renombrar(@PathVariable String id, @RequestBody Map<String, String> body) {
        service.renombrar(id, body.get("nombre"));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/interno/point/terminales/{id}/habilitado")
    @Operation(summary = "Habilitar o no un Point para las tablets (ADMIN, Configuración > Points)")
    public ResponseEntity<Void> habilitar(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        Boolean habilitado = body.get("habilitado");
        if (habilitado == null) throw new IllegalArgumentException("Falta indicar si queda habilitado");
        service.habilitar(id, habilitado);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/interno/point/terminales/{id}/modo")
    @Operation(summary = "Pasar un Point a integrado (PDV) o a manual (STANDALONE) (ADMIN)",
            description = "Si se corta internet, se pasa a manual para seguir cobrando.")
    public ResponseEntity<Void> cambiarModo(@PathVariable String id, @RequestBody Map<String, String> body) {
        service.cambiarModo(id, body.get("modo"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/interno/point/caja/{cajaId}/resumen")
    @Operation(summary = "Lo cobrado con el Point en una caja (ADMIN)", description = "Para precargar el cierre de posnet y avisar de cobros sin venta.")
    public CobroPointService.ResumenCajaPoint resumenCaja(@PathVariable Long cajaId) {
        return service.resumenCaja(cajaId);
    }

    /** Mercado Pago caído o token mal: no va a Sistema > Errores, alcanza con el log. */
    @ExceptionHandler(PointException.class)
    public ResponseEntity<String> errorDeMercadoPago(PointException e) {
        log.warn("Mercado Pago (Point): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
    }
}
