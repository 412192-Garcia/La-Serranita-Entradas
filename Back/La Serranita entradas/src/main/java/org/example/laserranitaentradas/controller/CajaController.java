package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.AbrirCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CerrarCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaDetalleAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajasCerradasResponseDTO;
import org.example.laserranitaentradas.model.dto.IngresoEntradasRequestDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaRequestDTO;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/interno/caja")
@Tag(name = "Caja", description = "Apertura, retiros y cierre de la caja de cada boletero")
public class CajaController {

    private final CajaService cajaService;
    private final RechazoOperacionService rechazoService;

    public CajaController(CajaService cajaService, RechazoOperacionService rechazoService) {
        this.cajaService = cajaService;
        this.rechazoService = rechazoService;
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
        try {
            return ResponseEntity.ok(cajaService.registrarRetiro(operador.id(), request.getMonto(), request.getMotivo(),
                    request.getTipo(), request.getIdempotencyKey(), request.getFechaOriginal()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Sólo interesa registrarlo para un admin si pasó en un reintento en segundo plano de
            // la cola offline: un rechazo en el primer intento en vivo ya lo ve y lo corrige la
            // persona que lo tipeó ahí mismo (ver el campo en el DTO).
            if (Boolean.TRUE.equals(request.getEsReintentoEncolado())) {
                rechazoService.registrar("RETIRO_APORTE", request, ex.getMessage(), request.getIdempotencyKey());
            }
            throw ex;
        }
    }

    @PostMapping("/ingresos-entradas")
    @Operation(summary = "Registrar un ingreso o retiro de entradas físicas en la caja abierta", description = "Ingreso: para cuando el boletero se queda sin talonario y le traen más a mitad de turno. Retiro: para sacarle entradas y dárselas a otro boletero.")
    public ResponseEntity<CajaResponseDTO> registrarIngresoEntradas(@RequestBody IngresoEntradasRequestDTO request,
                                                                      @AuthenticationPrincipal UsuarioAutenticado operador) {
        try {
            return ResponseEntity.ok(cajaService.registrarIngresoEntradas(operador.id(), request.getCantidad(),
                    request.getMotivo(), request.getTipo(), request.getIdempotencyKey(), request.getFechaOriginal()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            if (Boolean.TRUE.equals(request.getEsReintentoEncolado())) {
                rechazoService.registrar("INGRESO_ENTRADAS", request, ex.getMessage(), request.getIdempotencyKey());
            }
            throw ex;
        }
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

    @PostMapping("/{id}/ingresos-entradas")
    @Operation(summary = "Registrar un ingreso o retiro de entradas físicas en la caja de otro usuario (ADMIN)", description = "Mismo caso de uso que /{id}/retiros pero para el talonario físico.")
    public ResponseEntity<CajaResponseDTO> registrarIngresoEntradasComoAdmin(@PathVariable Long id, @RequestBody IngresoEntradasRequestDTO request) {
        return ResponseEntity.ok(cajaService.registrarIngresoEntradasComoAdmin(id, request.getCantidad(), request.getMotivo(), request.getTipo()));
    }

    @GetMapping("/abiertas")
    @Operation(summary = "Cajas abiertas ahora mismo (ADMIN)", description = "Todas las cajas en curso, sin importar de qué boletero — para el dashboard de hoy")
    public ResponseEntity<List<CajaAbiertaDTO>> getCajasAbiertas() {
        return ResponseEntity.ok(cajaService.getCajasAbiertas());
    }

    @GetMapping("/cerradas")
    @Operation(summary = "Cajas cerradas paginadas (ADMIN)",
            description = "Para el listado de \"Cajas cerradas\" en la pantalla de Cajas: pagina en la base, así que soporta boleteros con meses de turnos sin traer todo a memoria. Los totales de retiros/faltantes/sobrantes son de todo lo que matchea el filtro, no sólo la página. ordenarPor admite cualquier campo salvo totalRetiros (no es una columna propia de Caja).")
    public ResponseEntity<CajasCerradasResponseDTO> getCajasCerradas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String usuarioNombre,
            @RequestParam(defaultValue = "fechaCierre") String ordenarPor,
            @RequestParam(defaultValue = "DESC") String direccion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cajaService.getCajasCerradas(desde, hasta, usuarioNombre, ordenarPor, direccion, page, size));
    }

    @GetMapping("/{id}/detalle")
    @Operation(summary = "Detalle completo de una caja (ADMIN)", description = "Para que el admin revise cualquier caja cerrada, sin importar quién la abrió")
    public ResponseEntity<CajaResponseDTO> getDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.getDetalle(id));
    }

    @GetMapping("/{id}/operaciones")
    @Operation(summary = "Detalle de una caja con la caja todavía abierta (ADMIN)", description = "Ventas/retiros/ingresos en orden cronológico más un resumen de lo vendido (por forma de pago y por tipo de entrada). A diferencia de /detalle, funciona sin esperar al cierre: para que el admin revise y corrija una venta mal cargada mientras el boletero sigue trabajando.")
    public ResponseEntity<CajaDetalleAbiertaDTO> getOperaciones(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.getOperacionesCaja(id));
    }

    @PutMapping("/{id}/cierre")
    @Operation(summary = "Corregir un cierre ya hecho (ADMIN)", description = "Para arreglar un error de tipeo en el conteo sin tener que meter la mano en la base. Sólo un ADMIN puede corregir, cualquier caja cerrada sin importar quién la abrió.")
    public ResponseEntity<CajaResponseDTO> corregirCierre(@PathVariable Long id, @RequestBody CerrarCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.corregirCierre(id, request.getConteoEfectivo(),
                request.getCierresPosnet(), request.getEntradasFisicasCortadas(), request.getCambioContado(), request.getDolaresContado()));
    }
}
