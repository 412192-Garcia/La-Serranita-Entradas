package org.example.laserranitaentradas.service.factura;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.dto.VentaFacturaDTO;
import org.example.laserranitaentradas.model.entity.*;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CobroPointRepository;
import org.example.laserranitaentradas.model.dto.CorreccionVentaDTO;
import org.example.laserranitaentradas.service.CorreccionVentaService;
import java.time.LocalDateTime;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.FacturaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ventana "Ventas y facturas": recuperar la factura de una venta o facturar una que se cobró sin factura. El
 * boletero sólo ve su caja; el admin, todas.
 */
@Service
public class VentasFacturasService {

    private static final String EMAIL_VALIDO = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    /** Ventas que se cobraron de verdad y se pueden facturar (ni canceladas ni reservas sin cobrar). */
    private static final Set<EstadoCompra> FACTURABLES = Set.of(EstadoCompra.VENDIDO_EN_PUERTA, EstadoCompra.USADO);

    private final CajaRepository cajaRepository;
    private final CompraRepository compraRepository;
    private final FacturaRepository facturaRepository;
    private final FacturaService facturaService;
    private final EmailService emailService;
    private final CobroPointRepository cobroPointRepository;
    private final CorreccionVentaService correccionVentaService;

    public VentasFacturasService(CajaRepository cajaRepository, CompraRepository compraRepository,
                                 FacturaRepository facturaRepository, FacturaService facturaService,
                                 EmailService emailService, CobroPointRepository cobroPointRepository,
                                 CorreccionVentaService correccionVentaService) {
        this.cajaRepository = cajaRepository;
        this.compraRepository = compraRepository;
        this.facturaRepository = facturaRepository;
        this.facturaService = facturaService;
        this.emailService = emailService;
        this.cobroPointRepository = cobroPointRepository;
        this.correccionVentaService = correccionVentaService;
    }

    /** Las ventas cobradas de un turno, de la más nueva a la más vieja, cada una con su factura. */
    @Transactional(readOnly = true)
    public List<VentaFacturaDTO> ventasDeCaja(Long cajaId, UsuarioAutenticado operador) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada ID: " + cajaId));
        validarAcceso(caja, operador);
        List<Compra> ventas = compraRepository.findAllByCajaIdConDetalles(cajaId).stream()
                .filter(c -> FACTURABLES.contains(c.getEstado()))
                .sorted(Comparator.comparing(Compra::getFechaValidacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        // Se consulta cada 2,5 s mientras algo se emite: todo el turno en pocas consultas.
        List<Long> ids = ventas.stream().map(Compra::getId).toList();
        Map<Long, FacturaResponseDTO> facturas = facturaService.obtenerPorCompras(ids);
        Map<Long, CobroPoint> cobrosPoint = ids.isEmpty() ? Map.of() : cobroPointRepository.findAllByCompraIdIn(ids).stream()
                .filter(cp -> cp.getEstado() == EstadoCobroPoint.APROBADO)
                .collect(Collectors.toMap(cp -> cp.getCompra().getId(), cp -> cp, (a, b) -> a));
        Map<Long, List<CorreccionVentaDTO>> correcciones = correccionVentaService.deCompras(ids);
        LocalDateTime ahora = LocalDateTime.now();
        return ventas.stream().map(c -> aDto(c, facturas.get(c.getId()), cobrosPoint.get(c.getId()),
                CorreccionVentaService.corregibleHasta(c, operador, ahora), correcciones.getOrDefault(c.getId(), List.of()))).toList();
    }

    private static VentaFacturaDTO aDto(Compra c, FacturaResponseDTO factura, CobroPoint point, LocalDateTime corregibleHasta,
                                        List<CorreccionVentaDTO> correcciones) {
        List<VentaFacturaDTO.Linea> lineas = c.getDetalles() == null ? List.of() : c.getDetalles().stream()
                .map(d -> new VentaFacturaDTO.Linea(d.getCantidad(), nombre(d),
                        d.getPrecioUnitario() != null ? d.getPrecioUnitario()
                                : d.getTipoEntrada() != null ? d.getTipoEntrada().getPrecio() : null,
                        d.getTipoEntrada() != null && d.getTipoEntrada().getTipo() == Tipo.ENTRADA,
                        d.getTipoEntrada() != null ? d.getTipoEntrada().getId() : null,
                        d.getArticuloVario() != null ? d.getArticuloVario().getId() : null))
                .toList();
        // En una caja, USADO es una reserva que se cobró acá; la venta de puerta queda VENDIDO_EN_PUERTA.
        boolean anticipada = c.getEstado() == EstadoCompra.USADO;
        String titular = anticipada && c.getCliente() != null
                ? (c.getCliente().getNombre() + " " + c.getCliente().getApellido()).trim() : null;
        return new VentaFacturaDTO(c.getId(), c.getCodigoReserva(), c.getFechaValidacion(), c.getMontoTotal(),
                c.getFormaPago(), resumen(c), factura, lineas, c.getDescuentoAplicado(),
                c.getPromocion() != null ? c.getPromocion().getNombre() : null,
                c.getFormaPagoSecundaria(), c.getMontoFormaPagoSecundaria(), c.getDolaresRecibidos(), c.getCotizacionDolar(),
                anticipada, titular,
                point != null ? point.getMedioPago() : null, point != null ? point.getTipoMedio() : null,
                point != null ? point.getCuotas() : null, corregibleHasta, correcciones);
    }

    private static String nombre(CompraDetalle d) {
        return d.getTipoEntrada() != null ? d.getTipoEntrada().getNombre()
                : d.getArticuloVario() != null ? d.getArticuloVario().getNombre()
                : Objects.requireNonNullElse(d.getDescripcionLibre(), "Artículo");
    }

    /** Mismo camino que el POS: se emite al commitear. */
    @Transactional
    public FacturaResponseDTO facturar(Long compraId, FacturacionPosDTO pedido, UsuarioAutenticado operador) {
        // Bloqueada para que dos "Facturar" (o una cancelación) no pasen los dos el chequeo.
        Compra compra = compraRepository.findByIdBloqueando(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada ID: " + compraId));
        if (compra.getCaja() == null) {
            throw new IllegalStateException("Sólo se facturan desde acá las ventas de una caja");
        }
        validarAcceso(compra.getCaja(), operador);
        if (!FACTURABLES.contains(compra.getEstado())) {
            throw new IllegalStateException("Esta venta no se puede facturar (está " + compra.getEstado() + ")");
        }
        if (pedido == null || pedido.getDestino() == null) {
            throw new IllegalArgumentException("Elegí si la factura se imprime o se manda por mail");
        }
        if (pedido.getDestino() == DestinoFactura.MAIL && (pedido.getEmail() == null || pedido.getEmail().isBlank())) {
            throw new IllegalArgumentException("Cargá el email del cliente");
        }
        if (facturaService.obtenerPorCompra(compraId)
                .filter(f -> f.getEstado() != EstadoFactura.ANULADA && !f.isAnulacionPedida()).isPresent()) {
            throw new IllegalStateException("Esta venta ya tiene factura");
        }
        Factura factura = facturaService.solicitar(compra, pedido)
                .orElseThrow(() -> new IllegalStateException("No se pudo facturar: la facturación no está configurada o la venta es de $0"));
        return facturaService.obtenerPorCompra(compraId)
                .orElseThrow(() -> new IllegalStateException("No se encontró la factura recién creada ID " + factura.getId()));
    }

    /** Un boletero sólo toca facturas de su caja. Devuelve la factura para no buscarla dos veces. */
    @Transactional(readOnly = true)
    public Factura validarAccesoAFactura(Long facturaId, UsuarioAutenticado operador) {
        Factura factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (operador.rol().esAdmin()) return factura;
        // Las manuales (sin venta) y las de compras online (sin caja): sólo un admin.
        Caja caja = factura.getCompra() != null ? factura.getCompra().getCaja() : null;
        if (caja == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sólo podés ver las facturas de tu propia caja");
        }
        validarAcceso(caja, operador);
        return factura;
    }

    /** Manda una factura ya emitida al email que dé el cliente (sirve aunque se haya impreso). */
    public void enviarPorMail(Long facturaId, String email, UsuarioAutenticado operador) {
        Factura factura = validarAccesoAFactura(facturaId, operador);
        if (factura.getEstado() != EstadoFactura.EMITIDA) {
            throw new IllegalStateException("La factura todavía no está emitida");
        }
        if (Boolean.TRUE.equals(factura.getAnulacionPedida())) {
            throw new IllegalStateException("Esta factura se anuló (la venta se canceló o cambió)");
        }
        String limpio = email == null ? "" : email.trim();
        if (!limpio.matches(EMAIL_VALIDO)) {
            throw new IllegalArgumentException("El email no es válido");
        }
        emailService.enviarFacturaA(facturaId, limpio);
    }

    private static void validarAcceso(Caja caja, UsuarioAutenticado operador) {
        if (operador.rol().esAdmin()) return;
        if (caja.getUsuario() == null || !Objects.equals(caja.getUsuario().getId(), operador.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sólo podés ver las ventas de tu propia caja");
        }
    }

    private static String resumen(Compra c) {
        if (c.getDetalles() == null) return "";
        return c.getDetalles().stream()
                .map(d -> d.getCantidad() + "x " + nombre(d))
                .collect(Collectors.joining(", "));
    }
}
