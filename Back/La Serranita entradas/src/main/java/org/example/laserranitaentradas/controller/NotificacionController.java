package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.MarcarVistasRequestDTO;
import org.example.laserranitaentradas.model.entity.TipoNotificacion;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.NotificacionService;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.EnumMap;
import java.util.Map;

/** Avisos genéricos (punto rojo en la cabecera interna) del usuario logueado. ADMIN-only por
 * ahora (gateado en SecurityConfig): los dos tipos existentes (CAJA_ATRASADA, RECHAZO_OPERACION)
 * sólo le importan al admin. Si en el futuro se agrega un tipo para BOLETERO, aflojar esa regla. */
@RestController
@RequestMapping("/api/interno/notificaciones")
@Tag(name = "Notificaciones", description = "Avisos genéricos (cajas atrasadas, rechazos pendientes, etc.) del usuario logueado")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final CajaService cajaService;
    private final RechazoOperacionService rechazoService;

    public NotificacionController(NotificacionService notificacionService, CajaService cajaService,
                                   RechazoOperacionService rechazoService) {
        this.notificacionService = notificacionService;
        this.cajaService = cajaService;
        this.rechazoService = rechazoService;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Qué tipos de aviso tienen pendientes para el usuario logueado ahora mismo")
    public ResponseEntity<Map<TipoNotificacion, Boolean>> resumen(@AuthenticationPrincipal UsuarioAutenticado operador) {
        Map<TipoNotificacion, Boolean> resultado = new EnumMap<>(TipoNotificacion.class);
        resultado.put(TipoNotificacion.CAJA_ATRASADA,
                notificacionService.hayPendientes(TipoNotificacion.CAJA_ATRASADA, cajaService.getIdsCajasAtrasadas(), operador.id()));
        resultado.put(TipoNotificacion.RECHAZO_OPERACION,
                notificacionService.hayPendientes(TipoNotificacion.RECHAZO_OPERACION, rechazoService.getIdsPendientes(), operador.id()));
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/{tipo}/marcar-vistas")
    @Operation(summary = "Marcar estos ids como vistos por el usuario logueado, para este tipo de aviso",
            description = "Sólo tiene efecto real en tipos con desapareceAlVerse=true (ej. CAJA_ATRASADA); en los demás no hace nada.")
    public ResponseEntity<Void> marcarVistas(@PathVariable TipoNotificacion tipo,
                                              @RequestBody MarcarVistasRequestDTO request,
                                              @AuthenticationPrincipal UsuarioAutenticado operador) {
        notificacionService.marcarVistas(tipo, request.getRefIds(), operador.id());
        return ResponseEntity.noContent().build();
    }
}
