package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.model.entity.Modulo;
import org.example.laserranitaentradas.service.FacturaService;
import org.example.laserranitaentradas.service.ModulosService;
import org.example.laserranitaentradas.service.afip.ArcaClient;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Módulos que se pueden apagar. Leerlos puede cualquier usuario del módulo interno (el POS los usa
 * para saber qué mostrar); cambiarlos, sólo el SUPERADMIN (ruta /api/interno/sistema/**).
 */
@RestController
@Tag(name = "Módulos", description = "Partes del sistema que se prenden y apagan desde Sistema > Módulos")
public class ModulosController {

    private final ModulosService modulos;
    private final ArcaClient arcaClient;
    private final MercadoPagoPointClient pointClient;
    private final FacturaService facturaService;

    public ModulosController(ModulosService modulos, ArcaClient arcaClient, MercadoPagoPointClient pointClient,
                             FacturaService facturaService) {
        this.modulos = modulos;
        this.arcaClient = arcaClient;
        this.pointClient = pointClient;
        this.facturaService = facturaService;
    }

    /**
     * activo = prendido desde Módulos; configurado = el servidor tiene lo que hace falta (certificado,
     * token). Sólo funciona si están las dos cosas.
     */
    public record EstadoModulo(String codigo, String nombre, String descripcion, boolean activo, boolean configurado,
                               String faltaConfigurar, String padre) {}

    @GetMapping("/api/interno/modulos")
    @Operation(summary = "Módulos y si están prendidos")
    public List<EstadoModulo> listar() {
        return Arrays.stream(Modulo.values()).map(this::estado).toList();
    }

    @PutMapping("/api/interno/sistema/modulos/{codigo}")
    @Operation(summary = "Prender o apagar un módulo (SUPERADMIN)")
    public EstadoModulo cambiar(@PathVariable String codigo, @RequestBody Map<String, Boolean> body) {
        Modulo modulo;
        try {
            modulo = Modulo.valueOf(codigo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el módulo " + codigo);
        }
        Boolean activo = body.get("activo");
        if (activo == null) {
            throw new IllegalArgumentException("Falta indicar si queda prendido");
        }
        modulos.cambiar(modulo, activo);
        return estado(modulo);
    }

    private EstadoModulo estado(Modulo m) {
        String falta = switch (m) {
            case FACTURACION -> arcaClient.estaConfigurado() ? null : "Falta el certificado de ARCA en el servidor (AFIP_CERT / AFIP_KEY).";
            // Los submódulos sólo dicen lo propio: el certificado ya lo avisa Facturación.
            case FACTURACION_BOLETERIA -> null;
            case FACTURACION_ONLINE -> facturaService.puntoVentaOnlineConfigurado() ? null
                    : "Falta el punto de venta de las compras online (AFIP_PUNTO_VENTA_ONLINE).";
            case RESERVA_EFECTIVO -> null;
            case POINT -> pointClient.estaConfigurado() ? null : "Falta el access token de Mercado Pago en el servidor.";
        };
        return new EstadoModulo(m.name(), m.getNombre(), m.getDescripcion(), modulos.activo(m), falta == null, falta,
                m.getCodigoPadre());
    }
}
