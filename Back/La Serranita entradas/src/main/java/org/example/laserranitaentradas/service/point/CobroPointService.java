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
import org.springframework.dao.OptimisticLockingFailureException;
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
    /** Detalle de un cobro cuya orden se pidió a MP sin respuesta clara: se vuelve a pedir con la misma clave. */
    static final String DETALLE_SIN_CONFIRMAR = "sin_confirmar";
    private static final Set<String> MODOS = Set.of("PDV", "STANDALONE");
    private static final Set<FormaPago> FORMAS_POINT = Set.of(FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);

    private final CobroPointRepository repository;
    private final TerminalPointRepository terminalRepository;
    private final MercadoPagoPointClient client;
    private final ModulosService modulos;
    private final CajaService cajaService;

    private volatile List<Terminal> terminalesCache;
    /** Modo pedido hace poco, que manda sobre lo que diga MP mientras su lista se pone al día. */
    private record ModoReciente(String modo, LocalDateTime hasta) {}
    private final Map<String, ModoReciente> modosRecientes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Duration MODO_RECIENTE = Duration.ofMinutes(2);
    private volatile LocalDateTime terminalesCacheHasta = LocalDateTime.MIN;

    public CobroPointService(CobroPointRepository repository, TerminalPointRepository terminalRepository,
                             MercadoPagoPointClient client, ModulosService modulos, CajaService cajaService) {
        this.repository = repository;
        this.terminalRepository = terminalRepository;
        this.client = client;
        this.modulos = modulos;
        this.cajaService = cajaService;
    }

    /** habilitado: se puede elegir desde las tablets (se activa en Configuración > Points). */
    public record TerminalDTO(String id, String nombre, String serie, String modo, boolean integrado, boolean habilitado) {}

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
        // Copia local: cambiarModo puede reemplazar la caché mientras tanto.
        List<Terminal> lista = terminalesCache;
        if (refrescar || lista == null || LocalDateTime.now().isAfter(terminalesCacheHasta)) {
            lista = conModosRecientes(client.listarTerminales(), lista);
            terminalesCache = lista;
            terminalesCacheHasta = LocalDateTime.now().plus(VIGENCIA_TERMINALES);
        }
        Map<String, String> nombres = new java.util.HashMap<>();
        Set<String> habilitados = new java.util.HashSet<>();
        terminalRepository.findAll().forEach(t -> {
            nombres.put(t.getId(), t.getNombre());
            if (t.isHabilitado()) habilitados.add(t.getId());
        });
        // Por nombre: MP los devuelve en cualquier orden y cambiaban de lugar en cada consulta.
        return lista.stream().map(t -> {
            String serie = serie(t.id());
            String nombre = nombres.getOrDefault(t.id(), nombrePorDefecto(serie));
            return new TerminalDTO(t.id(), nombre, serie, t.modo(), "PDV".equals(t.modo()), habilitados.contains(t.id()));
        }).sorted(java.util.Comparator.comparing(TerminalDTO::nombre, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TerminalDTO::serie)).toList();
    }

    /** Los que se pueden elegir desde las tablets (los demás de la cuenta no aparecen en el POS). */
    public List<TerminalDTO> terminalesHabilitados() {
        return terminales(false).stream().filter(TerminalDTO::habilitado).toList();
    }

    /** Vacío = vuelve al nombre por defecto ("Point " + el final de la serie). */
    public void renombrar(String terminalId, String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.length() > 60) {
            throw new IllegalArgumentException("El nombre puede tener hasta 60 caracteres");
        }
        String nuevo = limpio.isEmpty() ? nombrePorDefecto(serie(terminalId)) : limpio;
        TerminalPoint t = terminalRepository.findById(terminalId).orElseGet(() -> new TerminalPoint(terminalId, nuevo));
        AuditoriaContexto.cambio("Nombre", t.getNombre().equals(nuevo) ? null : t.getNombre(), nuevo);
        t.setNombre(nuevo);
        terminalRepository.save(t);
    }

    /** Que se pueda elegir (o no) desde las tablets. */
    public void habilitar(String terminalId, boolean habilitado) {
        TerminalPoint t = terminalRepository.findById(terminalId)
                .orElseGet(() -> new TerminalPoint(terminalId, nombrePorDefecto(serie(terminalId))));
        AuditoriaContexto.cambio("Disponible en las tablets", t.isHabilitado() ? "Sí" : "No", habilitado ? "Sí" : "No");
        t.setHabilitado(habilitado);
        terminalRepository.save(t);
        log.info("Point {} {} para las tablets", terminalId, habilitado ? "habilitado" : "deshabilitado");
    }

    private static String nombrePorDefecto(String serie) {
        return "Point " + serie.substring(Math.max(0, serie.length() - 6));
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
        // Se actualiza en la caché en vez de tirarla: la lista de MP tarda en reflejar el cambio, y
        // pedirla enseguida devolvía el Point con el modo viejo, o directamente sin él.
        List<Terminal> lista = terminalesCache;
        if (lista != null) {
            terminalesCache = lista.stream().map(t -> t.id().equals(terminalId)
                    ? new Terminal(t.id(), modo, t.posId(), t.storeId(), t.externalPosId()) : t).toList();
        }
        modosRecientes.put(terminalId, new ModoReciente(modo, LocalDateTime.now().plus(MODO_RECIENTE)));
        log.info("Point {} pasado a modo {}", terminalId, modo);
    }

    /** "NEWLAND_N950__N950NCB801293324" → "N950NCB801293324" (lo que dice la etiqueta de atrás del Point). */
    static String serie(String terminalId) {
        int i = terminalId.indexOf("__");
        return i >= 0 ? terminalId.substring(i + 2) : terminalId;
    }

    /**
     * Lo que trae MP, corregido con los Points cuyo modo se cambió desde acá hace poco: la lista de
     * MP tarda en ponerse al día y, mientras tanto, muestra el modo viejo o directamente no incluye
     * ese Point (en la pantalla "desaparecía" hasta actualizar de nuevo).
     */
    List<Terminal> conModosRecientes(List<Terminal> deMp, List<Terminal> anterior) {
        LocalDateTime ahora = LocalDateTime.now();
        modosRecientes.values().removeIf(m -> ahora.isAfter(m.hasta()));
        if (modosRecientes.isEmpty()) return deMp;
        List<Terminal> resultado = new java.util.ArrayList<>(deMp.stream().map(t -> {
            ModoReciente m = modosRecientes.get(t.id());
            return m == null ? t : new Terminal(t.id(), m.modo(), t.posId(), t.storeId(), t.externalPosId());
        }).toList());
        if (anterior != null) {
            Set<String> enMp = deMp.stream().map(Terminal::id).collect(java.util.stream.Collectors.toSet());
            for (Terminal t : anterior) {
                ModoReciente m = modosRecientes.get(t.id());
                if (m != null && !enMp.contains(t.id())) {
                    resultado.add(new Terminal(t.id(), m.modo(), t.posId(), t.storeId(), t.externalPosId()));
                }
            }
        }
        return resultado;
    }

    // ---------- Cobros ----------

    public CobroPointDTO crear(CrearCobroRequest pedido, UsuarioAutenticado operador) {
        if (pedido.claveIdempotencia() == null || pedido.claveIdempotencia().isBlank() || pedido.claveIdempotencia().length() > 64) {
            throw new IllegalArgumentException("Falta la clave del cobro");
        }
        // El POS reintenta con la misma clave si se perdió la respuesta: mismo cobro, nunca otra orden.
        // Antes que el chequeo del módulo: un cobro que ya salió se devuelve aunque lo hayan apagado.
        var existente = repository.findByClaveIdempotencia(pedido.claveIdempotencia());
        if (existente.isPresent()) {
            return toDto(existente.get());
        }
        if (!modulos.activo(Modulo.POINT)) {
            throw new IllegalStateException("El cobro con Point está apagado");
        }
        if (pedido.terminalId() == null || pedido.terminalId().isBlank()) {
            throw new IllegalArgumentException("Elegí el Point en Dispositivos");
        }
        if (!terminalRepository.findById(pedido.terminalId()).map(TerminalPoint::isHabilitado).orElse(false)) {
            throw new IllegalArgumentException("Ese Point no está habilitado para cobrar desde las tablets (Configuración > Points)");
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
        mandarOrden(cobro);
        return toDto(guardar(cobro));
    }

    /**
     * Crea la orden en MP para el cobro, con su clave de idempotencia: si se repite (porque la vez
     * anterior no hubo respuesta), MP devuelve la misma orden en vez de crear otra. Si MP rechaza
     * de forma segura (4xx) el cobro queda en ERROR; si no se sabe qué pasó (sin respuesta, 5xx)
     * queda CREADO sin orden y se vuelve a pedir en la próxima consulta del POS, porque la orden
     * puede estar igual en el Point esperando la tarjeta.
     */
    private void mandarOrden(CobroPoint cobro) {
        try {
            Orden orden = client.crearOrden(cobro.getTerminalId(), cobro.getMonto(), PREFIJO_REFERENCIA + cobro.getId(),
                    cobro.getClaveIdempotencia(), cobro.getFormaPago() == FormaPago.MERCADO_PAGO_QR);
            cobro.setOrdenId(orden.id());
            aplicar(cobro, orden);
            log.info("Cobro con Point {} por {} mandado al Point {} (orden {})", cobro.getId(), cobro.getMonto(),
                    cobro.getTerminalId(), orden.id());
        } catch (PointException e) {
            cobro.setUltimaConsultaMp(LocalDateTime.now());
            if (e.incierto()) {
                cobro.setDetalle(DETALLE_SIN_CONFIRMAR);
                log.warn("Mercado Pago no confirmó la orden del cobro {} (Point {}), se vuelve a pedir: {}",
                        cobro.getId(), cobro.getTerminalId(), e.getMessage());
            } else {
                cobro.setEstado(EstadoCobroPoint.ERROR);
                cobro.setDetalle(recortar(e.getMessage()));
                log.warn("No se pudo mandar el cobro {} al Point {}: {}", cobro.getId(), cobro.getTerminalId(), e.getMessage());
            }
        }
    }

    /** Lo que el POS consulta mientras espera. Si no llegó el webhook, le pregunta a MP (no más de cada 3 s). */
    public CobroPointDTO consultar(Long id, UsuarioAutenticado operador) {
        CobroPoint cobro = cargar(id, operador);
        boolean toca = cobro.getUltimaConsultaMp() == null
                || cobro.getUltimaConsultaMp().plus(ENTRE_CONSULTAS).isBefore(LocalDateTime.now());
        if (cobro.getEstado().enCurso() && toca) {
            if (cobro.getOrdenId() == null) {
                // MP no confirmó la orden al crearla: se pide de nuevo con la misma clave.
                mandarOrden(cobro);
            } else {
                try {
                    aplicar(cobro, client.obtenerOrden(cobro.getOrdenId()));
                } catch (PointException e) {
                    // Se anota igual la consulta: con MP caído o limitando, el POS sigue preguntando
                    // cada 2 s pero a MP se le pregunta como siempre, no más seguido.
                    cobro.setUltimaConsultaMp(LocalDateTime.now());
                    log.warn("No se pudo consultar la orden {} del cobro {} en Mercado Pago: {}", cobro.getOrdenId(), id, e.getMessage());
                }
            }
            cobro = guardar(cobro);
        }
        return toDto(cobro);
    }

    /**
     * Guarda lo que se trajo de MP. Si mientras tanto otro lo cambió (la venta lo vinculó, llegó el
     * webhook), gana lo que ya está guardado: el estado de MP lo vuelve a traer la próxima consulta.
     */
    private CobroPoint guardar(CobroPoint cobro) {
        try {
            return repository.save(cobro);
        } catch (OptimisticLockingFailureException e) {
            log.debug("El cobro con Point {} cambió mientras se consultaba a Mercado Pago", cobro.getId());
            return cobro;
        }
    }

    public CobroPointDTO cancelar(Long id, UsuarioAutenticado operador) {
        CobroPoint cobro = cargar(id, operador);
        if (!cobro.getEstado().enCurso()) {
            return toDto(cobro);
        }
        if (cobro.getOrdenId() == null) {
            // MP nunca confirmó la orden: no hay nada que cancelar allá. Si igual existía, el aviso
            // de MP la encuentra por la referencia y la cancela (ver actualizarDesdeMercadoPago).
            cobro.setEstado(EstadoCobroPoint.CANCELADO);
            cobro.setDetalle("canceled_by_api");
            log.info("Cobro con Point {} cancelado desde la tablet antes de que MP confirmara la orden", id);
            return toDto(guardar(cobro));
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
        return toDto(guardar(cobro));
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
        Devolucion d = devolverEnMercadoPago(cobro);
        // Una venta de la cola offline pudo haberlo vinculado mientras MP procesaba la devolución.
        repository.compraIdActual(id).ifPresent(compraId -> alertas.warn(
                "El cobro con Point {} por {} se devolvió al cliente pero quedó atado a la venta ID {}: revisar esa venta",
                id, cobro.getMonto(), compraId));
        cobro.setEstado(d.estado());
        cobro.setDetalle(d.detalle());
        if (d.estado() != EstadoCobroPoint.DEVUELTO) {
            // Algunas tarjetas sólo se devuelven desde el propio Point: MP acepta el pedido pero no la deja devuelta.
            log.warn("La devolución del cobro con Point {} quedó en {} ({})", id, d.estado(), d.detalle());
        }
        log.info("Cobro con Point {} por {} devuelto al cliente por {}", id, cobro.getMonto(), operador.username());
        return toDto(cobro);
    }

    private record Devolucion(EstadoCobroPoint estado, String detalle) {}

    /**
     * Pide la devolución a MP y deja el resultado guardado en una transacción propia (la plata ya
     * volvió al cliente: eso no se deshace aunque después falle lo demás). Antes se fija si la orden
     * ya figura devuelta (por ejemplo, desde el propio Point), porque un reintento con la misma
     * clave le devolvería a MP la respuesta del primer pedido y no el estado actual.
     */
    private Devolucion devolverEnMercadoPago(CobroPoint cobro) {
        Orden orden;
        try {
            orden = client.obtenerOrden(cobro.getOrdenId());
            if (estadoDe(orden.status()) != EstadoCobroPoint.DEVUELTO) {
                orden = client.devolverOrden(cobro.getOrdenId(), "devolver-" + cobro.getClaveIdempotencia());
            }
        } catch (PointException e) {
            throw new IllegalStateException("Mercado Pago no aceptó la devolución del cobro con Point: " + e.getMessage());
        }
        EstadoCobroPoint estado = estadoDe(orden.status()) != null ? estadoDe(orden.status()) : cobro.getEstado();
        String detalle = recortar(orden.pagoStatusDetail() != null ? orden.pagoStatusDetail() : orden.statusDetail());
        repository.asentarDevolucion(cobro.getId(), estado, detalle, LocalDateTime.now());
        return new Devolucion(estado, detalle);
    }

    /**
     * Al cancelar una venta pagada con el Point se le devuelve la plata al cliente. Va antes de
     * marcarla cancelada y dentro de su transacción: si MP no hace la devolución, la venta no se
     * cancela (y el admin ve por qué). Lo que sí se devolvió queda asentado aunque la cancelación
     * falle después (un pago mixto con el segundo cobro rechazado): al reintentar, se saltea.
     */
    public void devolverDeVenta(Compra venta) {
        for (CobroPoint cobro : repository.findAllByCompraId(venta.getId())) {
            if (cobro.getEstado() != EstadoCobroPoint.APROBADO) continue;
            Devolucion d = devolverEnMercadoPago(cobro);
            String monto = cobro.getMonto().stripTrailingZeros().toPlainString();
            if (d.estado() != EstadoCobroPoint.DEVUELTO) {
                throw new IllegalStateException("Mercado Pago tomó el pedido pero no dio por devuelto el cobro con Point de $"
                        + monto + " (" + d.detalle() + "). Devolvelo desde el propio Point y, cuando figure devuelto, "
                        + "volvé a cancelar la venta.");
            }
            AuditoriaContexto.detalle("Cobro con Point devuelto: $" + monto);
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
        Orden orden = null;
        CobroPoint cobro = repository.findByOrdenId(ordenId).orElse(null);
        if (cobro == null) {
            // Puede ser un cobro cuya orden MP no llegó a confirmar al crearla (sin respuesta, 5xx):
            // se lo encuentra por la referencia que se le puso a la orden.
            orden = client.obtenerOrden(ordenId);
            cobro = cobroDeReferencia(orden.externalReference());
            if (cobro == null) {
                log.info("Aviso de la orden {} de Mercado Pago que no es un cobro con Point de este sistema", ordenId);
                return;
            }
            cobro.setOrdenId(ordenId);
            log.warn("La orden {} del cobro con Point {} apareció recién con el aviso de Mercado Pago", ordenId, cobro.getId());
            if (cobro.getEstado() == EstadoCobroPoint.CANCELADO && estadoDe(orden.status()) != null
                    && estadoDe(orden.status()).enCurso()) {
                // Se canceló en la tablet sin saber que la orden existía: que no quede esperando la tarjeta.
                try {
                    orden = client.cancelarOrden(ordenId, "cancelar-" + cobro.getClaveIdempotencia());
                } catch (PointException e) {
                    alertas.warn("El cobro con Point {} se canceló en la tablet pero su orden {} sigue en el Point: {}",
                            cobro.getId(), ordenId, e.getMessage());
                }
            }
        }
        EstadoCobroPoint antes = cobro.getEstado();
        aplicar(cobro, orden != null ? orden : client.obtenerOrden(ordenId));
        try {
            repository.save(cobro);
        } catch (OptimisticLockingFailureException e) {
            // Otro (la venta, la consulta del POS) lo guardó mientras se le preguntaba a MP: se deja lo
            // suyo, que no pisa la venta vinculada; el estado de MP lo trae la próxima consulta.
            log.info("El cobro con Point {} cambió mientras se procesaba el aviso de Mercado Pago", cobro.getId());
            return;
        }
        if (antes != cobro.getEstado()) {
            log.info("Cobro con Point {}: {} → {} (aviso de Mercado Pago)", cobro.getId(), antes, cobro.getEstado());
        }
    }

    /** "POINT-50" → el cobro 50, si todavía no tiene orden. */
    private CobroPoint cobroDeReferencia(String referencia) {
        if (referencia == null || !referencia.startsWith(PREFIJO_REFERENCIA)) return null;
        try {
            long id = Long.parseLong(referencia.substring(PREFIJO_REFERENCIA.length()));
            return repository.findByIdAndOrdenIdIsNull(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Ata los cobros a la venta que se pagó con ellos (uno, o dos en un pago mixto tarjeta + QR).
     * Corre dentro de la transacción de la venta: si algo no cierra, la venta no se registra (y el
     * POS la muestra rechazada con el motivo).
     *
     * El cobro tiene que estar aprobado, salvo uno que el boletero confirmó a mano en el Point
     * mientras MP todavía no daba el resultado ("Salió aprobado: anotar a mano"): ése se ata igual,
     * para que cuando MP lo apruebe no aparezca como plata cobrada sin venta.
     */
    public void vincular(List<Long> cobroIds, Compra venta) {
        if (cobroIds == null || cobroIds.isEmpty()) return;
        List<CobroPoint> cobros = new java.util.ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(cobroIds)) {
            // Con la fila bloqueada: dos ventas con el mismo cobro no lo pueden atar las dos.
            CobroPoint cobro = repository.findParaVincular(id)
                    .orElseThrow(() -> new IllegalArgumentException("No existe el cobro con Point " + id));
            if (cobro.getCompra() != null && !cobro.getCompra().getId().equals(venta.getId())) {
                throw new IllegalStateException("El cobro con Point " + id + " ya es de la venta " + cobro.getCompra().getCodigoReserva());
            }
            if (venta.getCaja() == null || !cobro.getCaja().getId().equals(venta.getCaja().getId())) {
                throw new IllegalStateException("El cobro con Point " + id + " es de otra caja: no puede pagar esta venta");
            }
            if (cobro.getEstado() != EstadoCobroPoint.APROBADO && !cobro.getEstado().enCurso()) {
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
            // La misma cuenta que usan la caja y los reportes para repartir un pago mixto.
            BigDecimal parte = venta.montoPorForma(forma);
            String nombre = forma == FormaPago.MERCADO_PAGO_QR ? "QR" : "tarjeta";
            if (parte.signum() == 0) {
                throw new IllegalArgumentException("La venta no tiene una parte en " + nombre + " para pagar con el cobro del Point");
            }
            if (parte.compareTo(cobrado) != 0) {
                throw new IllegalStateException("El Point cobró $" + cobrado.toPlainString() + " en " + nombre + " pero esa parte de la venta es $"
                        + parte.toPlainString() + ": revisá la venta o devolvé el cobro");
            }
        });
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
            if (cobro.getCompra() != null && cobro.getEstado().enCurso() && !estado.enCurso() && estado != EstadoCobroPoint.APROBADO) {
                // Se anotó a mano como cobrado ("salió aprobado") y MP dice que no.
                alertas.warn("La venta {} se registró como pagada con el cobro con Point {} por {}, pero Mercado Pago lo dio {}",
                        cobro.getCompra().getCodigoReserva(), cobro.getId(), cobro.getMonto(), estado);
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
            case DETALLE_SIN_CONFIRMAR -> "Mercado Pago no confirmó el cobro todavía: reintentando...";
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
