package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.AbrirCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CerrarCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaRequestDTO;
import org.example.laserranitaentradas.service.CajaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
    @Operation(summary = "Abrir caja", description = "Declara el efectivo inicial y habilita a cobrar")
    public ResponseEntity<CajaResponseDTO> abrir(@RequestBody AbrirCajaRequestDTO request,
                                                  @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.abrir(operador.id(), request.getMontoInicial()));
    }

    @PostMapping("/retiros")
    @Operation(summary = "Registrar un retiro de efectivo de la caja abierta")
    public ResponseEntity<CajaResponseDTO> registrarRetiro(@RequestBody RetiroCajaRequestDTO request,
                                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.ok(cajaService.registrarRetiro(operador.id(), request.getMonto(), request.getMotivo()));
    }

    @PostMapping("/cerrar")
    @Operation(summary = "Cerrar caja", description = "Compara el efectivo contado contra el esperado y cierra el turno")
    public ResponseEntity<CajaResponseDTO> cerrar(@RequestBody CerrarCajaRequestDTO request,
                                                   @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.ok(cajaService.cerrar(operador.id(), request.getMontoContado()));
    }
}
