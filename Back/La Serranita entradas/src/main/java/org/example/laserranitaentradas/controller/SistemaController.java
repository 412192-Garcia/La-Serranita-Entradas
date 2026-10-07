package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.EstadoSistemaDTO;
import org.example.laserranitaentradas.model.entity.Incidente;
import org.example.laserranitaentradas.model.entity.RegistroAuditoria;
import org.example.laserranitaentradas.monitoreo.AuditoriaService;
import org.example.laserranitaentradas.monitoreo.EstadoSistemaService;
import org.example.laserranitaentradas.monitoreo.IncidenteService;
import org.example.laserranitaentradas.monitoreo.MetricasRequests;
import org.example.laserranitaentradas.monitoreo.PedidoBackup;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sistema (SUPERADMIN): estado de cada área, historial de acciones, errores y rendimiento. */
@RestController
@RequestMapping("/api/interno/sistema")
@Tag(name = "Sistema", description = "Monitoreo y trazabilidad: estado, historial de acciones y errores")
public class SistemaController {

    private final EstadoSistemaService estadoService;
    private final AuditoriaService auditoriaService;
    private final IncidenteService incidenteService;
    private final MetricasRequests metricasRequests;
    private final PedidoBackup pedidoBackup;

    public SistemaController(EstadoSistemaService estadoService, AuditoriaService auditoriaService, IncidenteService incidenteService,
                             MetricasRequests metricasRequests, PedidoBackup pedidoBackup) {
        this.estadoService = estadoService;
        this.auditoriaService = auditoriaService;
        this.incidenteService = incidenteService;
        this.metricasRequests = metricasRequests;
        this.pedidoBackup = pedidoBackup;
    }

    @GetMapping("/estado")
    @Operation(summary = "Estado de cada área (facturación, pagos, backups, mails, impresión, cajas, seguridad, errores, servidor)")
    public EstadoSistemaDTO estado() {
        return estadoService.estado();
    }

    @GetMapping("/auditoria")
    @Operation(summary = "Historial de acciones", description = "Por defecto, los últimos 7 días. Filtros opcionales.")
    public Page<RegistroAuditoria> auditoria(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                             @RequestParam(required = false) String usuario,
                                             @RequestParam(required = false) String accion,
                                             @RequestParam(required = false) String texto,
                                             @RequestParam(defaultValue = "0") int pagina,
                                             @RequestParam(defaultValue = "50") int tamanio) {
        return auditoriaService.buscar(desde, hasta, usuario, accion, texto, pagina, tamanio);
    }

    @GetMapping("/auditoria/filtros")
    @Operation(summary = "Acciones y usuarios que aparecen en el historial (para los filtros)")
    public Map<String, List<String>> filtrosAuditoria() {
        return Map.of("acciones", auditoriaService.acciones(), "usuarios", auditoriaService.usuarios());
    }

    @GetMapping("/incidentes")
    @Operation(summary = "Errores del servidor", description = "filtro: pendientes (por defecto), resueltos o todos; area opcional.")
    public Page<Incidente> incidentes(@RequestParam(defaultValue = "pendientes") String filtro,
                                      @RequestParam(required = false) String area,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "30") int tamanio) {
        return incidenteService.listar(filtro, area, pagina, tamanio);
    }

    @PostMapping("/backups/forzar")
    @Operation(summary = "Pedir un backup de la base ahora", description = "Lo hace el contenedor backup en menos de un minuto (con la copia a Drive, si está configurada).")
    public PedidoBackup.Estado forzarBackup(@AuthenticationPrincipal UsuarioAutenticado operador) {
        return pedidoBackup.pedir(operador != null ? operador.username() : null);
    }

    @GetMapping("/rendimiento")
    @Operation(summary = "Tiempos de respuesta por endpoint y últimas requests lentas", description = "Desde que arrancó el servidor (en memoria).")
    public MetricasRequests.Resumen rendimiento() {
        return metricasRequests.resumen();
    }

    @PostMapping("/incidentes/{id}/resolver")
    @Operation(summary = "Marcar resuelto un error del servidor")
    public Incidente resolver(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado operador) {
        return incidenteService.resolver(id, operador != null ? operador.username() : null);
    }
}
