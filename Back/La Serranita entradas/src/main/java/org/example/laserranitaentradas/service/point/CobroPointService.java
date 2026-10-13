package org.example.laserranitaentradas.service.point;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.CobroPoint;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCobroPoint;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Modulo;
import org.example.laserranitaentradas.model.entity.TerminalPoint;
import org.example.laserranitaentradas.monitoreo.AuditoriaContexto;
import org.example.laserranitaentradas.repository.CobroPointRepository;
import org.example.laserranitaentradas.repository.TerminalPointRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.ModulosService;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient.Orden;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient.PointException;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cobros con el Point de Mercado Pago desde el POS (ver {@link CobroPoint}).
 *
 * Sin @Transactional a propósito: cada paso habla con Mercado Pago, y tener una transacción
 * abierta durante la llamada retiene una conexión del pool mientras el boletero espera. Los
 * guardados van de a uno (cada save tiene la suya). Las excepciones son {@link #vincular},
 * {@link #devolverDeVenta} y {@link #validarEdicion}, que corren dentro de la transacción de la venta.
 */
@Service
public class CobroPointService {

    private static final Logger log = LoggerFactory.getLogger(CobroPointService.class);
    private static final Logger alertas = LoggerFactory.getLogger("alertas.pagos");

    /** El POS pregunta cada ~2 s mientras espera; a MP se le pregunta como mucho cada 3 s por cobro
     * (MP desaconseja consultar seguido: el aviso normal es el webhook). */
    private static final Duration ENTRE_CONSULTAS = Duration.ofSeconds(3);
    /** Los Points casi no cambian: se le pide la lista a MP como mucho cada 30 s. */
    private static final Duration VIGENCIA_TERMINALES = Duration.ofSeconds(30);
    public static final String PREFIJO_REFERENCIA = "POINT-";
    private static final Set<String> MODOS = Set.of("PDV", "STANDALONE");
    private static final Set<FormaPago> FORMAS_POINT = Set.of(FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);

    private final CobroPointRepository repository;
    private final TerminalPointRepository terminalRepository;
    private final MercadoPagoPointClient client;
    private final ModulosService modulos;
    private final CajaService cajaService;

    private volatile List<Terminal> terminalesCache;
    private volatile LocalDateTime terminalesCacheHasta = LocalDateTime.MIN;

    public CobroPointService(CobroPointRepository repository, TerminalPointRepository terminalRepository,
                             MercadoPagoPointClient client, ModulosService modulos, CajaService cajaService) {
        this.repository = repository;
        this.terminalRepository = terminalRepository;
        this.client = client;
        this.modulos = modulos;
        this.cajaService = cajaService;
    }

    public record TerminalDTO(String id, String nombre, String serie, String modo, boolean integrado) {}

    public record CobroPointDTO(Long id, String ordenId, String terminalId, BigDecimal monto, FormaPago formaPago,
                                EstadoCobroPoint estado, boolean enCurso, String detalle, String motivo, String tipoMedio,
                                String medioPago, Integer cuotas, Long compraId, LocalDateTime fecha) {}

    /** formaPago: TARJETA (por defecto) o MERCADO_PAGO_QR. */
    public record CrearCobroRequest(String terminalId, BigDecimal monto, String claveIdempotencia, FormaPago formaPago) {}

    /** Para el cierre de caja: lo que cobró el Point (ya atado a ventas) y lo cobrado sin venta. */
    public record ResumenCajaPoint(BigDecimal tarjeta, BigDecimal qr, long cantidad, List<CobroPointDTO> sinVenta) {}

    // ---------- Terminales ----------

    /** Los Points de la cuenta, con el nombre que se les puso acá. */
    public List<TerminalDTO> terminales(boolean refrescar) {
        if (refrescar || terminalesCache == null || LocalDateTime.now().isAfter(terminalesCacheHasta)) {
            terminalesCache = client.listarTerminales();
            terminalesCacheHasta = LocalDateTime.now().plus(VIGENCIA_TERMINALES);
        }
        Map<String, String> nombres = new java.util.HashMap<>();
        terminalRepository.findAll().forEach(t -> nombres.put(t.getId(), t.getNombre()));
        return terminalesCache.stream().map(t -> {
            String serie = serie(t.id());
            String nombre = nombres.getOrDefault(t.id(), "Point " + serie.substring(Math.max(0, serie.length() - 6)));
            return new TerminalDTO(t.id(), nombre, serie, t.modo(), "PDV".equals(t.modo()));
        }).toList();
    }

    public void renombrar(String terminalId, String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) {
            terminalRepository.deleteById(terminalId);
            return;
        }
        if (limpio.length() > 60) {
            throw new IllegalArgumentException("El nombre puede tener hasta 60 caracteres");
        }
        TerminalPoint t = terminalRepository.findById(terminalId).orElseGet(() -> new TerminalPoint(terminalId, limpio));
        AuditoriaContexto.cambio("Nombre", t.getNombre().equals(limpio) ? null : t.getNombre(), limpio);
        t.setNombre(limpio);
        terminalRepository.save(t);
    }

    /** PDV = integrado (sólo cobra lo que manda la tablet); STANDALONE = se cobra a mano en el Point. */
    public void cambiarModo(String terminalId, String modo) {
        if (!MODOS.contains(modo)) {
            throw new IllegalArgumentException("Modo inválido: " + modo);
        }
        try {
            AuditoriaContexto.cambio("Modo", null, "PDV".equals(modo) ? "Integrado" : "Manual");
        client.cambiarModo(terminalId, modo);
        } catch (PointException e) {
            throw new IllegalStateException(switch (e.getStatus()) {
                case 412 -> "Ya hay otro Point integrado en la misma caja de Mercado Pago: cada caja admite uno solo.";
                case 403 -> "Este modelo de Point no se puede integrar.";
                default -> e.getMessage();
            });
        }
        terminalesCache = null;
        log.info("Point {} pasado a modo {}", terminalId, modo);
    }

    /** "NEWLAND_N950__N950NCB801293324" → "N950NCB801293324" (lo que dice la etiqueta de atrás del Point). */
    static String serie(String terminalId) {
        int i = terminalId.indexOf("__");
        return i >= 0 ? terminalId.substring(i + 2) : terminalId;
    }

    // ---------- Cobros ----------

    public CobroPointDTO crear(CrearCobroRequest pedido, UsuarioAutenticado operador) {
        if (!modulos.activo(Modulo.POINT)) {
            throw new IllegalStateException("El cobro con Point está apagado");
        }
        if (pedido.claveIdempotencia() == null || pedido.claveIdempotencia().isBlank() || pedido.claveIdempotencia().length() > 64) {
            throw new IllegalArgumentException("Falta la clave del cobro");
        }
        // El POS reintenta con la misma clave si se perdió la respuesta: mismo cobro, nunca otra orden.
        var existente = repository.findByClaveIdempotencia(pedido.claveIdempotencia());
        if (existente.isPresent()) {
            return toDto(existente.get());
        }
        if (pedido.terminalId() == null || pedido.terminalId().isBlank()) {
            throw new IllegalArgumentException("Elegí el Point en Dispositivos");
        }
        if (pedido.monto() == null || pedido.monto().signum() <= 0) {
            throw new IllegalArgumentException("El monto a cobrar tiene que ser mayor a cero");
        }
        FormaPago forma = pedido.formaPago() != null ? pedido.formaPago() : FormaPago.TARJETA;
        if (!FORMAS_POINT.contains(forma)) {
            throw new IllegalArgumentException("El Point sólo cobra tarjeta o QR");
        }
        Caja caja = cajaService.getAbiertaOrThrow(operador.id());

        CobroPoint cobro = repository.save(CobroPoint.builder()
                .claveIdempotencia(pedido.claveIdempotencia())
                .terminalId(pedido.terminalId())
                .monto(pedido.monto())
                .formaPago(forma)
                .estado(EstadoCobroPoint.CREADO)
                .caja(caja)
                .build());
        try {
            Orden orden = client.crearOrden(cobro.getTerminalId(), cobro.getMonto(), PREFIJO_REFERENCIA + cobro.getId(),
                    cobro.getClaveIdempotencia(), forma == FormaPago.MERCADO_PAGO_QR);
            cobro.setOrdenId(orden.id());
            aplicar(cobro, orden);
            log.info("Cobro con Point {} por {} mandado al Point {} (orden {})", cobro.getId(), cobro.getMonto(),
                    cobro.getTerminalId(), orden.id());
        } catch (PointException e) {
            cobro.setEstado(EstadoCobroPoint.ERROR);
            cobro.setDetalle(recortar(e.getMessage()));
            log.warn("No se pudo mandar el cobro {} al Point {}: {}", cobro.getId(), cobro.getTerminalId(), e.getMessage());
        }
        return toDto(repository.save(cobro));
    }

    /** Lo que el POS consulta mientras espera. Si no llegó el webhook, le pregunta a MP (no más de cada 3 s). */
    public CobroPointDTO consultar(Long id, UsuarioAutenticado operador) {
        CobroPoint cobro = cargar(id, operador);
        boolean toca = cobro.getUltimaConsultaMp() == null
                || cobro.getUltimaConsultaMp().plus(ENTRE_CONSULTAS).isBefore(LocalDateTime.now());
        if (cobro.getEstado().enCurso() && cobro.getOrdenId() != null && toca) {
            try {
                aplicar(cobro, client.obtenerOrden(cobro.getOrdenId()));
                cobro = repository.save(cobro);
            } catch (PointException e) {
                // Un pedido que falla no cambia nada: el POS vuelve a preguntar en un rato.
                log.warn("No se pudo consultar la orden {} del cobro {} en Mercado Pago: {}", cobro.getOrdenId(), id, e.getMessage());
            }
        }
        return toDto(cobro);
    }

    public CobroPointDTO cancelar(Long id, UsuarioAutenticado operador) {
        CobroPoint cobro = cargar(id, operador);
        if (!cobro.getEstado().enCurso() || cobro.getOrdenId() == null) {
            return toDto(cobro);
        }
        try {
            aplicar(cobro, client.cancelarOrden(cobro.getOrdenId(), "cancelar-" + cobro.getClaveIdempotencia()));
        } catch (PointException e) {
            if (e.getStatus() == 409) {
                throw new IllegalStateException("El Point ya está procesando la tarjeta: esperá el resultado o cancelalo en el propio Point.");
            }
            throw new IllegalStateException(e.getMessage());
        }
        log.info("Cobro con Point {} cancelado desde la tablet ({})", id, cobro.getEstado());
        return toDto(repository.save(cobro));
    }

    /** Devuelve un cobro aprobado que no terminó en venta (la venta se rechazó, el cliente se fue...). */
    public CobroPointDTO devolver(Long id, UsuarioAutenticado operador) {
        CobroPoint cobro = cargar(id, operador);
        if (cobro.getEstado() != EstadoCobroPoint.APROBADO) {
            throw new IllegalStateException("Sólo se puede devolver un cobro aprobado");
        }
        if (cobro.getCompra() != null) {
            throw new IllegalStateException("Este cobro ya es de la venta " + cobro.getCompra().getCodigoReserva()
                    + ": para devolverlo hay que cancelar esa venta");
        }
        devolverEnMercadoPago(cobro);
        log.info("Cobro con Point {} por {} devuelto al cliente por {}", id, cobro.getMonto(), operador.username());
        return toDto(repository.save(cobro));
    }

    private void devolverEnMercadoPago(CobroPoint cobro) {
        try {
            aplicar(cobro, client.devolverOrden(cobro.getOrdenId(), "devolver-" + cobro.getClaveIdempotencia()));
        } catch (PointException e) {
            throw new IllegalStateException("Mercado Pago no aceptó la devolución del cobro con Point: " + e.getMessage());
        }
        if (cobro.getEstado() != EstadoCobroPoint.DEVUELTO) {
            // Algunas tarjetas sólo se devuelven desde el propio Point: MP acepta el pedido pero no la deja devuelta.
            log.warn("La devolución del cobro con Point {} quedó en {} ({})", cobro.getId(), cobro.getEstado(), cobro.getDetalle());
        }
    }

    /**
     * Al cancelar una venta pagada con el Point se le devuelve la plata al cliente. Va antes de
     * marcarla cancelada y dentro de su transacción: si MP no acepta la devolución, la venta no
     * se cancela (y el admin ve por qué).
     */
    public void devolverDeVenta(Compra venta) {
        for (CobroPoint cobro : repository.findAllByCompraId(venta.getId())) {
            if (cobro.getEstado() != EstadoCobroPoint.APROBADO) continue;
            devolverEnMercadoPago(cobro);
            repository.save(cobro);
            AuditoriaContexto.detalle("Cobro con Point devuelto: $" + cobro.getMonto().stripTrailingZeros().toPlainString());
            log.info("Cobro con Point {} por {} devuelto al cancelar la venta {}", cobro.getId(), cobro.getMonto(), venta.getCodigoReserva());
        }
    }

    /**
     * Una venta pagada con el Point no puede cambiar lo que cobró el Point: el pago ya está hecho
     * por ese monto. Si cambian las entradas o la forma de pago de esa parte, hay que cancelarla
     * (se devuelve el cobro) y cargarla de nuevo.
     */
    public void validarEdicion(Compra venta) {
        List<CobroPoint> cobros = repository.findAllByCompraId(venta.getId()).stream()
                .filter(c -> c.getEstado() == EstadoCobroPoint.APROBADO).toList();
        if (cobros.isEmpty()) return;
        try {
            validarMontos(cobros, venta);
        } catch (RuntimeException e) {
            BigDecimal total = cobros.stream().map(CobroPoint::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
            throw new IllegalStateException("Esta venta se cobró con el Point ($" + total.stripTrailingZeros().toPlainString()
                    + ") y ese pago no se puede cambiar: cancelala (se le devuelve el cobro al cliente) y cargala de nuevo.");
        }
    }

    /** Para el cierre de caja (ADMIN). */
    public ResumenCajaPoint resumenCaja(Long cajaId) {
        BigDecimal tarjeta = BigDecimal.ZERO;
        BigDecimal qr = BigDecimal.ZERO;
        long cantidad = 0;
        for (Object[] fila : repository.totalesAprobadosPorForma(cajaId)) {
            BigDecimal suma = (BigDecimal) fila[1];
            if (fila[0] == FormaPago.MERCADO_PAGO_QR) qr = qr.add(suma);
            else tarjeta = tarjeta.add(suma);
            cantidad += ((Number) fila[2]).longValue();
        }
        List<CobroPointDTO> sinVenta = repository.aprobadosSinVenta(cajaId).stream().map(CobroPointService::toDto).toList();
        return new ResumenCajaPoint(tarjeta, qr, cantidad, sinVenta);
    }

    /** Aviso de Mercado Pago (tópico "order"). No se confía en el cuerpo: la orden se trae de la API. */
    public void actualizarDesdeMercadoPago(String ordenId) {
        CobroPoint cobro = repository.findByOrdenId(ordenId).orElse(null);
        if (cobro == null) {
            log.info("Aviso de la orden {} de Mercado Pago que no es un cobro con Point de este sistema", ordenId);
            return;
        }
        EstadoCobroPoint antes = cobro.getEstado();
        aplicar(cobro, client.obtenerOrden(ordenId));
        repository.save(cobro);
        if (antes != cobro.getEstado()) {
            log.info("Cobro con Point {}: {} → {} (aviso de Mercado Pago)", cobro.getId(), antes, cobro.getEstado());
        }
    }

    /**
     * Ata los cobros a la venta que se pagó con ellos (uno, o dos en un pago mixto tarjeta + QR).
     * Corre dentro de la transacción de la venta: si algo no cierra, la venta no se registra (y el
     * POS la muestra rechazada con el motivo).
     */
    public void vincular(List<Long> cobroIds, Compra venta) {
        if (cobroIds == null || cobroIds.isEmpty()) return;
        List<CobroPoint> cobros = new java.util.ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(cobroIds)) {
            CobroPoint cobro = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("No existe el cobro con Point " + id));
            if (cobro.getCompra() != null && !cobro.getCompra().getId().equals(venta.getId())) {
                throw new IllegalStateException("El cobro con Point " + id + " ya es de la venta " + cobro.getCompra().getCodigoReserva());
            }
            if (cobro.getEstado() != EstadoCobroPoint.APROBADO) {
                throw new IllegalStateException("El cobro con Point " + id + " no está aprobado (" + cobro.getEstado() + ")");
            }
            cobros.add(cobro);
        }
        validarMontos(cobros, venta);
        for (CobroPoint cobro : cobros) {
            cobro.setCompra(venta);
            repository.save(cobro);
        }
    }

    /** Lo cobrado por el Point de cada forma (tarjeta, QR) tiene que ser exactamente esa parte de la venta. */
    private static void validarMontos(List<CobroPoint> cobros, Compra venta) {
        Map<FormaPago, BigDecimal> porForma = new java.util.EnumMap<>(FormaPago.class);
        for (CobroPoint c : cobros) {
            porForma.merge(c.getFormaPago() != null ? c.getFormaPago() : FormaPago.TARJETA, c.getMonto(), BigDecimal::add);
        }
        porForma.forEach((forma, cobrado) -> {
            BigDecimal parte = montoDeForma(venta, forma);
            String nombre = forma == FormaPago.MERCADO_PAGO_QR ? "QR" : "tarjeta";
            if (parte == null) {
                throw new IllegalArgumentException("La venta no tiene una parte en " + nombre + " para pagar con el cobro del Point");
            }
            if (parte.compareTo(cobrado) != 0) {
                throw new IllegalStateException("El Point cobró $" + cobrado.toPlainString() + " en " + nombre + " pero esa parte de la venta es $"
                        + parte.toPlainString() + ": revisá la venta o devolvé el cobro");
            }
        });
    }

    /** Cuánto de la venta se cobró con esa forma: todo, la parte principal o la secundaria de un pago mixto. Null si ninguna. */
    static BigDecimal montoDeForma(Compra venta, FormaPago forma) {
        BigDecimal secundaria = venta.getMontoFormaPagoSecundaria() != null ? venta.getMontoFormaPagoSecundaria() : BigDecimal.ZERO;
        if (venta.getFormaPago() == forma) {
            return venta.getMontoTotal().subtract(venta.getFormaPagoSecundaria() != null ? secundaria : BigDecimal.ZERO);
        }
        if (venta.getFormaPagoSecundaria() == forma) {
            return secundaria;
        }
        return null;
    }

    private CobroPoint cargar(Long id, UsuarioAutenticado operador) {
        CobroPoint cobro = repository.findConCajaById(id).orElseThrow(() -> new IllegalArgumentException("No existe el cobro con Point " + id));
        // Un boletero sólo ve los cobros de su propia caja; un admin, todos.
        if (!operador.rol().esAdmin() && !cobro.getCaja().getUsuario().getId().equals(operador.id())) {
            throw new IllegalArgumentException("No existe el cobro con Point " + id);
        }
        return cobro;
    }

    void aplicar(CobroPoint cobro, Orden orden) {
        EstadoCobroPoint estado = estadoDe(orden.status());
        if (estado != null) {
            if (cobro.getEstado() == EstadoCobroPoint.CANCELADO && estado == EstadoCobroPoint.APROBADO) {
                // Se pidió cancelar con el Point ya cobrando y el pago entró igual.
                alertas.warn("El cobro con Point {} por {} se había pedido cancelar pero salió APROBADO", cobro.getId(), cobro.getMonto());
            }
            cobro.setEstado(estado);
        }
        cobro.setDetalle(recortar(orden.pagoStatusDetail() != null ? orden.pagoStatusDetail() : orden.statusDetail()));
        if (orden.pagoId() != null) cobro.setPagoId(orden.pagoId());
        if (orden.medioPago() != null) cobro.setMedioPago(orden.medioPago());
        if (orden.tipoMedio() != null) cobro.setTipoMedio(orden.tipoMedio());
        if (orden.cuotas() != null) cobro.setCuotas(orden.cuotas());
        cobro.setUltimaConsultaMp(LocalDateTime.now());
    }

    /** status de la orden de MP → estado propio. Null = uno desconocido: se deja el que estaba. */
    static EstadoCobroPoint estadoDe(String status) {
        if (status == null) return null;
        return switch (status) {
            case "created" -> EstadoCobroPoint.CREADO;
            case "at_terminal" -> EstadoCobroPoint.EN_TERMINAL;
            case "action_required" -> EstadoCobroPoint.REVISAR_EN_POINT;
            case "processed" -> EstadoCobroPoint.APROBADO;
            case "refunded" -> EstadoCobroPoint.DEVUELTO;
            case "failed" -> EstadoCobroPoint.RECHAZADO;
            case "expired" -> EstadoCobroPoint.EXPIRADO;
            case "canceled" -> EstadoCobroPoint.CANCELADO;
            default -> null;
        };
    }

    /** Lo que se le muestra al boletero. */
    static String motivo(CobroPoint cobro) {
        if (cobro.getEstado() == EstadoCobroPoint.ERROR) return cobro.getDetalle();
        String detalle = cobro.getDetalle();
        String porDetalle = detalle == null ? null : switch (detalle) {
            case "rejected_by_issuer" -> "El banco rechazó la tarjeta";
            case "insufficient_amount" -> "Fondos insuficientes";
            case "card_disabled" -> "La tarjeta está bloqueada o deshabilitada";
            case "bad_filled_card_data" -> "Los datos de la tarjeta se ingresaron mal";
            case "high_risk" -> "Rechazado por prevención de fraude";
            case "required_call_for_authorize" -> "El banco pide que el cliente llame para autorizar el pago";
            case "max_attempts_exceeded" -> "Se superó la cantidad de intentos";
            case "amount_limit_exceeded" -> "El monto supera el límite de la tarjeta";
            case "invalid_installments" -> "Las cuotas elegidas no se aceptan";
            case "processing_error" -> "Error de procesamiento en Mercado Pago";
            case "canceled_on_terminal" -> "Se canceló en el Point";
            case "canceled_by_api" -> "Se canceló desde la tablet";
            case "cancellation_requested" -> "Cancelando en el Point...";
            case "check_on_terminal" -> "Revisá en el Point si el pago salió";
            case "waiting_payment" -> "Procesando el pago...";
            default -> null;
        };
        if (porDetalle != null) return porDetalle;
        return switch (cobro.getEstado()) {
            case CREADO -> "Mandando el cobro al Point...";
            case EN_TERMINAL -> cobro.getFormaPago() == FormaPago.MERCADO_PAGO_QR
                    ? "Esperando que el cliente escanee el QR del Point" : "Esperando la tarjeta en el Point";
            case REVISAR_EN_POINT -> "Revisá en el Point si el pago salió";
            case APROBADO -> "Pago aprobado";
            case RECHAZADO -> "El pago fue rechazado";
            case CANCELADO -> "Cobro cancelado";
            case EXPIRADO -> "Se venció el tiempo para pasar la tarjeta";
            case DEVUELTO -> "Cobro devuelto al cliente";
            case ERROR -> detalle;
        };
    }

    static CobroPointDTO toDto(CobroPoint c) {
        return new CobroPointDTO(c.getId(), c.getOrdenId(), c.getTerminalId(), c.getMonto(),
                c.getFormaPago() != null ? c.getFormaPago() : FormaPago.TARJETA, c.getEstado(), c.getEstado().enCurso(),
                c.getDetalle(), motivo(c), c.getTipoMedio(), c.getMedioPago(), c.getCuotas(),
                c.getCompra() != null ? c.getCompra().getId() : null, c.getFechaCreacion());
    }

    private static String recortar(String s) {
        return s == null ? null : s.length() > 300 ? s.substring(0, 300) : s;
    }
}
