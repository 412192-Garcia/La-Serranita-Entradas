package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.CorreccionVentaDTO;
import org.example.laserranitaentradas.model.dto.EditarVentaRequestDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CorreccionVenta;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.CorreccionVentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Corregir o cancelar una venta de caja. El admin, sin límites. El boletero, sólo una venta de puerta de
 * su caja abierta y dentro de los 15 minutos; lo suyo queda registrado para el cierre.
 */
@Service
public class CorreccionVentaService {

    public static final Duration VENTANA_BOLETERO = Duration.ofMinutes(15);

    private final CompraService compraService;
    private final CompraRepository compraRepository;
    private final CorreccionVentaRepository correccionRepository;

    public CorreccionVentaService(CompraService compraService, CompraRepository compraRepository,
                                  CorreccionVentaRepository correccionRepository) {
        this.compraService = compraService;
        this.compraRepository = compraRepository;
        this.correccionRepository = correccionRepository;
    }

    @Transactional
    public Compra editar(Long compraId, EditarVentaRequestDTO request, UsuarioAutenticado operador) {
        if (operador.rol().esAdmin()) return compraService.editarVenta(compraId, request);
        Compra compra = validarBoletero(compraId, operador);
        String antes = describir(compra);
        Compra editada = compraService.editarVenta(compraId, request);
        registrar(editada, operador, CorreccionVenta.Tipo.EDICION, antes, describir(editada), null);
        return editada;
    }

    @Transactional
    public Compra cancelar(Long compraId, String motivo, UsuarioAutenticado operador) {
        if (operador.rol().esAdmin()) return compraService.cancelarVenta(compraId, motivo);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribí por qué cancelás la venta.");
        }
        Compra compra = validarBoletero(compraId, operador);
        String antes = describir(compra);
        Compra cancelada = compraService.cancelarVenta(compraId, motivo);
        registrar(cancelada, operador, CorreccionVenta.Tipo.CANCELACION, antes, null, motivo.trim());
        return cancelada;
    }

    @Transactional(readOnly = true)
    public List<CorreccionVentaDTO> deCaja(Long cajaId) {
        return correccionRepository.findAllByCajaIdConCompra(cajaId).stream().map(CorreccionVentaDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CorreccionVentaDTO>> deCompras(Collection<Long> compraIds) {
        if (compraIds.isEmpty()) return Map.of();
        return correccionRepository.findAllByCompraIdInConCompra(compraIds).stream()
                .map(CorreccionVentaDTO::de)
                .collect(Collectors.groupingBy(CorreccionVentaDTO::compraId));
    }

    /** Hasta cuándo el boletero puede corregir esta venta; null si no puede (o si es admin). */
    public static LocalDateTime corregibleHasta(Compra c, UsuarioAutenticado operador, LocalDateTime ahora) {
        if (operador.rol().esAdmin() || motivoNoCorregible(c, operador, ahora) != null) return null;
        return c.getFechaValidacion().plus(VENTANA_BOLETERO);
    }

    private Compra validarBoletero(Long compraId, UsuarioAutenticado operador) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada ID: " + compraId));
        String motivo = motivoNoCorregible(compra, operador, LocalDateTime.now());
        if (motivo == null) return compra;
        if (compra.getCaja() == null || !esSuCaja(compra.getCaja(), operador)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, motivo);
        }
        throw new IllegalStateException(motivo);
    }

    /** Null si el boletero la puede corregir; si no, por qué. */
    static String motivoNoCorregible(Compra c, UsuarioAutenticado operador, LocalDateTime ahora) {
        if (c.getCaja() == null || !esSuCaja(c.getCaja(), operador)) return "Sólo podés corregir ventas de tu propia caja.";
        if (c.getCaja().getFechaCierre() != null) return "La caja ya se cerró: pedile la corrección a un administrador.";
        if (c.getEstado() != EstadoCompra.VENDIDO_EN_PUERTA) {
            return "Sólo se corrigen ventas de puerta. Una anticipada se deshace desde Anticipadas.";
        }
        if (c.getFechaValidacion() == null || ahora.isAfter(c.getFechaValidacion().plus(VENTANA_BOLETERO))) {
            return "Pasaron más de 15 minutos: pedile la corrección a un administrador.";
        }
        return null;
    }

    private static boolean esSuCaja(Caja caja, UsuarioAutenticado operador) {
        return caja.getUsuario() != null && Objects.equals(caja.getUsuario().getId(), operador.id());
    }

    private void registrar(Compra compra, UsuarioAutenticado operador, CorreccionVenta.Tipo tipo, String antes,
                           String despues, String motivo) {
        correccionRepository.save(CorreccionVenta.builder()
                .compra(compra).caja(compra.getCaja()).usuario(operador.username()).fecha(LocalDateTime.now())
                .tipo(tipo).antes(antes).despues(despues).motivo(motivo).build());
    }

    /** "Efectivo · 2× Pase General, 1× Gorra · $68.600". */
    static String describir(Compra c) {
        String items = c.getDetalles() == null || c.getDetalles().isEmpty() ? "(sin ítems)" : c.getDetalles().stream()
                .map(d -> d.getCantidad() + "× " + (d.getTipoEntrada() != null ? d.getTipoEntrada().getNombre()
                        : d.getArticuloVario() != null ? d.getArticuloVario().getNombre()
                        : Objects.requireNonNullElse(d.getDescripcionLibre(), "Artículo")))
                .collect(Collectors.joining(", "));
        NumberFormat pesos = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR"));
        String pago = c.getFormaPagoSecundaria() == null ? forma(c.getFormaPago())
                : forma(c.getFormaPago()) + " $" + pesos.format(c.getMontoTotal().subtract(c.getMontoFormaPagoSecundaria()))
                + " + " + forma(c.getFormaPagoSecundaria()) + " $" + pesos.format(c.getMontoFormaPagoSecundaria());
        String texto = pago + " · " + items + " · $" + pesos.format(c.getMontoTotal());
        return texto.length() > 500 ? texto.substring(0, 497) + "..." : texto;
    }

    private static String forma(FormaPago f) {
        if (f == null) return "Sin forma de pago";
        return switch (f) {
            case EFECTIVO_BOLETERIA -> "Efectivo";
            case TARJETA -> "Tarjeta";
            case MERCADO_PAGO_QR -> "QR";
            case SIN_COBRO -> "Sin cobro";
            default -> f.name();
        };
    }
}
