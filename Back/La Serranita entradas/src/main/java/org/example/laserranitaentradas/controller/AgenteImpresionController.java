package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.example.laserranitaentradas.service.impresion.ImpresionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lo que usa el agente de impresión de la PC de la entrada. No lleva JWT (no es una persona, y
 * tiene que reconectarse solo durante meses): se autentica con un token fijo compartido,
 * IMPRESION_AGENTE_TOKEN. Sin ese token configurado, estos endpoints quedan cerrados.
 */
@RestController
@RequestMapping("/api/impresion/agente")
@Tag(name = "Agente de impresión", description = "Conexión del agente que imprime en la ticketera de la boletería")
public class AgenteImpresionController {

    private final ImpresionService impresionService;

    @Value("${impresion.agente.token:}")
    private String tokenEsperado;

    public AgenteImpresionController(ImpresionService impresionService) {
        this.impresionService = impresionService;
    }

    @GetMapping(value = "/conectar", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Conexión SSE del agente", description = "Queda abierta: por acá le llegan los tickets a imprimir (evento 'trabajo').")
    public SseEmitter conectar(@RequestHeader(value = "X-Agente-Token", required = false) String token,
                               @RequestParam String agente,
                               @RequestParam String impresoras,
                               HttpServletResponse response) {
        validarToken(token);
        Set<String> nombres = new LinkedHashSet<>();
        Arrays.stream(impresoras.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(nombres::add);
        if (nombres.isEmpty() || nombres.stream().anyMatch(n -> n.contains(";") || n.length() > 60)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombres de impresora inválidos");
        }
        // Que nginx no retenga los eventos en su buffer: sin esto el agente recibe los tickets
        // tarde (cuando se llena el buffer) o nunca.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return impresionService.conectarAgente(agente.trim(), nombres);
    }

    @PostMapping(value = "/estado", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "El agente informa si cada ticketera responde",
            description = "Una línea por ticketera: nombre<TAB>1|0<TAB>detalle. Lo usa la tablet para avisar 'ticketera apagada' antes de cobrar.")
    public ResponseEntity<Void> estado(@RequestHeader(value = "X-Agente-Token", required = false) String token,
                                       @RequestParam String agente,
                                       @RequestBody(required = false) String cuerpo) {
        validarToken(token);
        Map<String, ImpresionService.EstadoImpresora> estados = new LinkedHashMap<>();
        if (cuerpo != null) {
            for (String linea : cuerpo.split("\n")) {
                String[] partes = linea.split("\t", 3);
                if (partes.length < 2 || partes[0].isBlank()) continue;
                String detalle = partes.length == 3 && !partes[2].isBlank() ? partes[2].trim() : null;
                if (detalle != null && detalle.length() > 200) detalle = detalle.substring(0, 200);
                estados.put(partes[0].trim(), new ImpresionService.EstadoImpresora("1".equals(partes[1].trim()), detalle));
            }
        }
        impresionService.actualizarEstados(agente.trim(), estados);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/trabajos/{id}/resultado")
    @Operation(summary = "El agente confirma si imprimió un trabajo")
    public ResponseEntity<Void> resultado(@RequestHeader(value = "X-Agente-Token", required = false) String token,
                                          @PathVariable Long id,
                                          @RequestParam boolean ok,
                                          @RequestParam(required = false) String error) {
        validarToken(token);
        impresionService.registrarResultado(id, ok, error);
        return ResponseEntity.ok().build();
    }

    private void validarToken(String token) {
        boolean valido = tokenEsperado != null && !tokenEsperado.isBlank() && token != null
                && MessageDigest.isEqual(tokenEsperado.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de agente inválido");
        }
    }
}
