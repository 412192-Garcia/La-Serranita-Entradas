package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.AbrirCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CerrarCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.IngresoEntradasRequestDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaRequestDTO;
import org.example.laserranitaentradas.service.CajaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/interno/caja")
@Tag(name = "Caja", description = "Apertura, retiros y cierre de la caja de cada boletero")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping("/actual")
    @Operation(summary = "Caja abierta del boletero autenticado", description = "Null si no tiene ninguna caja en curso")
    public ResponseEntity<CajaResponseDTO> getActual(@AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.ok(cajaService.getActual(operador.id()));
    }

    @PostMapping("/abrir")
    @Operation(summary = "Abrir caja", description = "Declara el efectivo inicial y con cuántas entradas físicas arranca, y habilita a cobrar")
    public ResponseEntity<CajaResponseDTO> abrir(@RequestBody AbrirCajaRequestDTO request,
                                                  @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cajaService.abrir(operador.id(), request.getMontoInicial(), request.getEntradasFisicasInicial()));
    }

    @PostMapping("/retiros")
    @Operation(summary = "Registrar un retiro o aporte de efectivo en la caja abierta")
    public ResponseEntity<CajaResponseDTO> registrarRetiro(@RequestBody RetiroCajaRequestDTO request,
                                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.ok(cajaService.registrarRetiro(operador.id(), request.getMonto(), request.getMotivo(),
                request.getTipo(), request.getIdempotencyKey(), request.getFechaOriginal()));
    }

    @PostMapping("/ingresos-entradas")
    @Operation(summary = "Registrar un ingreso de entradas físicas a la caja abierta", description = "Para cuando el boletero se queda sin talonario y le traen más a mitad de turno")
    public ResponseEntity<CajaResponseDTO> registrarIngresoEntradas(@RequestBody IngresoEntradasRequestDTO request,
                                                                      @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.ok(cajaService.registrarIngresoEntradas(operador.id(), request.getCantidad(),
                request.getIdempotencyKey(), request.getFechaOriginal()));
    }

    @PostMapping("/{id}/cerrar")
    @Operation(summary = "Cerrar caja (ADMIN)", description = "Compara lo contado (efectivo por denominación, cierres de posnet, entradas físicas) contra lo esperado y cierra el turno. Sólo un ADMIN puede cerrar cajas, sin importar de qué boletero — el cierre ya no es self-service.")
    public ResponseEntity<CajaResponseDTO> cerrar(@PathVariable Long id, @RequestBody CerrarCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.cerrarComoAdmin(id, request.getConteoEfectivo(), request.getCierresPosnet(),
                request.getEntradasFisicasCortadas(), request.getCambioContado(), request.getDolaresContado()));
    }

    @PostMapping("/{id}/retiros")
    @Operation(summary = "Registrar un retiro o aporte en la caja de otro usuario (ADMIN)", description = "Para cuando el admin está cerrando la caja de un boletero y nota que falta cargar un movimiento.")
    public ResponseEntity<CajaResponseDTO> registrarRetiroComoAdmin(@PathVariable Long id, @RequestBody RetiroCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.registrarRetiroComoAdmin(id, request.getMonto(), request.getMotivo(), request.getTipo()));
    }

    @GetMapping("/abiertas")
    @Operation(summary = "Cajas abiertas ahora mismo (ADMIN)", description = "Todas las cajas en curso, sin importar de qué boletero — para el dashboard de hoy")
    public ResponseEntity<List<CajaAbiertaDTO>> getCajasAbiertas() {
        return ResponseEntity.ok(cajaService.getCajasAbiertas());
    }

    @GetMapping("/{id}/detalle")
    @Operation(summary = "Detalle completo de una caja (ADMIN)", description = "Para que el admin revise cualquier caja cerrada, sin importar quién la abrió")
    public ResponseEntity<CajaResponseDTO> getDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.getDetalle(id));
    }

    @PutMapping("/{id}/cierre")
    @Operation(summary = "Corregir un cierre ya hecho (ADMIN)", description = "Para arreglar un error de tipeo en el conteo sin tener que meter la mano en la base. Sólo un ADMIN puede corregir, cualquier caja cerrada sin importar quién la abrió.")
    public ResponseEntity<CajaResponseDTO> corregirCierre(@PathVariable Long id, @RequestBody CerrarCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.corregirCierre(id, request.getConteoEfectivo(),
                request.getCierresPosnet(), request.getEntradasFisicasCortadas(), request.getCambioContado(), request.getDolaresContado()));
    }
}
