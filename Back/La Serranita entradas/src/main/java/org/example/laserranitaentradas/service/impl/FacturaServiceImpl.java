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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final ComprobanteFacturaService comprobanteFacturaService;
    private final FacturaPdfGenerator pdfGenerator;
    private final EmailService emailService;
    private final ImpresionService impresionService;

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
                              ComprobanteFacturaService comprobanteFacturaService, FacturaPdfGenerator pdfGenerator,
                              EmailService emailService, ImpresionService impresionService) {
        this.facturaRepository = facturaRepository;
        this.wsfe = wsfe;
        this.afipClient = afipClient;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.comprobanteFacturaService = comprobanteFacturaService;
        this.pdfGenerator = pdfGenerator;
        this.emailService = emailService;
        this.impresionService = impresionService;
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
        Optional<Factura> existente = facturaRepository.findByCompraId(compra.getId());
        if (existente.isPresent()) {
            return existente;
        }

        BigDecimal total = compra.getMontoTotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal neto = total.divide(DIVISOR_IVA_21, 2, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(neto);

        Factura factura = Factura.builder()
                .compra(compra)
                .destino(pedido.getDestino())
                .email(pedido.getDestino() == DestinoFactura.MAIL ? email : null)
                .impresora(pedido.getDestino() == DestinoFactura.IMPRIMIR && pedido.getImpresora() != null
                        && !pedido.getImpresora().isBlank() ? pedido.getImpresora().trim() : null)
                .puntoVenta(puntoVentaBoleteria)
                .tipoComprobante(WsfeService.CBTE_TIPO_FACTURA_B)
                .concepto(concepto(compra.getDetalles()))
                .fechaServicio(compra.getFechaVisita() != null ? compra.getFechaVisita() : LocalDate.now())
                .importeTotal(total)
                .importeNeto(neto)
                .importeIva(iva)
                .build();
        factura = facturaRepository.save(factura);
        // Se emite recién cuando la venta commitea (ver EmisionFacturaListener): si la venta
        // terminara haciendo rollback, no puede quedar una factura autorizada en ARCA.
        eventPublisher.publishEvent(new FacturaSolicitadaEvent(factura.getId()));
        return Optional.of(factura);
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
        int pv = factura.getPuntoVenta();
        int tipo = factura.getTipoComprobante();
        try {
            // Un intento anterior mandó el pedido con este número y nunca supo la respuesta
            // (timeout, corte): antes de pedir otro número hay que preguntar si ese quedó
            // autorizado. Si no se hace esto, un timeout = la misma venta facturada dos veces.
            if (factura.getNumeroIntentado() != null) {
                Optional<WsfeService.ComprobanteAutorizado> previo = wsfe.consultar(pv, tipo, factura.getNumeroIntentado());
                if (previo.isPresent() && previo.get().total().compareTo(factura.getImporteTotal()) == 0) {
                    log.info("La factura ID {} ya había quedado autorizada en ARCA con el número {}", factura.getId(), factura.getNumeroIntentado());
                    marcarEmitida(factura.getId(), factura.getNumeroIntentado(), previo.get().cae(),
                            previo.get().caeVencimiento(), previo.get().fecha());
                    return;
                }
            }

            long numero = wsfe.ultimoAutorizado(pv, tipo) + 1;
            LocalDate fecha = LocalDate.now();
            // Se guarda el número ANTES de pedir el CAE, en su propia transacción: si la
            // respuesta se pierde, el próximo intento sabe qué número consultar.
            tx.executeWithoutResult(s -> facturaRepository.findById(factura.getId()).ifPresent(f -> {
                f.setNumeroIntentado(numero);
                f.setFechaEmision(fecha);
                facturaRepository.save(f);
            }));

            WsfeService.ResultadoCae resultado = wsfe.solicitarCae(new WsfeService.SolicitudCae(
                    pv, tipo, factura.getConcepto(), numero, fecha, factura.getFechaServicio(),
                    factura.getImporteTotal(), factura.getImporteNeto(), factura.getImporteIva()));

            if (resultado.aprobado()) {
                marcarEmitida(factura.getId(), numero, resultado.cae(), resultado.caeVencimiento(), fecha);
                log.info("Factura ID {} emitida: B {}-{} CAE {}", factura.getId(), pv, numero, resultado.cae());
            } else if (resultado.codigos().contains(WsfeService.ERROR_NUMERO_NO_ES_EL_PROXIMO)) {
                // Otro comprobante tomó ese número entre la consulta y el pedido (no debería
                // pasar con un punto de venta propio, pero sí en homologación, donde el CUIT de
                // prueba es compartido). No es un error de la factura: se reintenta con el
                // número que siga.
                liberarNumero(factura.getId());
                reprogramar(factura.getId(), "ARCA: " + String.join(" | ", resultado.mensajes()));
            } else {
                // Rechazada: ARCA no usó el número, así que se libera para no consultarlo en vano.
                marcarError(factura.getId(), "ARCA rechazó la factura: " + String.join(" | ", resultado.mensajes()), true);
            }
        } catch (AfipException e) {
            if (e.esTransitorio()) {
                reprogramar(factura.getId(), e.getMessage());
            } else {
                marcarError(factura.getId(), e.getMessage(), false);
            }
        } catch (Exception e) {
            log.error("Error inesperado emitiendo la factura ID {}", factura.getId(), e);
            reprogramar(factura.getId(), "Error inesperado: " + e.getMessage());
        }
    }

    private void marcarEmitida(Long id, long numero, String cae, LocalDate caeVencimiento, LocalDate fecha) {
        Factura emitida = tx.execute(s -> facturaRepository.findById(id).map(f -> {
            f.setEstado(EstadoFactura.EMITIDA);
            f.setNumero(numero);
            f.setNumeroIntentado(numero);
            f.setCae(cae);
            f.setCaeVencimiento(caeVencimiento);
            f.setFechaEmision(fecha);
            f.setUltimoError(null);
            f.setProximoIntento(null);
            return facturaRepository.save(f);
        }).orElse(null));
        if (emitida == null) return;
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

    private void liberarNumero(Long id) {
        tx.executeWithoutResult(s -> facturaRepository.findById(id).ifPresent(f -> {
            f.setNumeroIntentado(null);
            facturaRepository.save(f);
        }));
    }

    private void marcarError(Long id, String mensaje, boolean liberarNumero) {
        log.warn("Factura ID {} en ERROR: {}", id, mensaje);
        tx.executeWithoutResult(s -> facturaRepository.findById(id).ifPresent(f -> {
            f.setEstado(EstadoFactura.ERROR);
            f.setIntentos(f.getIntentos() + 1);
            f.setUltimoError(recortar(mensaje));
            f.setProximoIntento(null);
            if (liberarNumero) {
                f.setNumeroIntentado(null);
            }
            facturaRepository.save(f);
        }));
    }

    private void reprogramar(Long id, String mensaje) {
        tx.executeWithoutResult(s -> facturaRepository.findById(id).ifPresent(f -> {
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
        Factura factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (factura.getEstado() == EstadoFactura.EMITIDA) {
            throw new IllegalStateException("La factura ya está emitida");
        }
        tx.executeWithoutResult(s -> facturaRepository.findById(facturaId).ifPresent(f -> {
            f.setEstado(EstadoFactura.PENDIENTE);
            f.setIntentos(0);
            f.setProximoIntento(null);
            facturaRepository.save(f);
        }));
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
        return facturaRepository.findByCompraId(compraId).map(this::toDto);
    }

    private FacturaResponseDTO toDto(Factura f) {
        Optional<TrabajoImpresion> ultimo = impresionService.ultimoTrabajo(f.getId());
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
