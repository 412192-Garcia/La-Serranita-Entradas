package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.ControlFacturacionDTO;
import org.example.laserranitaentradas.model.dto.FacturaManualDTO;
import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.DestinoFactura;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.CalculoPrecioService;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.FacturaService;
import org.example.laserranitaentradas.service.factura.ComprobanteFacturaService;
import org.example.laserranitaentradas.service.factura.FacturaPdfGenerator;
import org.example.laserranitaentradas.service.impresion.ImpresionService;
import org.example.laserranitaentradas.service.afip.AfipException;
import org.example.laserranitaentradas.service.afip.AfipSdkClient;
import org.example.laserranitaentradas.service.afip.WsfeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FacturaServiceImpl implements FacturaService {

    private static final Logger log = LoggerFactory.getLogger(FacturaServiceImpl.class);

    private static final BigDecimal DIVISOR_IVA_21 = new BigDecimal("1.21");

    /**
     * Zona de las fechas fiscales (la fecha que va a ARCA, la del servicio y la hora impresa).
     * Explícita a propósito: el contenedor tiene TZ de Argentina, pero si faltara quedaría en UTC y
     * entre las 21 y las 24 la factura saldría con el día siguiente.
     */
    public static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int CONCEPTO_PRODUCTOS = 1;
    private static final int CONCEPTO_SERVICIOS = 2;
    private static final int CONCEPTO_PRODUCTOS_Y_SERVICIOS = 3;
    /** Pasados estos intentos fallidos transitorios la factura pasa a ERROR para que alguien la
     * mire: con el backoff de abajo son ~15 horas reintentando. */
    private static final int MAX_INTENTOS = 20;
    private static final long BACKOFF_MAX_MINUTOS = 60;

    private final FacturaRepository facturaRepository;
    private final WsfeService wsfe;
    private final AfipSdkClient afipClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    /** Siempre en una transacción propia (la factura de una compra online, ver solicitarOnline). */
    private final TransactionTemplate txNueva;
    private final EntityManager em;
    private final ComprobanteFacturaService comprobanteFacturaService;
    private final FacturaPdfGenerator pdfGenerator;
    private final EmailService emailService;
    private final ImpresionService impresionService;
    private final CalculoPrecioService calculoPrecioService;

    /**
     * ARCA numera en orden estricto por punto de venta: dos emisiones a la vez pedirían el mismo
     * "último + 1" y una de las dos sería rechazada. Se emite de a una. Un lock en memoria
     * alcanza porque el backend corre como una sola instancia; si algún día se escala a varias,
     * esto tiene que pasar a un lock en la base.
     */
    private final ReentrantLock lockEmision = new ReentrantLock();

    /** Punto de venta de ARCA para la boletería (tipo "Web Services"). Distinto del 0010 de la
     * cantina: comparten CUIT pero cada uno lleva su propia numeración. */
    @Value("${afip.punto-venta.boleteria:1}")
    private int puntoVentaBoleteria;

    /** Punto de venta de las compras online (pagadas con Mercado Pago). Vacío = no se facturan. */
    @Value("${afip.punto-venta.online:}")
    private String puntoVentaOnline;

    public FacturaServiceImpl(FacturaRepository facturaRepository, WsfeService wsfe, AfipSdkClient afipClient,
                              ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager,
                              EntityManager em,
                              ComprobanteFacturaService comprobanteFacturaService, FacturaPdfGenerator pdfGenerator,
                              EmailService emailService, ImpresionService impresionService,
                              CalculoPrecioService calculoPrecioService) {
        this.facturaRepository = facturaRepository;
        this.wsfe = wsfe;
        this.afipClient = afipClient;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.txNueva = new TransactionTemplate(transactionManager);
        this.txNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.em = em;
        this.comprobanteFacturaService = comprobanteFacturaService;
        this.pdfGenerator = pdfGenerator;
        this.emailService = emailService;
        this.impresionService = impresionService;
        this.calculoPrecioService = calculoPrecioService;
    }

    @Override
    public boolean estaHabilitada() {
        return afipClient.estaConfigurado();
    }

    @Override
    public Optional<Factura> solicitar(Compra compra, FacturacionPosDTO pedido) {
        if (pedido == null || pedido.getDestino() == null) {
            return Optional.empty();
        }
        if (pedido.getDestino() == DestinoFactura.NINGUNO) {
            // Sólo para las facturas que arma el sistema (refacturar una venta corregida): pedida
            // desde afuera sería una factura que no se imprime ni se manda.
            throw new IllegalArgumentException("La factura se imprime o se manda por mail");
        }
        String email = pedido.getEmail() == null ? null : pedido.getEmail().trim();
        boolean sinEmail = email == null || email.isEmpty();
        // "Enviar por mail" con el campo vacío = no facturar. No es un error: es lo que pasa
        // casi siempre al cobrar en efectivo, el cajero ni toca el campo.
        if (pedido.getDestino() == DestinoFactura.MAIL && sinEmail) {
            return Optional.empty();
        }
        if (pedido.getDestino() == DestinoFactura.MAIL && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El email para enviar la factura no es válido");
        }
        if (compra.getMontoTotal() == null || compra.getMontoTotal().signum() <= 0) {
            return Optional.empty();
        }
        if (!estaHabilitada()) {
            log.warn("Se pidió factura para la compra ID {} pero la facturación no está configurada (falta AFIPSDK_ACCESS_TOKEN)", compra.getId());
            return Optional.empty();
        }
        // Reintento de la cola offline con la misma venta: ya tiene su factura.
        Optional<Factura> existente = facturaVigente(compra.getId());
        if (existente.isPresent()) {
            return existente;
        }

        String impresora = pedido.getDestino() == DestinoFactura.IMPRIMIR && pedido.getImpresora() != null
                && !pedido.getImpresora().isBlank() ? pedido.getImpresora().trim() : null;
        return Optional.of(crearFactura(compra, pedido.getDestino(),
                pedido.getDestino() == DestinoFactura.MAIL ? email : null, impresora));
    }

    /** Crea una factura B PENDIENTE por el total actual de la compra y la deja lista para emitir
     * cuando commitee la transacción en curso (la de la venta, o la de la cancelación/edición). */
    private Factura crearFactura(Compra compra, DestinoFactura destino, String email, String impresora) {
        return crearFactura(compra, destino, email, impresora, puntoVentaBoleteria);
    }

    private Factura crearFactura(Compra compra, DestinoFactura destino, String email, String impresora, int puntoVenta) {
        BigDecimal total = compra.getMontoTotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal neto = total.divide(DIVISOR_IVA_21, 2, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(neto);

        Factura factura = Factura.builder()
                .compra(compra)
                .destino(destino)
                .email(email)
                .impresora(impresora)
                .puntoVenta(puntoVenta)
                .tipoComprobante(WsfeService.CBTE_TIPO_FACTURA_B)
                .concepto(concepto(compra.getDetalles()))
                .fechaServicio(compra.getFechaVisita() != null ? compra.getFechaVisita() : LocalDate.now(ZONA_ARGENTINA))
                .importeTotal(total)
                .importeNeto(neto)
                .importeIva(iva)
                .detalle(detalleTexto(compra, total))
                .build();
        factura = facturaRepository.save(factura);
        // Se emite recién cuando commitea la transacción (ver EmisionFacturasScheduler): si hiciera
        // rollback, no puede quedar un comprobante autorizado en ARCA.
        eventPublisher.publishEvent(new FacturaSolicitadaEvent(factura.getId()));
        return factura;
    }

    /** La factura que hoy vale para la compra: la última, salvo que esté anulada o en camino de
     * anularse. */
    private Optional<Factura> facturaVigente(Long compraId) {
        return facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(compraId, WsfeService.CBTE_TIPO_FACTURA_B)
                .filter(f -> f.getEstado() != EstadoFactura.ANULADA && !Boolean.TRUE.equals(f.getAnulacionPedida()));
    }

    /**
     * Copia de los ítems al facturar: "cantidad<TAB>descripción<TAB>subtotal" por línea, más una línea
     * "0<TAB>Descuento<TAB>-monto" si hubo descuento, para que el ticket muestre cuánto salió cada
     * cosa y la cuenta cierre con el total.
     *
     * El subtotal de una entrada se calcula con la misma regla de precios que usó la venta (precio de
     * grupo si se cobró en efectivo, ver CompraServiceImpl#construirDetalles), y el de un artículo con
     * el precio que cargó el cajero. Si aun así la suma diera MENOS que el total (no debería: sería un
     * precio que cambió entre la venta y la factura), se guardan las líneas sin subtotal: un ticket
     * con números que no cierran es peor que uno sin el detalle.
     */
    private String detalleTexto(Compra compra, BigDecimal total) {
        List<CompraDetalle> detalles = compra.getDetalles();
        if (detalles == null) return null;
        List<String> descripciones = new ArrayList<>();
        List<BigDecimal> subtotales = new ArrayList<>();
        boolean todosConPrecio = true;
        for (CompraDetalle d : detalles) {
            String descripcion = d.getTipoEntrada() != null ? d.getTipoEntrada().getNombre()
                    : d.getArticuloVario() != null ? d.getArticuloVario().getNombre()
                    : d.getDescripcionLibre();
            if (descripcion == null || descripcion.isBlank()) descripcion = "Artículo";
            descripciones.add(descripcion.replace('\n', ' ').replace('\t', ' '));
            BigDecimal subtotal = subtotal(d, compra);
            subtotales.add(subtotal);
            if (subtotal == null) todosConPrecio = false;
        }

        BigDecimal suma = subtotales.stream().filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuento = suma.subtract(total);
        boolean cierra = todosConPrecio && descuento.signum() >= 0;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < detalles.size(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(detalles.get(i).getCantidad()).append('\t').append(descripciones.get(i));
            if (cierra) sb.append('\t').append(subtotales.get(i).setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (cierra && descuento.signum() > 0) {
            sb.append('\n').append(0).append('\t').append("Descuento").append('\t').append(descuento.negate().toPlainString());
        }
        String texto = sb.toString();
        return texto.length() > 2000 ? texto.substring(0, 2000) : texto;
    }

    private BigDecimal subtotal(CompraDetalle d, Compra compra) {
        try {
            if (d.getTipoEntrada() != null) {
                if (compra.getFormaPago() == null) return null;
                // Con el precio de lista congelado al armar la compra (una reserva cobrada tras un
                // aumento no puede mostrar un "descuento" que en realidad es el aumento).
                return d.getPrecioUnitario() != null
                        ? calculoPrecioService.calcularTotal(d.getTipoEntrada(), d.getCantidad(), compra.getFormaPago(), d.getPrecioUnitario())
                        : calculoPrecioService.calcularTotal(d.getTipoEntrada(), d.getCantidad(), compra.getFormaPago());
            }
            return d.getPrecioUnitario() == null ? null : d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
        } catch (RuntimeException e) {
            log.warn("No se pudo calcular el subtotal de una línea de la compra ID {}", compra.getId(), e);
            return null;
        }
    }

    // ---------- Cancelación y edición de ventas facturadas ----------

    @Override
    public void alCancelarVenta(Compra compra) {
        bloquear(compra);
        facturaVigente(compra.getId()).ifPresent(this::anular);
    }

    @Override
    public void alEditarVenta(Compra compra, BigDecimal montoAnterior) {
        bloquear(compra);
        facturaVigente(compra.getId()).ifPresent(vieja -> {
            boolean mismoTotal = montoAnterior != null && compra.getMontoTotal() != null
                    && montoAnterior.compareTo(compra.getMontoTotal()) == 0;
            // Mismo total y mismos ítems (ej. sólo se corrigió la forma de pago sin mover precios):
            // la factura sigue siendo correcta. Si cambió lo que dice (otro artículo del mismo
            // precio, otras cantidades), va nota de crédito y factura nueva igual.
            if (mismoTotal && (vieja.getDetalle() == null
                    || vieja.getDetalle().equals(detalleTexto(compra, compra.getMontoTotal())))) {
                return;
            }
            anular(vieja);
            if (compra.getMontoTotal() != null && compra.getMontoTotal().signum() > 0) {
                // La corrección la hace un admin desde la oficina: si iba por mail se le manda la
                // nueva al mismo cliente; si era impresa no se imprime sola en la boletería.
                boolean porMail = vieja.getDestino() == DestinoFactura.MAIL;
                crearFactura(compra, porMail ? DestinoFactura.MAIL : DestinoFactura.NINGUNO,
                        porMail ? vieja.getEmail() : null, null);
            }
        });
    }

    /**
     * Deja sin efecto una factura. Si nunca llegó a ARCA, alcanza con anularla acá. Si ya está
     * autorizada, ARCA no permite borrarla: se emite una nota de crédito por el total. Y si está
     * en camino (pidió número y no se sabe si quedó autorizada) se marca, y lo resuelve la
     * emisión: nota de crédito si quedó autorizada, anulada si no.
     *
     * Corre dentro de la transacción de la cancelación/edición, con la fila bloqueada: la emisión
     * reserva el número también con la fila bloqueada, así que las dos cosas no se cruzan.
     */
    private void anular(Factura vigente) {
        Factura f = bloquear(vigente.getId()).orElse(vigente);
        if (f.getEstado() == EstadoFactura.ANULADA || Boolean.TRUE.equals(f.getAnulacionPedida())) {
            return;
        }
        // Un ticket en cola (agente apagado) no tiene que salir después de la nota de crédito.
        impresionService.cancelarTrabajos(f.getId());
        if (f.getEstado() != EstadoFactura.EMITIDA && f.getNumeroIntentado() == null) {
            f.setEstado(EstadoFactura.ANULADA);
            f.setProximoIntento(null);
            facturaRepository.save(f);
            return;
        }
        f.setAnulacionPedida(true);
        if (f.getEstado() == EstadoFactura.EMITIDA) {
            facturaRepository.save(f);
            Factura nc = facturaRepository.save(notaDeCredito(f));
            eventPublisher.publishEvent(new FacturaSolicitadaEvent(nc.getId()));
            return;
        }
        // En ERROR con número pedido tampoco se sabe si quedó autorizada: vuelve a la cola para
        // que la emisión lo consulte y lo resuelva.
        f.setEstado(EstadoFactura.PENDIENTE);
        f.setIntentos(0);
        f.setProximoIntento(null);
        facturaRepository.save(f);
        eventPublisher.publishEvent(new FacturaSolicitadaEvent(f.getId()));
    }

    /**
     * La factura con su fila bloqueada hasta el fin de la transacción, y RELEÍDA de la base.
     *
     * Todo cambio de estado pasa por acá: así la cancelación de una venta y la emisión contra ARCA
     * (que corren en hilos distintos) no se pisan. El refresh es la parte importante: un SELECT
     * ... FOR UPDATE bloquea la fila, pero si la entidad ya estaba cargada en la sesión (la
     * cancelación la busca antes, y con open-in-view la sesión dura todo el request) Hibernate
     * devuelve esa instancia vieja sin pisarla con lo que acaba de leer, y se decidía con datos
     * desactualizados.
     */
    private Optional<Factura> bloquear(Long id) {
        return facturaRepository.findById(id).map(f -> {
            em.refresh(f, LockModeType.PESSIMISTIC_WRITE);
            return f;
        });
    }

    private Factura notaDeCredito(Factura factura) {
        return Factura.builder()
                .compra(factura.getCompra())
                .comprobanteAsociado(factura)
                .destino(DestinoFactura.NINGUNO)
                .puntoVenta(factura.getPuntoVenta())
                .tipoComprobante(WsfeService.CBTE_TIPO_NOTA_CREDITO_B)
                .concepto(factura.getConcepto())
                .fechaServicio(factura.getFechaServicio())
                .importeTotal(factura.getImporteTotal())
                .importeNeto(factura.getImporteNeto())
                .importeIva(factura.getImporteIva())
                .detalle(factura.getDetalle())
                .build();
    }

    /** Entradas = servicio; artículos varios (souvenirs) = producto. */
    private static int concepto(List<CompraDetalle> detalles) {
        boolean hayServicio = detalles != null && detalles.stream().anyMatch(d -> d.getTipoEntrada() != null);
        boolean hayProducto = detalles != null && detalles.stream().anyMatch(d -> d.getTipoEntrada() == null);
        if (hayServicio && hayProducto) return CONCEPTO_PRODUCTOS_Y_SERVICIOS;
        return hayProducto ? CONCEPTO_PRODUCTOS : CONCEPTO_SERVICIOS;
    }

    @Override
    public void emitirPendientes() {
        if (!estaHabilitada()) {
            return;
        }
        for (Long id : facturaRepository.idsListosParaEmitir(EstadoFactura.PENDIENTE, LocalDateTime.now())) {
            emitir(id);
        }
    }

    @Override
    public void emitir(Long facturaId) {
        lockEmision.lock();
        try {
            Factura factura = facturaRepository.findById(facturaId).orElse(null);
            if (factura == null || factura.getEstado() != EstadoFactura.PENDIENTE) {
                return;
            }
            emitirBajoLock(factura);
        } finally {
            lockEmision.unlock();
        }
    }

    private void emitirBajoLock(Factura factura) {
        Long id = factura.getId();
        int pv = factura.getPuntoVenta();
        int tipo = factura.getTipoComprobante();
        boolean anular = Boolean.TRUE.equals(factura.getAnulacionPedida());
        try {
            if (anular && factura.getNumeroIntentado() == null) {
                marcarAnulada(id);
                return;
            }
            // Un intento anterior mandó el pedido con este número y nunca supo la respuesta
            // (timeout, corte): antes de pedir otro número hay que preguntar si ese quedó
            // autorizado. Si no se hace esto, un timeout = la misma venta facturada dos veces.
            if (factura.getNumeroIntentado() != null) {
                long intentado = factura.getNumeroIntentado();
                Optional<WsfeService.ComprobanteAutorizado> previo = wsfe.consultar(pv, tipo, intentado);
                // Mismo importe no alcanza para saber que es nuestro: mientras ésta esperaba su
                // reintento, otra factura de acá pudo haber sacado ese mismo número por el mismo
                // monto. Si otra ya lo tiene, no es de ésta.
                boolean esDeOtra = previo.isPresent()
                        && facturaRepository.existsByPuntoVentaAndTipoComprobanteAndNumeroAndIdNot(pv, tipo, intentado, id);
                if (previo.isPresent() && !esDeOtra && previo.get().total().compareTo(factura.getImporteTotal()) == 0) {
                    log.info("La factura ID {} ya había quedado autorizada en ARCA con el número {}", id, intentado);
                    marcarEmitida(id, intentado, previo.get().cae(), previo.get().caeVencimiento(), previo.get().fecha());
                    return;
                }
                if (anular) {
                    // No había quedado autorizada y la venta ya se canceló: no se pide otro número.
                    marcarAnulada(id);
                    return;
                }
            }

            resolverReservasDeOtras(pv, tipo, id);
            // Primer intento con el número que sale de la base (en producción); si ARCA lo rechaza
            // por no ser el próximo, un segundo intento enseguida con el que informa ARCA.
            boolean preguntarAArca = false;
            for (int intento = 1; intento <= 2; intento++) {
                Numero proximo = proximoNumero(pv, tipo, preguntarAArca);
                long numero = proximo.valor();
                LocalDate fecha = LocalDate.now(ZONA_ARGENTINA);
                // Se reserva el número ANTES de pedir el CAE, en su propia transacción y con la fila
                // bloqueada: si la respuesta se pierde, el próximo intento sabe qué número consultar.
                // Y si mientras se consultaba el último número la venta se canceló, acá se ve y no se
                // pide nada (antes se reservaba igual y se autorizaba la factura de una venta cancelada).
                boolean reservado = Boolean.TRUE.equals(tx.execute(st -> bloquear(id).map(f -> {
                    if (f.getEstado() != EstadoFactura.PENDIENTE || Boolean.TRUE.equals(f.getAnulacionPedida())) {
                        return false;
                    }
                    f.setNumeroIntentado(numero);
                    f.setFechaEmision(fecha);
                    facturaRepository.save(f);
                    return true;
                }).orElse(false)));
                if (!reservado) {
                    log.info("Factura ID {}: la venta se canceló o cambió mientras se emitía, no se pide CAE", id);
                    marcarAnulada(id);
                    return;
                }

                WsfeService.ResultadoCae resultado = wsfe.solicitarCae(new WsfeService.SolicitudCae(
                        pv, tipo, factura.getConcepto(), numero, fecha, factura.getFechaServicio(),
                        factura.getImporteTotal(), factura.getImporteNeto(), factura.getImporteIva(),
                        asociado(factura)));

                if (resultado.aprobado()) {
                    marcarEmitida(id, numero, resultado.cae(), resultado.caeVencimiento(), fecha);
                    log.info("Comprobante ID {} (tipo {}) emitido: {}-{} CAE {}", id, tipo, pv, numero, resultado.cae());
                    return;
                }
                if (resultado.codigos().contains(WsfeService.ERROR_NUMERO_NO_ES_EL_PROXIMO)) {
                    liberarNumero(id);
                    if (proximo.deLaBase()) {
                        // La base quedó desfasada de ARCA (ej. se restauró un backup viejo): se le
                        // pregunta a ARCA el último y se reintenta ya, sin esperar la cola.
                        log.warn("Factura ID {}: ARCA rechazó el número {} sacado de la base; se pide el último a ARCA", id, numero);
                        preguntarAArca = true;
                        continue;
                    }
                    // Otro comprobante tomó ese número entre la consulta y el pedido (no debería
                    // pasar con un punto de venta propio, pero sí en homologación, donde el CUIT de
                    // prueba es compartido). No es un error de la factura: se reintenta con el
                    // número que siga.
                    reprogramar(id, "ARCA: " + String.join(" | ", resultado.mensajes()));
                    return;
                }
                // Rechazada: ARCA no usó el número, así que se libera para no consultarlo en vano.
                marcarError(id, "ARCA rechazó la factura: " + String.join(" | ", resultado.mensajes()), true);
                return;
            }
            reprogramar(id, "ARCA rechazó dos veces el número de comprobante");
        } catch (AfipException e) {
            if (e.esTransitorio()) {
                reprogramar(id, e.getMessage());
            } else {
                marcarError(id, e.getMessage(), false);
            }
        } catch (Exception e) {
            log.error("Error inesperado emitiendo la factura ID {}", id, e);
            reprogramar(id, "Error inesperado: " + e.getMessage());
        }
    }

    /**
     * Antes de pedir un número, resuelve las otras facturas de este punto de venta que pidieron uno
     * y no supieron la respuesta (timeout): o quedó autorizado y es suyo, o se libera. Si no, ésta
     * podría reservar ese mismo número y, si las dos pierden la respuesta, una quedarse con la
     * autorización de la otra (o la misma venta facturada dos veces).
     */
    private void resolverReservasDeOtras(int pv, int tipo, Long id) {
        for (Long otraId : facturaRepository.reservasSinResolver(pv, tipo, id,
                List.of(EstadoFactura.PENDIENTE, EstadoFactura.ERROR))) {
            Factura otra = facturaRepository.findById(otraId).orElse(null);
            if (otra == null || otra.getNumeroIntentado() == null) continue;
            long numeroOtra = otra.getNumeroIntentado();
            Optional<WsfeService.ComprobanteAutorizado> previo = wsfe.consultar(pv, tipo, numeroOtra);
            boolean esDeOtraMas = previo.isPresent()
                    && facturaRepository.existsByPuntoVentaAndTipoComprobanteAndNumeroAndIdNot(pv, tipo, numeroOtra, otraId);
            if (previo.isPresent() && !esDeOtraMas && previo.get().total().compareTo(otra.getImporteTotal()) == 0) {
                log.info("Factura ID {}: su número {} había quedado autorizado (resuelto antes de emitir la ID {})", otraId, numeroOtra, id);
                marcarEmitida(otraId, numeroOtra, previo.get().cae(), previo.get().caeVencimiento(), previo.get().fecha());
            } else if (Boolean.TRUE.equals(otra.getAnulacionPedida())) {
                marcarAnulada(otraId);
            } else {
                liberarNumero(otraId);
            }
        }
    }

    private record Emision(Factura factura, Long notaDeCreditoId) {}

    private void marcarEmitida(Long id, long numero, String cae, LocalDate caeVencimiento, LocalDate fecha) {
        // Autorización y (si la venta ya se había cancelado) nota de crédito en la MISMA
        // transacción: si el backend se cae justo después, la nota de crédito ya quedó PENDIENTE y
        // el job la emite. Antes eran dos commits y en el medio podía quedar una venta cancelada
        // facturada sin su nota de crédito, sin nada que la reparara.
        Emision emision = tx.execute(s -> bloquear(id).map(f -> {
            f.setEstado(EstadoFactura.EMITIDA);
            f.setNumero(numero);
            f.setNumeroIntentado(numero);
            f.setCae(cae);
            f.setCaeVencimiento(caeVencimiento);
            f.setFechaEmision(fecha);
            f.setEmitidaEn(LocalDateTime.now(ZONA_ARGENTINA).withNano(0));
            f.setUltimoError(null);
            f.setProximoIntento(null);
            Factura guardada = facturaRepository.save(f);
            Long ncId = Boolean.TRUE.equals(guardada.getAnulacionPedida())
                    ? facturaRepository.save(notaDeCredito(guardada)).getId()
                    : null;
            return new Emision(guardada, ncId);
        }).orElse(null));
        if (emision == null) return;
        if (emision.notaDeCreditoId() != null) {
            // Quedó autorizada pero la venta ya se había cancelado: ni mail ni ticket, va directo
            // la nota de crédito.
            emitir(emision.notaDeCreditoId());
            return;
        }
        Factura emitida = emision.factura();
        // Ya commiteada como EMITIDA: el mail sale en segundo plano (si falla, queda como rechazo
        // para reenviar) y el ticket va a la cola del agente de impresión. En los dos casos la
        // factura sigue siendo válida aunque el envío falle.
        if (emitida.getDestino() == DestinoFactura.MAIL) {
            emailService.enviarFactura(id);
        } else if (emitida.getDestino() == DestinoFactura.IMPRIMIR) {
            try {
                impresionService.imprimirFactura(id, emitida.getImpresora());
            } catch (Exception e) {
                // Un problema con la impresora no tumba la factura: desde el POS se reimprime.
                log.warn("No se pudo mandar a imprimir la factura ID {}", id, e);
            }
        }
    }

    private void marcarAnulada(Long id) {
        tx.executeWithoutResult(s -> bloquear(id).ifPresent(f -> {
            if (f.getEstado() == EstadoFactura.EMITIDA) return;
            log.info("Factura ID {} anulada antes de autorizarse (la venta se canceló o cambió)", id);
            f.setEstado(EstadoFactura.ANULADA);
            f.setNumeroIntentado(null);
            f.setProximoIntento(null);
            facturaRepository.save(f);
        }));
    }

    /** El comprobante que anula una nota de crédito, para el CbtesAsoc del pedido. */
    private WsfeService.Asociado asociado(Factura f) {
        if (f.getComprobanteAsociado() == null) return null;
        Factura a = facturaRepository.findById(f.getComprobanteAsociado().getId())
                .orElseThrow(() -> new IllegalStateException("No se encontró la factura asociada a la nota de crédito " + f.getId()));
        return new WsfeService.Asociado(a.getTipoComprobante(), a.getPuntoVenta(), a.getNumero(), a.getFechaEmision());
    }

    /** Número a pedir y de dónde salió (si vino de la base, un rechazo se corrige preguntando a ARCA). */
    private record Numero(long valor, boolean deLaBase) {}

    /**
     * El próximo número del punto de venta. En producción sale de la base (el último emitido + 1):
     * el punto de venta web services es exclusivo de este sistema (desde "Comprobantes en línea"
     * no se puede usar), así que nadie más saca números ahí. Ahorra un request a AfipSDK por
     * factura. Se le pregunta a ARCA si la base no tiene ninguno (la primera factura) o si ARCA
     * rechazó el número de la base.
     *
     * En homologación se pregunta siempre: el CUIT de prueba de AfipSDK es compartido y otros
     * sacan números en el mismo punto de venta, así que el de la base casi nunca sería el próximo.
     */
    private Numero proximoNumero(int pv, int tipo, boolean preguntarAArca) {
        if (!preguntarAArca && afipClient.esProduccion()) {
            Long ultimo = facturaRepository.ultimoNumeroEmitido(pv, tipo);
            if (ultimo != null) {
                return new Numero(ultimo + 1, true);
            }
        }
        return new Numero(wsfe.ultimoAutorizado(pv, tipo) + 1, false);
    }

    private void liberarNumero(Long id) {
        tx.executeWithoutResult(s -> bloquear(id).ifPresent(f -> {
            f.setNumeroIntentado(null);
            facturaRepository.save(f);
        }));
    }

    private void marcarError(Long id, String mensaje, boolean liberarNumero) {
        tx.executeWithoutResult(s -> bloquear(id).ifPresent(f -> {
            // Si mientras se emitía la venta se canceló (ANULADA), una falla que llega tarde no la
            // puede volver a ERROR: un reintento manual después autorizaría una venta cancelada.
            if (f.getEstado() != EstadoFactura.PENDIENTE) return;
            f.setIntentos(f.getIntentos() + 1);
            f.setUltimoError(recortar(mensaje));
            f.setProximoIntento(null);
            if (liberarNumero) {
                f.setNumeroIntentado(null);
            }
            // Rechazada (sin número usado) y con la venta ya cancelada: no hay nada que corregir,
            // queda anulada en vez de esperar que un admin la reintente.
            boolean anulada = Boolean.TRUE.equals(f.getAnulacionPedida()) && f.getNumeroIntentado() == null;
            f.setEstado(anulada ? EstadoFactura.ANULADA : EstadoFactura.ERROR);
            log.warn("Factura ID {} en {}: {}", id, f.getEstado(), mensaje);
            facturaRepository.save(f);
        }));
    }

    private void reprogramar(Long id, String mensaje) {
        tx.executeWithoutResult(s -> bloquear(id).ifPresent(f -> {
            if (f.getEstado() != EstadoFactura.PENDIENTE) return;
            int intentos = f.getIntentos() + 1;
            f.setIntentos(intentos);
            f.setUltimoError(recortar(mensaje));
            if (intentos >= MAX_INTENTOS) {
                log.warn("Factura ID {} pasa a ERROR después de {} intentos: {}", id, intentos, mensaje);
                f.setEstado(EstadoFactura.ERROR);
                f.setProximoIntento(null);
            } else {
                // 1, 2, 4, 8... minutos, con techo de una hora.
                long minutos = Math.min(1L << Math.min(intentos - 1, 10), BACKOFF_MAX_MINUTOS);
                f.setProximoIntento(LocalDateTime.now().plusMinutes(minutos));
                log.info("Factura ID {} sin emitir (intento {}), se reintenta en {} min: {}", id, intentos, minutos, mensaje);
            }
            facturaRepository.save(f);
        }));
    }

    @Override
    public FacturaResponseDTO reintentar(Long facturaId) {
        // Sólo ERROR: una ANULADA es de una venta cancelada y reintentarla autorizaría una factura
        // sin su nota de crédito. Se chequea con la fila bloqueada, no antes.
        tx.executeWithoutResult(s -> {
            Factura f = bloquear(facturaId)
                    .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
            if (f.getEstado() != EstadoFactura.ERROR) {
                throw new IllegalStateException("Sólo se pueden reintentar facturas en ERROR (esta está " + f.getEstado() + ")");
            }
            f.setEstado(EstadoFactura.PENDIENTE);
            f.setIntentos(0);
            f.setProximoIntento(null);
            facturaRepository.save(f);
        });
        emitir(facturaId);
        return facturaRepository.findById(facturaId).map(this::toDto).orElseThrow();
    }

    @Override
    public void reenviarMail(Long facturaId) {
        Factura factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (factura.getEstado() != EstadoFactura.EMITIDA) {
            throw new IllegalStateException("La factura todavía no está emitida");
        }
        emailService.enviarFacturaOFallar(facturaId);
    }

    @Override
    public byte[] generarPdf(Long facturaId) {
        return pdfGenerator.generar(comprobanteFacturaService.armar(facturaId));
    }

    private static final int MAX_ITEMS_MANUAL = 30;
    private static final int MAX_DESCRIPCION_MANUAL = 80;

    @Override
    @Transactional
    public FacturaResponseDTO emitirManual(FacturaManualDTO pedido) {
        if (!estaHabilitada()) {
            throw new IllegalStateException("La facturación no está configurada");
        }
        if (pedido == null || pedido.items() == null || pedido.items().isEmpty()) {
            throw new IllegalArgumentException("Cargá al menos un ítem");
        }
        if (pedido.items().size() > MAX_ITEMS_MANUAL) {
            throw new IllegalArgumentException("Máximo " + MAX_ITEMS_MANUAL + " ítems por factura");
        }
        DestinoFactura destino = pedido.destino() == null ? DestinoFactura.NINGUNO : pedido.destino();
        String email = pedido.email() == null ? null : pedido.email().trim();
        if (destino == DestinoFactura.MAIL && (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw new IllegalArgumentException("El email para enviar la factura no es válido");
        }

        StringBuilder detalle = new StringBuilder();
        BigDecimal total = BigDecimal.ZERO;
        boolean hayServicio = false;
        boolean hayProducto = false;
        for (FacturaManualDTO.Item item : pedido.items()) {
            if (item == null || item.cantidad() == null || item.cantidad() < 1 || item.cantidad() > 9999) {
                throw new IllegalArgumentException("Cada ítem necesita una cantidad entre 1 y 9999");
            }
            String descripcion = item.descripcion() == null ? "" : item.descripcion().replaceAll("[\\t\\r\\n]+", " ").trim();
            if (descripcion.isEmpty()) {
                throw new IllegalArgumentException("Cada ítem necesita una descripción");
            }
            if (descripcion.length() > MAX_DESCRIPCION_MANUAL) {
                throw new IllegalArgumentException("La descripción \"" + descripcion.substring(0, 20) + "…\" es muy larga (máximo "
                        + MAX_DESCRIPCION_MANUAL + " caracteres)");
            }
            if (item.subtotal() == null || item.subtotal().signum() <= 0) {
                throw new IllegalArgumentException("El importe de \"" + descripcion + "\" tiene que ser mayor a cero");
            }
            BigDecimal subtotal = item.subtotal().setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);
            if ("ARTICULO".equalsIgnoreCase(item.tipo())) hayProducto = true;
            else hayServicio = true;
            if (detalle.length() > 0) detalle.append('\n');
            detalle.append(item.cantidad()).append('\t').append(descripcion).append('\t').append(subtotal.toPlainString());
        }
        if (detalle.length() > 2000) {
            throw new IllegalArgumentException("Demasiado texto en los ítems: acortá las descripciones o dividí la factura");
        }

        BigDecimal neto = total.divide(DIVISOR_IVA_21, 2, RoundingMode.HALF_UP);
        Factura factura = facturaRepository.save(Factura.builder()
                .destino(destino)
                .email(destino == DestinoFactura.MAIL ? email : null)
                .impresora(destino == DestinoFactura.IMPRIMIR ? pedido.impresora() : null)
                .puntoVenta(puntoVentaBoleteria)
                .tipoComprobante(WsfeService.CBTE_TIPO_FACTURA_B)
                .concepto(hayServicio && hayProducto ? CONCEPTO_PRODUCTOS_Y_SERVICIOS
                        : hayProducto ? CONCEPTO_PRODUCTOS : CONCEPTO_SERVICIOS)
                .fechaServicio(LocalDate.now(ZONA_ARGENTINA))
                .importeTotal(total)
                .importeNeto(neto)
                .importeIva(total.subtract(neto))
                .detalle(detalle.toString())
                .build());
        log.info("Factura manual ID {} pedida por {} ({} ítems)", factura.getId(), total, pedido.items().size());
        eventPublisher.publishEvent(new FacturaSolicitadaEvent(factura.getId()));
        return toDto(factura);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaResponseDTO> manuales() {
        List<Factura> facturas = facturaRepository.findTop50ByCompraIsNullAndTipoComprobanteOrderByIdDesc(WsfeService.CBTE_TIPO_FACTURA_B);
        Map<Long, TrabajoImpresion> trabajos = impresionService.ultimosTrabajos(facturas.stream().map(Factura::getId).toList());
        return facturas.stream().map(f -> toDto(f, Optional.ofNullable(trabajos.get(f.getId())))).toList();
    }

    @Override
    @Transactional
    public void anularFactura(Long facturaId) {
        Factura f = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (f.getTipoComprobante() != WsfeService.CBTE_TIPO_FACTURA_B) {
            throw new IllegalStateException("Sólo se anulan facturas (una nota de crédito no se anula)");
        }
        if (f.getEstado() == EstadoFactura.ANULADA || Boolean.TRUE.equals(f.getAnulacionPedida())) {
            throw new IllegalStateException("Esta factura ya está anulada");
        }
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.referencia(f.getNumero() != null
                ? String.format("B %04d-%08d", f.getPuntoVenta(), f.getNumero()) : "(sin número)");
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.detalle("Total: $" + f.getImporteTotal().stripTrailingZeros().toPlainString()
                + (f.getCompra() != null ? " · compra #" + f.getCompra().getCodigoReserva() : " · manual"));
        anular(f);
    }

    @Override
    public boolean solicitarOnline(Compra compra) {
        if (compra == null || compra.getId() == null || !facturacionOnlineActiva()
                || compra.getFormaPago() != FormaPago.MERCADO_PAGO
                || compra.getMontoTotal() == null || compra.getMontoTotal().signum() <= 0) {
            return false;
        }
        Long compraId = compra.getId();
        // Recién cuando la aprobación del pago ya quedó guardada, y en segundo plano (ver
        // EmisionFacturasScheduler): adentro de la misma transacción, un error de la factura la
        // desharía y se perdería la aprobación de alguien que ya pagó.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new CompraOnlinePagadaEvent(compraId));
                }
            });
        } else {
            eventPublisher.publishEvent(new CompraOnlinePagadaEvent(compraId));
        }
        return true;
    }

    /**
     * Pide la factura, espera a ARCA (normalmente 2-3 s) y manda la confirmación con la factura
     * adjunta. Si ARCA tarda o falla, la confirmación sale sola y la factura queda para mandarse
     * sola cuando se emita. Pase lo que pase, la confirmación sale (el finally).
     */
    @Override
    public void procesarCompraOnlinePagada(Long compraId) {
        boolean confirmacionEnviada = false;
        try {
            Factura factura = txNueva.execute(st -> crearFacturaOnline(compraId, false, true));
            if (factura == null) return;
            emitir(factura.getId());
            Factura actual = facturaRepository.findById(factura.getId()).orElse(null);
            String email = emailDeContacto(compraId);
            if (actual != null && actual.getEstado() == EstadoFactura.EMITIDA && email != null) {
                try {
                    emailService.enviarComprobanteCompraConFactura(compraId, factura.getId());
                    confirmacionEnviada = true;
                    return;
                } catch (RuntimeException e) {
                    log.error("No se pudo mandar la confirmación con la factura de la compra ID {}: se mandan por separado", compraId, e);
                }
            }
            emailService.enviarComprobanteCompra(compraId);
            confirmacionEnviada = true;
            if (email != null) mandarFacturaCuandoSeEmita(factura.getId(), email);
        } catch (RuntimeException e) {
            log.error("No se pudo facturar la compra online ID {}", compraId, e);
        } finally {
            if (!confirmacionEnviada) emailService.enviarComprobanteCompra(compraId);
        }
    }

    /**
     * La confirmación ya salió sin la factura: que la factura se mande sola al emitirse. Si justo se
     * emitió (entre el chequeo y acá) el UPDATE condicional no la toca y se manda ya, así no se pierde
     * ni sale dos veces (marcarEmitida decide con la fila bloqueada, igual que este UPDATE).
     */
    private void mandarFacturaCuandoSeEmita(Long facturaId, String email) {
        if (facturaRepository.pasarAMailSiNoEmitida(facturaId, email) > 0) return;
        Factura f = facturaRepository.findById(facturaId).orElse(null);
        if (f == null || f.getEstado() != EstadoFactura.EMITIDA || Boolean.TRUE.equals(f.getAnulacionPedida())
                || f.getMailEnviadoEn() != null) {
            return;
        }
        facturaRepository.pasarAMail(facturaId, email);
        emailService.enviarFactura(facturaId);
    }

    private String emailDeContacto(Long compraId) {
        Compra compra = em.find(Compra.class, compraId);
        String email = compra == null || compra.getContactEmail() == null ? null : compra.getContactEmail().trim();
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? email : null;
    }

    /**
     * Crea la factura de una compra online paga. Con `estricto` (pedido de un admin) los motivos
     * para no facturar son errores que se le muestran; si no (automático, al confirmarse el pago),
     * simplemente no se factura.
     */
    private Factura crearFacturaOnline(Long compraId, boolean estricto) {
        return crearFacturaOnline(compraId, estricto, false);
    }

    /** @param conLaConfirmacion la factura viaja adjunta a la confirmación: se crea sin mail propio */
    private Factura crearFacturaOnline(Long compraId, boolean estricto, boolean conLaConfirmacion) {
        Integer pv = puntoVentaOnline();
        // Bloqueada: el webhook y la verificación directa pueden confirmar el mismo pago casi a la
        // vez, y la segunda tiene que ver la factura de la primera, no crear otra.
        Compra compra = em.find(Compra.class, compraId, LockModeType.PESSIMISTIC_WRITE);
        String motivo = null;
        if (pv == null || !estaHabilitada()) motivo = "La facturación de compras online no está configurada";
        else if (compra == null) motivo = "Compra no encontrada ID: " + compraId;
        else if (compra.getFormaPago() != FormaPago.MERCADO_PAGO) motivo = "No es una compra pagada online (las de puerta se facturan en el POS)";
        else if (compra.getEstado() != EstadoCompra.APROBADO && compra.getEstado() != EstadoCompra.USADO) motivo = "La compra no está paga (está " + compra.getEstado() + ")";
        else if (compra.getMontoTotal() == null || compra.getMontoTotal().signum() <= 0) motivo = "La compra es de $0";
        else if (facturaVigente(compraId).isPresent()) motivo = "Esta compra ya tiene factura";
        if (motivo != null) {
            if (estricto) throw new IllegalStateException(motivo);
            return null;
        }
        String email = compra.getContactEmail() == null ? null : compra.getContactEmail().trim();
        boolean conMail = email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        // Toda venta online se factura; sin un email válido queda emitida igual (sin mandar).
        boolean mailPropio = conMail && !conLaConfirmacion;
        Factura f = crearFactura(compra, mailPropio ? DestinoFactura.MAIL : DestinoFactura.NINGUNO,
                mailPropio ? email : null, null, pv);
        log.info("Factura online ID {} pedida para la compra {} (pto vta {})", f.getId(), compra.getCodigoReserva(), pv);
        return f;
    }

    @Override
    public boolean facturacionOnlineActiva() {
        return puntoVentaOnline() != null && estaHabilitada();
    }

    @Override
    @Transactional
    public FacturaResponseDTO facturarOnlineAhora(Long compraId) {
        return toDto(crearFacturaOnline(compraId, true));
    }

    private Integer puntoVentaOnline() {
        if (puntoVentaOnline == null || puntoVentaOnline.isBlank()) return null;
        try {
            int pv = Integer.parseInt(puntoVentaOnline.trim());
            return pv > 0 ? pv : null;
        } catch (NumberFormatException e) {
            log.error("AFIP_PUNTO_VENTA_ONLINE no es un número: '{}'. Las compras online no se facturan", puntoVentaOnline);
            return null;
        }
    }

    @Override
    public Optional<FacturaResponseDTO> obtenerPorCompra(Long compraId) {
        return facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(compraId, WsfeService.CBTE_TIPO_FACTURA_B)
                .map(this::toDto);
    }

    @Override
    public Map<Long, FacturaResponseDTO> obtenerPorCompras(Collection<Long> compraIds) {
        if (compraIds.isEmpty()) return Map.of();
        Map<Long, Factura> ultimas = new HashMap<>();
        for (Factura f : facturaRepository.findByCompraIdInAndTipoComprobante(compraIds, WsfeService.CBTE_TIPO_FACTURA_B)) {
            ultimas.merge(f.getCompra().getId(), f, (a, b) -> a.getId() > b.getId() ? a : b);
        }
        Map<Long, TrabajoImpresion> trabajos = impresionService.ultimosTrabajos(
                ultimas.values().stream().map(Factura::getId).toList());
        Map<Long, FacturaResponseDTO> resultado = new HashMap<>();
        ultimas.forEach((compraId, f) -> resultado.put(compraId, toDto(f, Optional.ofNullable(trabajos.get(f.getId())))));
        return resultado;
    }

    /**
     * Bloquea la fila de la compra (SELECT ... FOR UPDATE, sin pisar los cambios en memoria) antes
     * de mirar su factura vigente: así cancelar, editar y "Facturar" desde Ventas y facturas no
     * pueden decidir a la vez sobre la misma venta (una factura de más, o una sin nota de crédito).
     */
    private void bloquear(Compra compra) {
        if (compra.getId() != null && em.contains(compra)) {
            em.lock(compra, LockModeType.PESSIMISTIC_WRITE);
        }
    }

    private FacturaResponseDTO toDto(Factura f) {
        return toDto(f, impresionService.ultimoTrabajo(f.getId()));
    }

    private FacturaResponseDTO toDto(Factura f, Optional<TrabajoImpresion> ultimo) {
        return FacturaResponseDTO.builder()
                .id(f.getId())
                .compraId(f.getCompra() != null ? f.getCompra().getId() : null)
                .estado(f.getEstado())
                .destino(f.getDestino())
                .email(f.getEmail())
                .puntoVenta(f.getPuntoVenta())
                .tipoComprobante(f.getTipoComprobante())
                .numero(f.getNumero())
                .cae(f.getCae())
                .caeVencimiento(f.getCaeVencimiento())
                .fechaEmision(f.getFechaEmision())
                .importeTotal(f.getImporteTotal())
                .importeNeto(f.getImporteNeto())
                .importeIva(f.getImporteIva())
                .intentos(f.getIntentos())
                .ultimoError(f.getUltimoError())
                .mailEnviadoEn(f.getMailEnviadoEn())
                .anulacionPedida(Boolean.TRUE.equals(f.getAnulacionPedida()))
                .qrUrl(comprobanteFacturaService.qrUrl(f))
                .impresionEstado(ultimo.map(TrabajoImpresion::getEstado).orElse(null))
                .impresionError(ultimo.map(TrabajoImpresion::getError).orElse(null))
                .detalle(f.getDetalle())
                .build();
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() > 1000 ? texto.substring(0, 1000) : texto;
    }

    // ---------- Control de facturas ----------

    /** Una PENDIENTE creada hace más que esto ya no es "emitiéndose": está trabada en reintentos. */
    private static final long MINUTOS_PARA_TRABADA = 10;
    private static final int DIAS_AVISO_CERTIFICADO = 30;

    private volatile LocalDateTime numeracionControladaEn;
    private volatile List<ControlFacturacionDTO.Desfase> desfases = List.of();

    @Override
    @Transactional(readOnly = true)
    public ControlFacturacionDTO controlFacturacion() {
        List<Factura> conProblemas = facturaRepository.conProblemas(LocalDateTime.now(ZONA_ARGENTINA).minusMinutes(MINUTOS_PARA_TRABADA));
        Map<Long, TrabajoImpresion> trabajos = impresionService.ultimosTrabajos(conProblemas.stream().map(Factura::getId).toList());
        List<ControlFacturacionDTO.Problema> problemas = conProblemas.stream()
                .map(f -> new ControlFacturacionDTO.Problema(toDto(f, Optional.ofNullable(trabajos.get(f.getId()))),
                        f.getCompra() != null ? f.getCompra().getCodigoReserva() : null, f.getFechaCreacion()))
                .toList();
        LocalDate vence = afipClient.vencimientoCertificado().orElse(null);
        Long dias = vence == null ? null : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZONA_ARGENTINA), vence);
        return new ControlFacturacionDTO(problemas, vence, dias, controlaNumeracion(), numeracionControladaEn, desfases);
    }

    private boolean controlaNumeracion() {
        return afipClient.esProduccion() && estaHabilitada();
    }

    @Override
    public List<ControlFacturacionDTO.Desfase> controlarNumeracion() {
        if (!controlaNumeracion()) return List.of();
        java.util.Set<Integer> puntos = new java.util.TreeSet<>(facturaRepository.puntosDeVentaUsados());
        puntos.add(puntoVentaBoleteria);
        Integer online = puntoVentaOnline();
        if (online != null) puntos.add(online);
        List<ControlFacturacionDTO.Desfase> encontrados = new ArrayList<>();
        // Cada punto de venta por separado: uno que ARCA no reconoce (ej. el de pruebas de facturas
        // viejas de homologación, "11002 - punto de venta no habilitado") no frena el control de los demás.
        java.util.Map<Integer, String> sinConsultar = new java.util.TreeMap<>();
        // Con la emisión frenada mientras tanto: una factura que se autoriza entre la consulta a la
        // base y la de ARCA daría un desfase que no existe.
        lockEmision.lock();
        try {
            for (int pv : puntos) {
                try {
                    for (int tipo : List.of(WsfeService.CBTE_TIPO_FACTURA_B, WsfeService.CBTE_TIPO_NOTA_CREDITO_B)) {
                        long base = Optional.ofNullable(facturaRepository.ultimoNumeroEmitido(pv, tipo)).orElse(0L);
                        Long reservado = facturaRepository.ultimoNumeroReservadoSinResolver(pv, tipo);
                        long arca = wsfe.ultimoAutorizado(pv, tipo);
                        // Un número pedido sin respuesta que ARCA sí autorizó no es desfase: lo resuelve la emisión.
                        if (arca != base && (reservado == null || arca != reservado)) {
                            log.warn("Numeración desfasada en pto vta {} tipo {}: base {} / ARCA {}", pv, tipo, base, arca);
                            encontrados.add(new ControlFacturacionDTO.Desfase(pv, tipo, base, arca));
                        }
                    }
                } catch (AfipException e) {
                    log.warn("No se pudo controlar la numeración del pto vta {} en ARCA: {}", pv, e.getMessage());
                    sinConsultar.put(pv, e.getMessage());
                }
            }
        } finally {
            lockEmision.unlock();
        }
        desfases = List.copyOf(encontrados);
        numeracionControladaEn = LocalDateTime.now(ZONA_ARGENTINA).withNano(0);
        if (!sinConsultar.isEmpty()) {
            // 400 con el motivo (lo ve quien apretó "Controlar"), no un error 500 del sistema.
            StringBuilder mensaje = new StringBuilder();
            sinConsultar.forEach((pv, motivo) -> mensaje.append("No se pudo consultar en ARCA el punto de venta ").append(pv)
                    .append(" (").append(motivo).append("). "));
            if (puntos.size() > sinConsultar.size()) {
                mensaje.append(encontrados.isEmpty() ? "Los demás coinciden con ARCA." : "En los demás hay " + encontrados.size() + " desfase(s): revisalos abajo.");
            }
            mensaje.append(" Si ese punto de venta es de facturas de prueba viejas, no es un problema de la facturación actual.");
            throw new IllegalStateException(mensaje.toString());
        }
        if (encontrados.isEmpty()) log.info("Control de numeración OK ({} puntos de venta)", puntos.size());
        return desfases;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> idsAlertasFacturacion() {
        if (!estaHabilitada()) return List.of();
        List<Long> ids = new ArrayList<>(facturaRepository.conProblemas(LocalDateTime.now(ZONA_ARGENTINA).minusMinutes(MINUTOS_PARA_TRABADA))
                .stream().map(Factura::getId).toList());
        afipClient.vencimientoCertificado().ifPresent(vence -> {
            if (java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZONA_ARGENTINA), vence) <= DIAS_AVISO_CERTIFICADO) ids.add(-1L);
        });
        if (!desfases.isEmpty()) ids.add(-2L);
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ControlFacturacionDTO.Totales> totales(LocalDate desde, LocalDate hasta) {
        Map<String, ControlFacturacionDTO.Totales> porClave = new java.util.LinkedHashMap<>();
        for (Factura f : facturaRepository.emitidasEntre(desde, hasta)) {
            String clave = f.getPuntoVenta() + "-" + f.getTipoComprobante();
            ControlFacturacionDTO.Totales t = porClave.get(clave);
            porClave.put(clave, new ControlFacturacionDTO.Totales(f.getPuntoVenta(), f.getTipoComprobante(),
                    (t == null ? 0 : t.cantidad()) + 1,
                    (t == null ? BigDecimal.ZERO : t.total()).add(f.getImporteTotal()),
                    (t == null ? BigDecimal.ZERO : t.neto()).add(f.getImporteNeto()),
                    (t == null ? BigDecimal.ZERO : t.iva()).add(f.getImporteIva())));
        }
        return new ArrayList<>(porClave.values());
    }

    @Override
    @Transactional(readOnly = true)
    public String exportarCsv(LocalDate desde, LocalDate hasta) {
        java.time.format.DateTimeFormatter fecha = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter hora = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        StringBuilder csv = new StringBuilder("\uFEFF"); // BOM: Excel lo abre como UTF-8 (tildes)
        csv.append("Fecha;Hora;Comprobante;Punto de venta;Número;CAE;Vto. CAE;Total;Neto gravado;IVA 21%;Estado;Compra;Comprobante asociado\r\n");
        for (Factura f : facturaRepository.emitidasEntre(desde, hasta)) {
            boolean nc = f.getTipoComprobante() == WsfeService.CBTE_TIPO_NOTA_CREDITO_B;
            String asociado = "";
            if (f.getComprobanteAsociado() != null && f.getComprobanteAsociado().getNumero() != null) {
                asociado = String.format("%04d-%08d", f.getComprobanteAsociado().getPuntoVenta(), f.getComprobanteAsociado().getNumero());
            }
            String estado = nc ? "Emitida" : (Boolean.TRUE.equals(f.getAnulacionPedida()) ? "Anulada con nota de crédito" : "Emitida");
            csv.append(String.join(";",
                    f.getFechaEmision().format(fecha),
                    f.getEmitidaEn() != null ? f.getEmitidaEn().format(hora) : "",
                    nc ? "Nota de Crédito B" : "Factura B",
                    String.format("%04d", f.getPuntoVenta()),
                    String.format("%08d", f.getNumero()),
                    nullAVacio(f.getCae()),
                    f.getCaeVencimiento() != null ? f.getCaeVencimiento().format(fecha) : "",
                    importeCsv(f.getImporteTotal()),
                    importeCsv(f.getImporteNeto()),
                    importeCsv(f.getImporteIva()),
                    estado,
                    f.getCompra() != null ? nullAVacio(f.getCompra().getCodigoReserva()) : "Manual",
                    asociado)).append("\r\n");
        }
        return csv.toString();
    }

    private static String importeCsv(BigDecimal importe) {
        return importe == null ? "" : importe.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String nullAVacio(String texto) {
        return texto == null ? "" : texto.replace(';', ',');
    }

    public record FacturaSolicitadaEvent(Long facturaId) {}

    /** Se aprobó el pago de una compra online: factura y confirmación en segundo plano. */
    public record CompraOnlinePagadaEvent(Long compraId) {}
}
