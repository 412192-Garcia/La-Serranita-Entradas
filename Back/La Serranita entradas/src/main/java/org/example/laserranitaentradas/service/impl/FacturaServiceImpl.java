package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.ControlFacturacionDTO;
import org.example.laserranitaentradas.model.dto.FacturaManualDTO;
import org.example.laserranitaentradas.model.dto.FacturaManualLoteDTO;
import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.DestinoFactura;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Modulo;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.CalculoPrecioService;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.ModulosService;
import org.example.laserranitaentradas.service.FacturaService;
import org.example.laserranitaentradas.service.factura.ComprobanteFacturaService;
import org.example.laserranitaentradas.service.factura.FacturaPdfGenerator;
import org.example.laserranitaentradas.service.impresion.ImpresionService;
import org.example.laserranitaentradas.service.afip.AfipException;
import org.example.laserranitaentradas.service.afip.ArcaClient;
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
     * Explícita: sin TZ del contenedor quedaría en UTC y entre las 21 y las 24 la factura saldría con el día
     * siguiente.
     */
    public static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int CONCEPTO_PRODUCTOS = 1;
    private static final int CONCEPTO_SERVICIOS = 2;
    private static final int CONCEPTO_PRODUCTOS_Y_SERVICIOS = 3;
    /** Después de estos intentos pasa a ERROR (~15 h con el backoff). */
    private static final int MAX_INTENTOS = 20;
    private static final long BACKOFF_MAX_MINUTOS = 60;

    private final FacturaRepository facturaRepository;
    private final WsfeService wsfe;
    private final ArcaClient afipClient;
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
    private final ModulosService modulos;

    /**
     * ARCA numera en orden estricto por punto de venta: se emite de a una. Lock en memoria porque hay una
     * sola instancia; con varias tiene que ir a la base.
     */
    private final ReentrantLock lockEmision = new ReentrantLock();

    /** Punto de venta de boletería. La cantina (0010) comparte CUIT pero tiene su propia numeración. */
    @Value("${afip.punto-venta.boleteria:1}")
    private int puntoVentaBoleteria;

    /** Punto de venta de las compras online (pagadas con Mercado Pago). Vacío = no se facturan. */
    @Value("${afip.punto-venta.online:}")
    private String puntoVentaOnline;

    public FacturaServiceImpl(FacturaRepository facturaRepository, WsfeService wsfe, ArcaClient afipClient,
                              ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager,
                              EntityManager em,
                              ComprobanteFacturaService comprobanteFacturaService, FacturaPdfGenerator pdfGenerator,
                              EmailService emailService, ImpresionService impresionService,
                              CalculoPrecioService calculoPrecioService, ModulosService modulos) {
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
        this.modulos = modulos;
    }

    /** Configurada en el servidor (certificado de ARCA) y no apagada desde Sistema > Módulos. */
    @Override
    public boolean estaHabilitada() {
        return afipClient.estaConfigurado() && modulos.activo(Modulo.FACTURACION);
    }

    @Override
    public Optional<Factura> solicitar(Compra compra, FacturacionPosDTO pedido) {
        if (pedido == null || pedido.getDestino() == null) {
            return Optional.empty();
        }
        if (pedido.getDestino() == DestinoFactura.NINGUNO) {
            // Sólo para facturas que arma el sistema (refacturar una venta corregida).
            throw new IllegalArgumentException("La factura se imprime o se manda por mail");
        }
        String email = pedido.getEmail() == null ? null : pedido.getEmail().trim();
        boolean sinEmail = email == null || email.isEmpty();
        // Mail con el campo vacío = no facturar. No es un error.
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
            log.warn("Se pidió factura para la compra ID {} pero la facturación no está configurada (falta el certificado de ARCA)", compra.getId());
            return Optional.empty();
        }
        if (!modulos.activo(Modulo.FACTURACION_BOLETERIA)) {
            // Apagada desde Sistema > Módulos (una tablet con la pantalla vieja todavía la ofrecía).
            log.info("Se pidió factura para la compra ID {} con la facturación de boletería apagada: se vende sin factura", compra.getId());
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

    /** Factura B PENDIENTE por el total actual; se emite al commitear la transacción en curso. */
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
        // Se emite después del commit: si hay rollback no puede quedar nada autorizado en ARCA.
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
     * Ítems al facturar: "cantidad<TAB>descripción<TAB>subtotal" por línea, más "0<TAB>Descuento<TAB>-monto"
     * si hubo. Si la suma diera menos que el total, se guardan sin subtotal: mejor sin detalle que con
     * números que no cierran.
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
                // Con el precio congelado, para que un aumento no aparezca como "descuento".
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
            // Mismo total y mismos ítems: la factura sigue valiendo.
            if (mismoTotal && (vieja.getDetalle() == null
                    || vieja.getDetalle().equals(detalleTexto(compra, compra.getMontoTotal())))) {
                return;
            }
            anular(vieja);
            if (compra.getMontoTotal() != null && compra.getMontoTotal().signum() > 0) {
                // La corrige un admin desde la oficina: si iba por mail se manda la nueva; si era impresa no
                // se imprime sola.
                boolean porMail = vieja.getDestino() == DestinoFactura.MAIL;
                crearFactura(compra, porMail ? DestinoFactura.MAIL : DestinoFactura.NINGUNO,
                        porMail ? vieja.getEmail() : null, null);
            }
        });
    }

    /**
     * Si no llegó a ARCA se anula acá; si está autorizada, nota de crédito; si está en camino se marca y lo
     * resuelve la emisión. Corre con la fila bloqueada, igual que la emisión.
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
        // En ERROR con número pedido tampoco se sabe si quedó autorizada: vuelve a la cola.
        f.setEstado(EstadoFactura.PENDIENTE);
        f.setIntentos(0);
        f.setProximoIntento(null);
        facturaRepository.save(f);
        eventPublisher.publishEvent(new FacturaSolicitadaEvent(f.getId()));
    }

    /**
     * La factura con su fila bloqueada y RELEÍDA. Todo cambio de estado pasa por acá. El refresh importa: si
     * la entidad ya estaba en la sesión, Hibernate devuelve la vieja.
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
                .tipoComprobante(WsfeService.notaDeCreditoDe(factura.getTipoComprobante()))
                .receptorCuit(factura.getReceptorCuit())
                .receptorRazonSocial(factura.getReceptorRazonSocial())
                .receptorDomicilio(factura.getReceptorDomicilio())
                .receptorCondicionIva(factura.getReceptorCondicionIva())
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
        // Con el módulo apagado nada va a ARCA: queda PENDIENTE hasta que se prenda.
        if (!estaHabilitada()) {
            log.info("Factura ID {} queda pendiente: la facturación está apagada", facturaId);
            return;
        }
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
            // Un intento anterior usó este número sin saber la respuesta: hay que consultarlo antes de pedir
            // otro, o la venta se factura dos veces.
            if (factura.getNumeroIntentado() != null) {
                long intentado = factura.getNumeroIntentado();
                Optional<WsfeService.ComprobanteAutorizado> previo = wsfe.consultar(pv, tipo, intentado);
                // Mismo importe no alcanza: otra factura pudo sacar ese número por el mismo monto.
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
            // Primero con el número de la base; si ARCA lo rechaza, enseguida con el que informa ARCA.
            boolean preguntarAArca = false;
            for (int intento = 1; intento <= 2; intento++) {
                Numero proximo = proximoNumero(pv, tipo, preguntarAArca);
                long numero = proximo.valor();
                LocalDate fecha = LocalDate.now(ZONA_ARGENTINA);
                // El número se reserva antes de pedir el CAE, en su propia transacción, para saber qué
                // consultar si se pierde la respuesta. Si la venta se canceló mientras tanto, no se pide
                // nada.
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
                        asociado(factura), receptor(factura)));

                if (resultado.aprobado()) {
                    marcarEmitida(id, numero, resultado.cae(), resultado.caeVencimiento(), fecha);
                    log.info("Comprobante ID {} (tipo {}) emitido: {}-{} CAE {}", id, tipo, pv, numero, resultado.cae());
                    return;
                }
                if (resultado.codigos().contains(WsfeService.ERROR_NUMERO_NO_ES_EL_PROXIMO)) {
                    liberarNumero(id);
                    if (proximo.deLaBase()) {
                        // La base quedó desfasada (ej. backup viejo): se pregunta a ARCA y se reintenta ya.
                        log.warn("Factura ID {}: ARCA rechazó el número {} sacado de la base; se pide el último a ARCA", id, numero);
                        preguntarAArca = true;
                        continue;
                    }
                    // Otro comprobante tomó el número (pasa en homologación con otra base de prueba). Se
                    // reintenta con el siguiente.
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
     * Resuelve antes las otras facturas del punto de venta con número pedido y sin respuesta, para que ésta
     * no reserve el mismo número.
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
        // Autorización y nota de crédito (si la venta se canceló) en la misma transacción: si el backend se
        // cae, la NC queda PENDIENTE y la emite el job.
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
            // Autorizada pero con la venta cancelada: directo a la nota de crédito.
            emitir(emision.notaDeCreditoId());
            return;
        }
        Factura emitida = emision.factura();
        // Ya EMITIDA: el mail o el ticket salen en segundo plano y la factura vale aunque fallen.
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

    /** El receptor identificado de una Factura A (o su nota de crédito); null = consumidor final. */
    private static WsfeService.Receptor receptor(Factura f) {
        if (f.getReceptorCuit() == null) return null;
        return new WsfeService.Receptor(f.getReceptorCuit(), f.getReceptorCondicionIva());
    }

    /** Número a pedir y de dónde salió (si vino de la base, un rechazo se corrige preguntando a ARCA). */
    private record Numero(long valor, boolean deLaBase) {}

    /**
     * En producción el número sale de la base (el punto de venta web services es exclusivo de este sistema);
     * se pregunta a ARCA si la base no tiene ninguno o si ARCA rechazó el de la base. En homologación se
     * pregunta siempre: las bases de prueba se borran seguido.
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
            // Si la venta se canceló mientras se emitía, una falla tardía no la vuelve a ERROR.
            if (f.getEstado() != EstadoFactura.PENDIENTE) return;
            f.setIntentos(f.getIntentos() + 1);
            f.setUltimoError(recortar(mensaje));
            f.setProximoIntento(null);
            if (liberarNumero) {
                f.setNumeroIntentado(null);
            }
            // Rechazada y con la venta cancelada: queda anulada.
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
        if (!estaHabilitada()) {
            throw new IllegalStateException("La facturación está apagada (Sistema > Módulos): se reintenta cuando se vuelva a prender");
        }
        // Sólo ERROR: reintentar una ANULADA autorizaría una factura sin su nota de crédito.
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

    /** Facturas por lote: alcanza para un día entero de ventas que quedaron sin facturar. */
    private static final int MAX_FACTURAS_LOTE = 100;

    @Override
    @Transactional
    public FacturaResponseDTO emitirManual(FacturaManualDTO pedido) {
        if (!estaHabilitada()) {
            throw new IllegalStateException("La facturación no está configurada");
        }
        Factura factura = facturaRepository.save(armarManual(pedido));
        log.info("Factura manual {} ID {} pedida por {} ({} ítems)", WsfeService.letra(factura.getTipoComprobante()),
                factura.getId(), factura.getImporteTotal(), pedido.items().size());
        eventPublisher.publishEvent(new FacturaSolicitadaEvent(factura.getId()));
        return toDto(factura);
    }

    /** Varias facturas manuales sin entrega. Todo o nada. */
    @Override
    @Transactional
    public List<FacturaResponseDTO> emitirManualLote(FacturaManualLoteDTO lote) {
        if (!estaHabilitada()) {
            throw new IllegalStateException("La facturación no está configurada");
        }
        if (lote == null || lote.facturas() == null || lote.facturas().isEmpty()) {
            throw new IllegalArgumentException("El lote no tiene facturas");
        }
        if (lote.facturas().size() > MAX_FACTURAS_LOTE) {
            throw new IllegalArgumentException("Máximo " + MAX_FACTURAS_LOTE + " facturas por lote");
        }
        String clave = lote.claveIdempotencia() == null || lote.claveIdempotencia().isBlank() ? null : lote.claveIdempotencia().trim();
        if (clave != null) {
            if (clave.length() > 64) throw new IllegalArgumentException("La clave del lote es demasiado larga");
            // Un lote ya pedido (se perdió la respuesta y se mandó de nuevo): mismas facturas, nada nuevo a ARCA.
            facturaRepository.bloquearLote(clave);
            List<Factura> yaPedidas = facturaRepository.findAllByLoteClaveOrderByIdAsc(clave);
            if (!yaPedidas.isEmpty()) {
                log.info("Lote de facturas manuales {} repetido: se devuelven las {} ya pedidas", clave, yaPedidas.size());
                return yaPedidas.stream().map(f -> toDto(f, Optional.empty())).toList();
            }
        }
        List<Factura> armadas = new ArrayList<>();
        for (int i = 0; i < lote.facturas().size(); i++) {
            FacturaManualDTO f = lote.facturas().get(i);
            try {
                armadas.add(armarManual(f == null ? null : new FacturaManualDTO(f.items(), DestinoFactura.NINGUNO, null, null)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Factura " + (i + 1) + " del lote: " + e.getMessage());
            }
        }
        List<FacturaResponseDTO> salida = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Factura armada : armadas) {
            armada.setLoteClave(clave);
            Factura factura = facturaRepository.save(armada);
            total = total.add(factura.getImporteTotal());
            ids.add(factura.getId());
            // Recién creadas: no tienen trabajos de impresión (no se busca uno por factura).
            salida.add(toDto(factura, Optional.empty()));
        }
        // Un solo evento para el lote: uno por factura llenaba el pool @Async y frenaba al POS y los mails.
        eventPublisher.publishEvent(new LoteFacturasSolicitadoEvent(ids));
        log.info("Lote de {} facturas manuales pedido por {}", armadas.size(), total);
        return salida;
    }

    /** Valida el pedido y arma la factura manual (sin guardar). */
    private Factura armarManual(FacturaManualDTO pedido) {
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

        FacturaManualDTO.Receptor receptor = validarReceptor(pedido.receptor());
        BigDecimal neto = total.divide(DIVISOR_IVA_21, 2, RoundingMode.HALF_UP);
        return Factura.builder()
                .destino(destino)
                .email(destino == DestinoFactura.MAIL ? email : null)
                .impresora(destino == DestinoFactura.IMPRIMIR ? pedido.impresora() : null)
                .puntoVenta(puntoVentaBoleteria)
                .tipoComprobante(receptor != null ? WsfeService.CBTE_TIPO_FACTURA_A : WsfeService.CBTE_TIPO_FACTURA_B)
                .receptorCuit(receptor != null ? receptor.cuit() : null)
                .receptorRazonSocial(receptor != null ? receptor.razonSocial() : null)
                .receptorDomicilio(receptor != null ? receptor.domicilio() : null)
                .receptorCondicionIva(receptor != null ? receptor.condicionIva() : null)
                .concepto(hayServicio && hayProducto ? CONCEPTO_PRODUCTOS_Y_SERVICIOS
                        : hayProducto ? CONCEPTO_PRODUCTOS : CONCEPTO_SERVICIOS)
                .fechaServicio(LocalDate.now(ZONA_ARGENTINA))
                .importeTotal(total)
                .importeNeto(neto)
                .importeIva(total.subtract(neto))
                .detalle(detalle.toString())
                .build();
    }

    private static final int MAX_RAZON_SOCIAL = 150;
    private static final int MAX_DOMICILIO = 200;

    /** Normaliza y valida el receptor de una Factura A; null si no vino (Factura B). */
    private FacturaManualDTO.Receptor validarReceptor(FacturaManualDTO.Receptor r) {
        if (r == null) return null;
        String cuit = r.cuit() == null ? "" : r.cuit().replaceAll("[\\s.-]", "");
        if (!WsfeService.cuitValido(cuit)) {
            throw new IllegalArgumentException("El CUIT del receptor no es válido");
        }
        if (cuit.equals(afipClient.getCuit())) {
            throw new IllegalArgumentException("El CUIT del receptor es el del parque");
        }
        String razonSocial = r.razonSocial() == null ? "" : r.razonSocial().replaceAll("\\s+", " ").trim();
        if (razonSocial.isEmpty()) {
            throw new IllegalArgumentException("Falta la razón social del receptor");
        }
        if (razonSocial.length() > MAX_RAZON_SOCIAL) {
            throw new IllegalArgumentException("La razón social es muy larga (máximo " + MAX_RAZON_SOCIAL + " caracteres)");
        }
        String domicilio = r.domicilio() == null ? "" : r.domicilio().replaceAll("\\s+", " ").trim();
        if (domicilio.isEmpty()) {
            throw new IllegalArgumentException("Falta el domicilio comercial del receptor (va impreso en la Factura A)");
        }
        if (domicilio.length() > MAX_DOMICILIO) {
            throw new IllegalArgumentException("El domicilio es muy largo (máximo " + MAX_DOMICILIO + " caracteres)");
        }
        if (r.condicionIva() == null || !WsfeService.CONDICIONES_IVA_FACTURA_A.containsKey(r.condicionIva())) {
            throw new IllegalArgumentException("La condición frente al IVA del receptor no admite Factura A");
        }
        return new FacturaManualDTO.Receptor(cuit, razonSocial, domicilio, r.condicionIva());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaResponseDTO> manuales() {
        List<Factura> facturas = facturaRepository.findTop50ByCompraIsNullAndTipoComprobanteInOrderByIdDesc(
                List.of(WsfeService.CBTE_TIPO_FACTURA_A, WsfeService.CBTE_TIPO_FACTURA_B));
        Map<Long, TrabajoImpresion> trabajos = impresionService.ultimosTrabajos(facturas.stream().map(Factura::getId).toList());
        return facturas.stream().map(f -> toDto(f, Optional.ofNullable(trabajos.get(f.getId())))).toList();
    }

    @Override
    @Transactional
    public void anularFactura(Long facturaId) {
        Factura f = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (!WsfeService.esFactura(f.getTipoComprobante())) {
            throw new IllegalStateException("Sólo se anulan facturas (una nota de crédito no se anula)");
        }
        if (f.getEstado() == EstadoFactura.ANULADA || Boolean.TRUE.equals(f.getAnulacionPedida())) {
            throw new IllegalStateException("Esta factura ya está anulada");
        }
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.referencia(f.getNumero() != null
                ? String.format("%s %04d-%08d", WsfeService.letra(f.getTipoComprobante()), f.getPuntoVenta(), f.getNumero()) : "(sin número)");
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
        // Después de guardar la aprobación y en segundo plano: un error de factura no puede deshacer el pago.
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
     * Pide la factura, espera a ARCA (2-3 s) y manda la confirmación con la factura. Si ARCA tarda, la
     * confirmación sale sola y la factura después. La confirmación sale siempre (finally).
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
     * Que la factura se mande sola al emitirse. Si justo se emitió, el UPDATE condicional no la toca y se
     * manda ya.
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
     * Con `estricto` (pedido de un admin) los motivos para no facturar son errores; si no, simplemente no se
     * factura.
     */
    private Factura crearFacturaOnline(Long compraId, boolean estricto) {
        return crearFacturaOnline(compraId, estricto, false);
    }

    /** @param conLaConfirmacion la factura viaja adjunta a la confirmación: se crea sin mail propio */
    private Factura crearFacturaOnline(Long compraId, boolean estricto, boolean conLaConfirmacion) {
        Integer pv = puntoVentaOnline();
        // Bloqueada: webhook y verificación directa pueden llegar a la vez.
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
    /**
     * Se factura sola cada compra online: configurada y prendida (general y online). Facturar a mano no
     * depende del módulo online.
     */
    public boolean facturacionOnlineActiva() {
        return puntoVentaOnline() != null && estaHabilitada() && modulos.activo(Modulo.FACTURACION_ONLINE);
    }

    @Override
    public boolean facturacionBoleteriaActiva() {
        return estaHabilitada() && modulos.activo(Modulo.FACTURACION_BOLETERIA);
    }

    @Override
    public boolean puntoVentaOnlineConfigurado() {
        return puntoVentaOnline() != null;
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
     * Bloquea la compra antes de mirar su factura, para que cancelar, editar y facturar no decidan a la vez.
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
                .receptorCuit(f.getReceptorCuit())
                .receptorRazonSocial(f.getReceptorRazonSocial())
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
        // Cada punto de venta por separado: uno que ARCA no reconoce no frena a los demás.
        java.util.Map<Integer, String> sinConsultar = new java.util.TreeMap<>();
        // Con la emisión frenada, para no ver un desfase que no existe.
        lockEmision.lock();
        try {
            for (int pv : puntos) {
                try {
                    for (int tipo : tiposAControlar(pv)) {
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

    /** B siempre; A sólo si ese punto de venta ya emitió alguna (si no, ARCA y la base están en 0 igual). */
    private List<Integer> tiposAControlar(int pv) {
        List<Integer> tipos = new ArrayList<>(List.of(WsfeService.CBTE_TIPO_FACTURA_B, WsfeService.CBTE_TIPO_NOTA_CREDITO_B));
        if (facturaRepository.ultimoNumeroEmitido(pv, WsfeService.CBTE_TIPO_FACTURA_A) != null) {
            tipos.add(WsfeService.CBTE_TIPO_FACTURA_A);
            tipos.add(WsfeService.CBTE_TIPO_NOTA_CREDITO_A);
        }
        return tipos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> idsAlertasFacturacion() {
        if (!afipClient.estaConfigurado()) return List.of();
        // Con el módulo apagado las pendientes esperan a propósito; ERROR y certificado se siguen avisando.
        boolean apagada = !modulos.activo(Modulo.FACTURACION);
        List<Long> ids = new ArrayList<>(facturaRepository.conProblemas(LocalDateTime.now(ZONA_ARGENTINA).minusMinutes(MINUTOS_PARA_TRABADA))
                .stream().filter(f -> !apagada || f.getEstado() == EstadoFactura.ERROR).map(Factura::getId).toList());
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
        csv.append("Fecha;Hora;Comprobante;Punto de venta;Número;CAE;Vto. CAE;Total;Neto gravado;IVA 21%;Estado;Compra;Comprobante asociado;CUIT receptor;Razón social receptor\r\n");
        for (Factura f : facturaRepository.emitidasEntre(desde, hasta)) {
            boolean nc = WsfeService.esNotaDeCredito(f.getTipoComprobante());
            String asociado = "";
            if (f.getComprobanteAsociado() != null && f.getComprobanteAsociado().getNumero() != null) {
                asociado = String.format("%s %04d-%08d", WsfeService.letra(f.getComprobanteAsociado().getTipoComprobante()),
                        f.getComprobanteAsociado().getPuntoVenta(), f.getComprobanteAsociado().getNumero());
            }
            String estado = nc ? "Emitida" : (Boolean.TRUE.equals(f.getAnulacionPedida()) ? "Anulada con nota de crédito" : "Emitida");
            csv.append(String.join(";",
                    f.getFechaEmision().format(fecha),
                    f.getEmitidaEn() != null ? f.getEmitidaEn().format(hora) : "",
                    WsfeService.nombre(f.getTipoComprobante()),
                    String.format("%04d", f.getPuntoVenta()),
                    String.format("%08d", f.getNumero()),
                    nullAVacio(f.getCae()),
                    f.getCaeVencimiento() != null ? f.getCaeVencimiento().format(fecha) : "",
                    importeCsv(f.getImporteTotal()),
                    importeCsv(f.getImporteNeto()),
                    importeCsv(f.getImporteIva()),
                    estado,
                    f.getCompra() != null ? nullAVacio(f.getCompra().getCodigoReserva()) : "Manual",
                    asociado,
                    nullAVacio(f.getReceptorCuit()),
                    nullAVacio(f.getReceptorRazonSocial()))).append("\r\n");
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

    /** Las facturas de un lote manual, para emitirlas una tras otra en un solo hilo. */
    public record LoteFacturasSolicitadoEvent(List<Long> facturaIds) {}

    /** Se aprobó el pago de una compra online: factura y confirmación en segundo plano. */
    public record CompraOnlinePagadaEvent(Long compraId) {}
}
