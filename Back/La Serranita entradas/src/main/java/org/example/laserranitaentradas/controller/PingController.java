package org.example.laserranitaentradas.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api")
@Tag(name = "Health Check", description = "Operaciones para verificar el estado de la API")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Verificar estado de la API", description = "Verifica que la API esté activa y funcionando correctamente")
    @ApiResponse(responseCode = "200", description = "API activa y funcionando")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("¡Pong! La API está viva 🎉");
    }

}
