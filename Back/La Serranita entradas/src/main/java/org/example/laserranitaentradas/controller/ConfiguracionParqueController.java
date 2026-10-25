package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.FacturaPredeterminadaDTO;
import org.example.laserranitaentradas.model.dto.HorarioRequest;
import org.example.laserranitaentradas.model.dto.HorarioResponseDTO;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/configuracion")
@Tag(name = "Configuración del Parque", description = "Operaciones para gestionar el horario general del parque")
public class ConfiguracionParqueController {

    private final ConfiguracionParqueService configuracionParqueService;
    private final DiaAperturaService diaAperturaService;

    public ConfiguracionParqueController(ConfiguracionParqueService configuracionParqueService,
                                         DiaAperturaService diaAperturaService) {
        this.configuracionParqueService = configuracionParqueService;
        this.diaAperturaService = diaAperturaService;
    }

    @GetMapping("/horario")
    @Operation(summary = "Obtener horario del parque",
            description = "Sin fecha, el horario por defecto. Con fecha, el especial de ese día o el general. Público.")
    public ResponseEntity<HorarioResponseDTO> obtenerHorario(
            @RequestParam(required = false) @Parameter(description = "Fecha (YYYY-MM-DD), opcional") LocalDate fecha) {
        ConfiguracionParque config = configuracionParqueService.getHorarioGeneral();
        DiaApertura dia = fecha != null ? diaAperturaService.findByDate(fecha).orElse(null) : null;
        // Lo que el día no define sale del horario general.
        LocalTime apertura = dia != null && dia.getHoraApertura() != null ? dia.getHoraApertura() : config.getHoraApertura();
        LocalTime cierre = dia != null && dia.getHoraCierre() != null ? dia.getHoraCierre() : config.getHoraCierre();
        boolean especial = !apertura.equals(config.getHoraApertura()) || !cierre.equals(config.getHoraCierre());
        return ResponseEntity.ok(new HorarioResponseDTO(apertura, cierre, config.getMinutosLimiteCompra(), especial));
    }

    @PutMapping("/horario")
    @Operation(summary = "Actualizar horario general", description = "Horario por defecto y, opcional, minutosLimiteCompra (0 a 720; null no lo cambia)")
    public ResponseEntity<ConfiguracionParque> actualizarHorarioGeneral(@RequestBody HorarioRequest request) {
        ConfiguracionParque actualizado = configuracionParqueService.actualizarHorarioGeneral(
                request.getHoraApertura(), request.getHoraCierre(), request.getMinutosLimiteCompra());
        return ResponseEntity.ok(actualizado);
    }

    @GetMapping("/factura-predeterminada")
    @Operation(summary = "Factura por defecto de cada forma de pago (ADMIN)",
            description = "Qué arranca marcado en el POS al elegir efectivo, tarjeta o QR: IMPRIMIR o MAIL (mail vacío = no facturar). "
                    + "El POS lo recibe en /api/interno/facturas/estado-servicio.")
    public FacturaPredeterminadaDTO obtenerFacturaPredeterminada() {
        return FacturaPredeterminadaDTO.de(configuracionParqueService.getHorarioGeneral());
    }

    @PutMapping("/factura-predeterminada")
    @Operation(summary = "Cambiar la factura por defecto de cada forma de pago (ADMIN)")
    public FacturaPredeterminadaDTO actualizarFacturaPredeterminada(@RequestBody FacturaPredeterminadaDTO request) {
        return FacturaPredeterminadaDTO.de(configuracionParqueService.actualizarFacturaPredeterminada(request));
    }
}
