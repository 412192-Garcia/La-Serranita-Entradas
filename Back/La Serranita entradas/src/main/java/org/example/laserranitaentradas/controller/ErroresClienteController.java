package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.monitoreo.IncidenteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Errores de JavaScript que no maneja ninguna pantalla (los manda GlobalErrorHandler del front):
 * quedan en Sistema > Errores con área FRONT, igual que los del servidor. Sin esto, un bug en el POS
 * o en la tienda sólo quedaba en la consola del navegador de quien lo sufrió.
 *
 * Es público (la tienda no tiene login), así que acepta pocos por IP: un navegador en un bucle de
 * errores o alguien mandando basura no llena la tabla.
 */
@RestController
@RequestMapping("/api/errores-cliente")
@Tag(name = "Errores del navegador", description = "Errores de JavaScript reportados por la app")
public class ErroresClienteController {

    private static final int MAX_POR_IP = 20;

    public record ErrorCliente(String mensaje, String stack, String pantalla, String navegador) {}

    private final IncidenteService incidenteService;
    /** Errores recibidos por IP en la ventana actual (se vacía cada 10 min). */
    private final Map<String, AtomicInteger> porIp = new ConcurrentHashMap<>();

    public ErroresClienteController(IncidenteService incidenteService) {
        this.incidenteService = incidenteService;
    }

    @PostMapping
    @Operation(summary = "Reportar un error de JavaScript", description = "Máximo " + MAX_POR_IP + " cada 10 minutos por IP.")
    public ResponseEntity<Void> reportar(@RequestBody ErrorCliente error, HttpServletRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (error == null || error.mensaje() == null || error.mensaje().isBlank()) return ResponseEntity.badRequest().build();
        if (porIp.size() > 10_000 || porIp.computeIfAbsent(request.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet() > MAX_POR_IP) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        incidenteService.registrarDelNavegador(error.mensaje(), recortar(error.stack(), 6_000), error.pantalla(),
                recortar(error.navegador(), 300), operador != null ? operador.username() : null);
        return ResponseEntity.accepted().build();
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void limpiar() {
        porIp.clear();
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() > max ? texto.substring(0, max) : texto;
    }
}
