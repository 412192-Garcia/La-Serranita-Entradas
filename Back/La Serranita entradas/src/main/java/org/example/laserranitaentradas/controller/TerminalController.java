package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.monitoreo.TerminalesService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Latido de las terminales del POS (BOLETERO o ADMIN): cuántas operaciones tienen sin subir. */
@RestController
@RequestMapping("/api/interno/terminales")
@Tag(name = "Terminales POS", description = "Estado de la cola offline de cada terminal, para Sistema > Estado")
public class TerminalController {

    private final TerminalesService terminalesService;

    public TerminalController(TerminalesService terminalesService) {
        this.terminalesService = terminalesService;
    }

    @PostMapping("/latido")
    @Operation(summary = "Latido de una terminal del POS", description = "Lo manda el POS cada minuto, con conexión.")
    public ResponseEntity<Void> latido(@RequestBody TerminalesService.Latido latido, @AuthenticationPrincipal UsuarioAutenticado operador) {
        terminalesService.registrar(latido, operador != null ? operador.username() : null);
        return ResponseEntity.noContent().build();
    }
}
