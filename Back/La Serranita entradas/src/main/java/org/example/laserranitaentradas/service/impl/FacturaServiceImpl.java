package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.DestinoFactura;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        BigDecimal total = compra.getMontoTotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal neto = total.divide(DIVISOR_IVA_21, 2, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(neto);

        Factura factura = Factura.builder()
                .compra(compra)
                .destino(destino)
                .email(email)
                .impresora(impresora)
                .puntoVenta(puntoVentaBoleteria)
                .tipoComprobante(WsfeService.CBTE_TIPO_FACTURA_B)
                .concepto(concepto(compra.getDetalles()))
                .fechaServicio(compra.getFechaVisita() != null ? compra.getFechaVisita() : LocalDate.now())
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
                return compra.getFormaPago() == null ? null
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
                LocalDate fecha = LocalDate.now();
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
                .compraId(f.getCompra().getId())
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
                .build();
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() > 1000 ? texto.substring(0, 1000) : texto;
    }

    public record FacturaSolicitadaEvent(Long facturaId) {}
}
