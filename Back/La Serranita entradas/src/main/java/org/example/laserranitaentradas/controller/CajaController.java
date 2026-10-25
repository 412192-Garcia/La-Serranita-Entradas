package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.AbrirCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CerrarCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaDetalleAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajasCerradasResponseDTO;
import org.example.laserranitaentradas.model.dto.CorreccionCajaRequestDTO;
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
    @Operation(summary = "Abrir caja", description = "Efectivo inicial y entradas físicas con las que arranca")
    public ResponseEntity<CajaResponseDTO> abrir(@RequestBody AbrirCajaRequestDTO request,
                                                  @AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cajaService.abrir(operador.id(), request.getMontoInicial(), request.getEntradasFisicasInicial()));
    }

    @PostMapping("/abrir-sin-control")
    @Operation(summary = "Vender sin abrir caja (ADMIN)", description = "Caja sin conteos de apertura ni de cierre. Se cierra sola a fin de día.")
    public ResponseEntity<CajaResponseDTO> abrirSinControl(@AuthenticationPrincipal UsuarioAutenticado operador) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.abrirSinControl(operador.id()));
    }

    @PostMapping("/retiros")
    @Operation(summary = "Registrar un retiro o aporte de efectivo en la caja abierta")
    public ResponseEntity<CajaResponseDTO> registrarRetiro(@RequestBody RetiroCajaRequestDTO request,
                                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        try {
            return ResponseEntity.ok(cajaService.registrarRetiro(operador.id(), request.getMonto(), request.getMotivo(),
                    request.getTipo(), request.getIdempotencyKey(), request.getFechaOriginal()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Sólo se registra si pasó en un reintento en segundo plano: en vivo lo ve quien lo cargó.
            if (Boolean.TRUE.equals(request.getEsReintentoEncolado())) {
                rechazoService.registrar("RETIRO_APORTE", request, ex.getMessage(), request.getIdempotencyKey());
            }
            throw ex;
        }
    }

    @PostMapping("/ingresos-entradas")
    @Operation(summary = "Registrar un ingreso o retiro de entradas físicas en la caja abierta", description = "Ingreso: le traen más talonario. Retiro: le sacan entradas.")
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
    @Operation(summary = "Cerrar caja (ADMIN)", description = "Compara lo contado (efectivo, posnet, entradas) con lo esperado y cierra el turno.")
    public ResponseEntity<CajaResponseDTO> cerrar(@PathVariable Long id, @RequestBody CerrarCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.cerrarComoAdmin(id, request.getConteoEfectivo(), request.getCierresPosnet(),
                request.getEntradasFisicasRestantes(), request.getCambioContado(), request.getDolaresContado()));
    }

    @PostMapping("/{id}/cerrar-sin-control")
    @Operation(summary = "Cerrar una caja \"sin control\" (ADMIN)", description = "Para las cajas abiertas con \"vender sin control de caja\": las cierra ya mismo sin pedir ningún conteo (lo mismo que hace solo el job de medianoche), en vez de esperar. Sólo aplica a cajas con controlOmitido=true.")
    public ResponseEntity<CajaResponseDTO> cerrarSinControl(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.cerrarSinControlPorId(id));
    }

    @PostMapping("/{id}/retiros")
    @Operation(summary = "Registrar un retiro o aporte en la caja de otro usuario (ADMIN)", description = "Para cargar un movimiento que faltó.")
    public ResponseEntity<CajaResponseDTO> registrarRetiroComoAdmin(@PathVariable Long id, @RequestBody RetiroCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.registrarRetiroComoAdmin(id, request.getMonto(), request.getMotivo(), request.getTipo()));
    }

    @PostMapping("/{id}/ingresos-entradas")
    @Operation(summary = "Registrar un ingreso o retiro de entradas físicas en la caja de otro usuario (ADMIN)", description = "Como /{id}/retiros, para el talonario.")
    public ResponseEntity<CajaResponseDTO> registrarIngresoEntradasComoAdmin(@PathVariable Long id, @RequestBody IngresoEntradasRequestDTO request) {
        return ResponseEntity.ok(cajaService.registrarIngresoEntradasComoAdmin(id, request.getCantidad(), request.getMotivo(), request.getTipo()));
    }

    @GetMapping("/abiertas")
    @Operation(summary = "Cajas abiertas ahora mismo (ADMIN)", description = "Todas las cajas en curso")
    public ResponseEntity<List<CajaAbiertaDTO>> getCajasAbiertas() {
        return ResponseEntity.ok(cajaService.getCajasAbiertas());
    }

    @GetMapping("/cerradas")
    @Operation(summary = "Cajas cerradas paginadas (ADMIN)",
            description = "Para el listado de \"Cajas cerradas\" en la pantalla de Cajas: sin filtro de fechas, pagina en la base sobre todas las cajas cerradas, así que soporta boleteros con meses de turnos sin traer todo a memoria. ordenarPor admite cualquier campo salvo totalRetiros (no es una columna propia de Caja).")
    public ResponseEntity<CajasCerradasResponseDTO> getCajasCerradas(
            @RequestParam(required = false) String usuarioNombre,
            @RequestParam(defaultValue = "fechaApertura") String ordenarPor,
            @RequestParam(defaultValue = "DESC") String direccion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cajaService.getCajasCerradas(usuarioNombre, ordenarPor, direccion, page, size));
    }

    @GetMapping("/cerradas/boleteros")
    @Operation(summary = "Boleteros con cajas cerradas (ADMIN)",
            description = "Boleteros con alguna caja cerrada habilitada, para el filtro de \"Cajas cerradas\", que ya no depende de un rango de fechas.")
    public ResponseEntity<List<String>> getBoleterosConCajasCerradas() {
        return ResponseEntity.ok(cajaService.getBoleterosConCajasCerradas());
    }

    @GetMapping("/{id}/detalle")
    @Operation(summary = "Detalle completo de una caja (ADMIN)", description = "Cualquier caja cerrada")
    public ResponseEntity<CajaResponseDTO> getDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.getDetalle(id));
    }

    @GetMapping("/{id}/operaciones")
    @Operation(summary = "Detalle de una caja con la caja todavía abierta (ADMIN)", description = "Operaciones en orden y resumen de lo vendido. Funciona con la caja abierta.")
    public ResponseEntity<CajaDetalleAbiertaDTO> getOperaciones(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.getOperacionesCaja(id));
    }

    @PutMapping("/{id}/correccion")
    @Operation(summary = "Corregir una caja cerrada (ADMIN)",
            description = "Recuento y traspasos entre formas de pago en una transacción. Los traspasos quedan como AjusteCaja, sin tocar las compras.")
    public ResponseEntity<CajaResponseDTO> corregirCaja(@PathVariable Long id, @RequestBody CorreccionCajaRequestDTO request) {
        return ResponseEntity.ok(cajaService.corregirCaja(id, request.getConteoEfectivo(),
                request.getCierresPosnet(), request.getEntradasFisicasRestantes(), request.getCambioContado(),
                request.getDolaresContado(), request.getAjustes()));
    }

    @PostMapping("/{id}/deshabilitar")
    @Operation(summary = "Deshabilitar una caja cerrada (ADMIN)",
            description = "Sólo cajas cerradas. Sale de listados, KPIs y reporte (habilitada=false). Irreversible desde la app.")
    public ResponseEntity<CajaResponseDTO> deshabilitar(@PathVariable Long id) {
        return ResponseEntity.ok(cajaService.deshabilitarCaja(id));
    }

    @DeleteMapping("/{id}/ajustes/{ajusteId}")
    @Operation(summary = "Deshacer un ajuste de repartición por forma de pago (ADMIN)", description = "Borra el ajuste y vuelve a recalcular el cierre sin él.")
    public ResponseEntity<CajaResponseDTO> eliminarAjuste(@PathVariable Long id, @PathVariable Long ajusteId) {
        return ResponseEntity.ok(cajaService.eliminarAjuste(id, ajusteId));
    }
}
