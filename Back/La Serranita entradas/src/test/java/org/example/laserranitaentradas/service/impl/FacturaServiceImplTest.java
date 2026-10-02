package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.entity.*;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.afip.AfipException;
import org.example.laserranitaentradas.service.afip.AfipSdkClient;
import org.example.laserranitaentradas.service.afip.WsfeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacturaServiceImplTest {

    @Mock private FacturaRepository facturaRepository;
    @Mock private WsfeService wsfe;
    @Mock private AfipSdkClient afipClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private jakarta.persistence.EntityManager em;
    @Mock private org.example.laserranitaentradas.service.factura.ComprobanteFacturaService comprobanteFacturaService;
    @Mock private org.example.laserranitaentradas.service.factura.FacturaPdfGenerator pdfGenerator;
    @Mock private org.example.laserranitaentradas.service.EmailService emailService;
    @Mock private org.example.laserranitaentradas.service.impresion.ImpresionService impresionService;
    @Mock private org.example.laserranitaentradas.service.CalculoPrecioService calculoPrecioService;

    private FacturaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FacturaServiceImpl(facturaRepository, wsfe, afipClient, eventPublisher, transactionManager, em,
                comprobanteFacturaService, pdfGenerator, emailService, impresionService, calculoPrecioService);
        ReflectionTestUtils.setField(service, "puntoVentaBoleteria", 5);
        when(afipClient.estaConfigurado()).thenReturn(true);
        when(afipClient.getCuit()).thenReturn("20409378472");
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> {
            Factura f = inv.getArgument(0);
            if (f.getId() == null) f.setId(99L);
            return f;
        });
    }

    // ---------- solicitar ----------

    @Test
    void mailConEmailVacio_noFactura() {
        Optional<Factura> r = service.solicitar(compra("2500", entrada()), pedido(DestinoFactura.MAIL, "  "));

        assertThat(r).isEmpty();
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void sinPedido_noFactura() {
        assertThat(service.solicitar(compra("2500", entrada()), null)).isEmpty();
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void ventaEnCero_noFactura() {
        assertThat(service.solicitar(compra("0", entrada()), pedido(DestinoFactura.IMPRIMIR, null))).isEmpty();
    }

    @Test
    void sinAccessToken_noFacturaNiFalla() {
        when(afipClient.estaConfigurado()).thenReturn(false);
        assertThat(service.solicitar(compra("2500", entrada()), pedido(DestinoFactura.IMPRIMIR, null))).isEmpty();
    }

    @Test
    void emailInvalido_rechaza() {
        assertThatThrownBy(() -> service.solicitar(compra("2500", entrada()), pedido(DestinoFactura.MAIL, "sin-arroba")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reintentoConMismaCompra_noDuplica() {
        Factura existente = Factura.builder().id(7L).estado(EstadoFactura.PENDIENTE).build();
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(existente));

        Optional<Factura> r = service.solicitar(compra("2500", entrada()), pedido(DestinoFactura.IMPRIMIR, null));

        assertThat(r).containsSame(existente);
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void imprimir_creaPendienteConIvaContenidoYConceptoServicios() {
        Factura f = service.solicitar(compra("2500", entrada()), pedido(DestinoFactura.IMPRIMIR, null)).orElseThrow();

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
        assertThat(f.getPuntoVenta()).isEqualTo(5);
        assertThat(f.getTipoComprobante()).isEqualTo(6);
        assertThat(f.getConcepto()).isEqualTo(2);
        // Mismo cálculo que el ticket de la cantina: $2.500 -> IVA contenido $433,88 (~$434).
        assertThat(f.getImporteNeto()).isEqualByComparingTo("2066.12");
        assertThat(f.getImporteIva()).isEqualByComparingTo("433.88");
        assertThat(f.getImporteNeto().add(f.getImporteIva())).isEqualByComparingTo("2500");
        verify(eventPublisher).publishEvent(any(FacturaServiceImpl.FacturaSolicitadaEvent.class));
    }

    @Test
    void entradasYArticulos_conceptoProductosYServicios() {
        Factura f = service.solicitar(compra("3000", entrada(), articulo()), pedido(DestinoFactura.MAIL, "a@b.com")).orElseThrow();

        assertThat(f.getConcepto()).isEqualTo(3);
        assertThat(f.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void soloArticulos_conceptoProductos() {
        Factura f = service.solicitar(compra("800", articulo()), pedido(DestinoFactura.IMPRIMIR, null)).orElseThrow();
        assertThat(f.getConcepto()).isEqualTo(1);
    }

    // ---------- emitir ----------

    @Test
    void emitir_pideUltimoMasUnoYQuedaEmitida() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "86383799071902", LocalDate.now().plusDays(10), List.of(), List.of()));

        service.emitir(10L);

        ArgumentCaptor<WsfeService.SolicitudCae> captor = ArgumentCaptor.forClass(WsfeService.SolicitudCae.class);
        verify(wsfe).solicitarCae(captor.capture());
        assertThat(captor.getValue().numero()).isEqualTo(42L);
        assertThat(f.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(f.getNumero()).isEqualTo(42L);
        assertThat(f.getCae()).isEqualTo("86383799071902");
    }

    @Test
    void emitir_destinoMail_mandaElMailAlQuedarEmitida() {
        Factura f = pendiente();
        f.setDestino(DestinoFactura.MAIL);
        f.setEmail("a@b.com");
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        verify(emailService).enviarFactura(10L);
    }

    @Test
    void emitir_destinoImprimir_loMandaALaImpresoraElegida() {
        Factura f = pendiente();
        f.setImpresora("Boletería");
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        verify(impresionService).imprimirFactura(10L, "Boletería");
    }

    @Test
    void emitir_siFallaLaImpresora_laFacturaQuedaEmitidaIgual() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));
        when(impresionService.imprimirFactura(anyLong(), any())).thenThrow(new RuntimeException("sin agente"));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
    }

    @Test
    void emitir_destinoImprimir_noMandaMail() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        verifyNoInteractions(emailService);
    }

    @Test
    void emitir_rechazada_noMandaMail() {
        Factura f = pendiente();
        f.setDestino(DestinoFactura.MAIL);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(false, null, null, List.of("x"), List.of(10048)));

        service.emitir(10L);

        verifyNoInteractions(emailService);
    }

    @Test
    void emitir_timeout_quedaPendienteConNumeroIntentadoGuardado() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenThrow(new AfipException("timeout", true));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
        assertThat(f.getNumeroIntentado()).isEqualTo(42L);
        assertThat(f.getIntentos()).isEqualTo(1);
        assertThat(f.getProximoIntento()).isNotNull();
    }

    @Test
    void emitir_despuesDeUnTimeout_siYaQuedoAutorizadaNoLaEmiteDeNuevo() {
        Factura f = pendiente();
        f.setNumeroIntentado(42L);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.of(
                new WsfeService.ComprobanteAutorizado("123", LocalDate.now().plusDays(10), LocalDate.now(), new BigDecimal("2500.00"))));

        service.emitir(10L);

        verify(wsfe, never()).solicitarCae(any());
        verify(wsfe, never()).ultimoAutorizado(anyInt(), anyInt());
        assertThat(f.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(f.getNumero()).isEqualTo(42L);
        assertThat(f.getCae()).isEqualTo("123");
    }

    @Test
    void emitir_despuesDeUnTimeout_siNoQuedoAutorizadaPideNumeroNuevo() {
        Factura f = pendiente();
        f.setNumeroIntentado(42L);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.empty());
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "999", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(f.getNumero()).isEqualTo(42L);
    }

    @Test
    void emitir_rechazadaPorArca_pasaAErrorYLiberaElNumero() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(false, null, null, List.of("10048 - campo invalido"), List.of(10048)));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ERROR);
        assertThat(f.getNumeroIntentado()).isNull();
        assertThat(f.getUltimoError()).contains("10048");
    }

    @Test
    void emitir_numeroTomadoPorOtro_seReintentaConOtroNumero() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(false, null, null,
                List.of("10016 - no es el proximo"), List.of(10016)));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
        assertThat(f.getNumeroIntentado()).isNull();
        assertThat(f.getProximoIntento()).isNotNull();
    }

    @Test
    void emitir_yaEmitida_noHaceNada() {
        Factura f = pendiente();
        f.setEstado(EstadoFactura.EMITIDA);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));

        service.emitir(10L);

        verifyNoInteractions(wsfe);
    }

    @Test
    void emitir_demasiadosIntentos_pasaAError() {
        Factura f = pendiente();
        f.setIntentos(19);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenThrow(new AfipException("sin internet", true));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ERROR);
        verify(wsfe, never()).consultar(anyInt(), anyInt(), anyLong());
    }

    // ---------- Notas de crédito ----------

    private Factura emitida() {
        Factura f = pendiente();
        f.setEstado(EstadoFactura.EMITIDA);
        f.setNumero(24L);
        f.setNumeroIntentado(24L);
        f.setCae("123");
        f.setFechaEmision(LocalDate.now());
        f.setDetalle("2\tGeneral");
        return f;
    }

    private List<Factura> capturarGuardadas() {
        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void cancelarVenta_bloqueaLaFilaDeLaCompraAntesDeMirarSuFactura() {
        Compra compra = Compra.builder().id(1L).build();
        when(em.contains(compra)).thenReturn(true);
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.empty());

        service.alCancelarVenta(compra);

        var orden = inOrder(em, facturaRepository);
        orden.verify(em).lock(compra, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        orden.verify(facturaRepository).findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6);
    }

    @Test
    void obtenerPorCompras_laMasNuevaDeCadaCompra_conSuUltimoTrabajo_enDosConsultas() {
        Factura vieja = emitida();
        vieja.setId(10L);
        vieja.setCompra(Compra.builder().id(1L).build());
        Factura nueva = emitida();
        nueva.setId(11L);
        nueva.setCompra(vieja.getCompra());
        Factura otra = emitida();
        otra.setId(20L);
        otra.setCompra(Compra.builder().id(2L).build());
        when(facturaRepository.findByCompraIdInAndTipoComprobante(List.of(1L, 2L), 6)).thenReturn(List.of(vieja, otra, nueva));
        TrabajoImpresion trabajo = new TrabajoImpresion();
        trabajo.setEstado(EstadoTrabajoImpresion.IMPRESO);
        when(impresionService.ultimosTrabajos(any())).thenReturn(java.util.Map.of(11L, trabajo));

        var r = service.obtenerPorCompras(List.of(1L, 2L));

        assertThat(r.get(1L).getId()).isEqualTo(11L);
        assertThat(r.get(1L).getImpresionEstado()).isEqualTo(EstadoTrabajoImpresion.IMPRESO);
        assertThat(r.get(2L).getImpresionEstado()).isNull();
        verify(facturaRepository, never()).findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(any(), any());
        verify(impresionService, never()).ultimoTrabajo(any());
    }

    @Test
    void cancelarVentaFacturada_emiteNotaDeCreditoPorElTotalAsociadaALaFactura() {
        Factura f = emitida();
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        service.alCancelarVenta(Compra.builder().id(1L).build());

        Factura nc = capturarGuardadas().stream().filter(x -> x.getTipoComprobante() == 8).findFirst().orElseThrow();
        assertThat(nc.getComprobanteAsociado()).isSameAs(f);
        assertThat(nc.getImporteTotal()).isEqualByComparingTo("2500.00");
        assertThat(nc.getDestino()).isEqualTo(DestinoFactura.NINGUNO);
        assertThat(nc.getDetalle()).isEqualTo("2\tGeneral");
        assertThat(f.getAnulacionPedida()).isTrue();
        verify(eventPublisher).publishEvent(any(FacturaServiceImpl.FacturaSolicitadaEvent.class));
    }

    @Test
    void cancelarVenta_conFacturaQueNuncaLlegoAArca_laAnulaSinNotaDeCredito() {
        Factura f = pendiente();
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        service.alCancelarVenta(Compra.builder().id(1L).build());

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
        assertThat(capturarGuardadas()).noneMatch(x -> x.getTipoComprobante() == 8);
    }

    @Test
    void cancelarVenta_sinFactura_noHaceNada() {
        service.alCancelarVenta(Compra.builder().id(1L).build());
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void cancelarVenta_conFacturaEnVuelo_laMarcaYAlNoEstarAutorizadaSeAnulaSinPedirOtroNumero() {
        Factura f = pendiente();
        f.setNumeroIntentado(42L);
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.empty());

        service.alCancelarVenta(Compra.builder().id(1L).build());
        assertThat(f.getAnulacionPedida()).isTrue();
        assertThat(f.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
        verify(wsfe, never()).ultimoAutorizado(anyInt(), anyInt());
        verify(wsfe, never()).solicitarCae(any());
    }

    @Test
    void facturaEnVueloQueResultoAutorizada_trasCancelar_sacaNotaDeCreditoYNoSeImprime() {
        Factura f = pendiente();
        f.setNumeroIntentado(42L);
        f.setAnulacionPedida(true);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.of(
                new WsfeService.ComprobanteAutorizado("123", LocalDate.now().plusDays(10), LocalDate.now(), new BigDecimal("2500.00"))));

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(capturarGuardadas()).anyMatch(x -> x.getTipoComprobante() == 8);
        verifyNoInteractions(impresionService);
        verifyNoInteractions(emailService);
    }

    @Test
    void emitirNotaDeCredito_mandaLaFacturaAsociadaAArca() {
        Factura factura = emitida();
        Factura nc = Factura.builder()
                .id(11L).compra(Compra.builder().id(1L).build()).comprobanteAsociado(factura)
                .estado(EstadoFactura.PENDIENTE).destino(DestinoFactura.NINGUNO)
                .puntoVenta(5).tipoComprobante(8).concepto(2).fechaServicio(LocalDate.now())
                .importeTotal(new BigDecimal("2500.00")).importeNeto(new BigDecimal("2066.12")).importeIva(new BigDecimal("433.88"))
                .build();
        when(facturaRepository.findById(11L)).thenReturn(Optional.of(nc));
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(factura));
        when(wsfe.ultimoAutorizado(5, 8)).thenReturn(3L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "999", LocalDate.now(), List.of(), List.of()));

        service.emitir(11L);

        ArgumentCaptor<WsfeService.SolicitudCae> captor = ArgumentCaptor.forClass(WsfeService.SolicitudCae.class);
        verify(wsfe).solicitarCae(captor.capture());
        assertThat(captor.getValue().tipoComprobante()).isEqualTo(8);
        assertThat(captor.getValue().numero()).isEqualTo(4L);
        assertThat(captor.getValue().asociado()).isEqualTo(new WsfeService.Asociado(6, 5, 24L, factura.getFechaEmision()));
        assertThat(nc.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        verifyNoInteractions(emailService);
        verifyNoInteractions(impresionService);
    }

    @Test
    void editarVenta_conOtroTotal_notaDeCreditoYFacturaNuevaAlMismoMail() {
        Factura f = emitida();
        f.setDestino(DestinoFactura.MAIL);
        f.setEmail("a@b.com");
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        service.alEditarVenta(compra("3000", entrada()), new BigDecimal("2500"));

        List<Factura> guardadas = capturarGuardadas();
        assertThat(guardadas).anyMatch(x -> x.getTipoComprobante() == 8 && x.getComprobanteAsociado() == f);
        Factura nueva = guardadas.stream().filter(x -> x.getTipoComprobante() == 6 && x != f).findFirst().orElseThrow();
        assertThat(nueva.getImporteTotal()).isEqualByComparingTo("3000");
        assertThat(nueva.getDestino()).isEqualTo(DestinoFactura.MAIL);
        assertThat(nueva.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void editarVenta_facturaImpresa_laNuevaNoSeImprimeSola() {
        Factura f = emitida();
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        service.alEditarVenta(compra("3000", entrada()), new BigDecimal("2500"));

        Factura nueva = capturarGuardadas().stream().filter(x -> x.getTipoComprobante() == 6 && x != f).findFirst().orElseThrow();
        assertThat(nueva.getDestino()).isEqualTo(DestinoFactura.NINGUNO);
    }

    @Test
    void editarVenta_mismoTotal_noTocaLaFactura() {
        service.alEditarVenta(compra("2500", entrada()), new BigDecimal("2500.00"));

        verify(facturaRepository, never()).save(any());
        verify(facturaRepository, never()).findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(anyLong(), any());
    }

    @Test
    void solicitar_guardaSubtotalPorLinea_conElPrecioQueUsoLaVenta() {
        when(calculoPrecioService.calcularTotal(any(), eq(1), any())).thenReturn(new BigDecimal("2500"));
        Compra c = compra("3000", entrada(), articulo());
        c.setFormaPago(FormaPago.TARJETA);

        Factura f = service.solicitar(c, pedido(DestinoFactura.IMPRIMIR, null)).orElseThrow();

        assertThat(f.getDetalle()).isEqualTo("1\tGeneral\t2500.00\n1\tSouvenir\t500.00");
    }

    @Test
    void solicitar_conDescuento_agregaLaLineaDeDescuentoParaQueCierreConElTotal() {
        when(calculoPrecioService.calcularTotal(any(), eq(1), any())).thenReturn(new BigDecimal("2500"));
        Compra c = compra("2700", entrada(), articulo()); // 2500 + 500 - 300 de descuento
        c.setFormaPago(FormaPago.TARJETA);

        Factura f = service.solicitar(c, pedido(DestinoFactura.IMPRIMIR, null)).orElseThrow();

        assertThat(f.getDetalle()).isEqualTo("1\tGeneral\t2500.00\n1\tSouvenir\t500.00\n0\tDescuento\t-300.00");
    }

    @Test
    void solicitar_siLaSumaNoCierra_guardaLosItemsSinSubtotal() {
        // Precio que bajó entre la venta y la factura: las líneas sumarían menos que el total.
        when(calculoPrecioService.calcularTotal(any(), eq(1), any())).thenReturn(new BigDecimal("1000"));
        Compra c = compra("3000", entrada(), articulo());
        c.setFormaPago(FormaPago.TARJETA);

        Factura f = service.solicitar(c, pedido(DestinoFactura.IMPRIMIR, null)).orElseThrow();

        assertThat(f.getDetalle()).isEqualTo("1\tGeneral\n1\tSouvenir");
    }

    @Test
    void solicitar_guardaCopiaDeLosItems() {
        Factura f = service.solicitar(compra("3000", entrada(), articulo()), pedido(DestinoFactura.IMPRIMIR, null)).orElseThrow();

        assertThat(f.getDetalle()).isEqualTo("1\tGeneral\n1\tSouvenir");
    }

    // ---------- Carreras señaladas en la review ----------

    @Test
    void cancelacionMientrasSeConsultabaElUltimoNumero_noPideCae() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        // La venta se cancela justo mientras se consulta el último número autorizado.
        when(wsfe.ultimoAutorizado(5, 6)).thenAnswer(inv -> {
            f.setEstado(EstadoFactura.ANULADA);
            return 41L;
        });

        service.emitir(10L);

        verify(wsfe, never()).solicitarCae(any());
        assertThat(f.getNumeroIntentado()).isNull();
        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
    }

    @Test
    void numeroRecuperadoQueYaEsDeOtraFactura_noSeAdoptaYSePideOtro() {
        Factura f = pendiente();
        f.setNumeroIntentado(42L);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.of(
                new WsfeService.ComprobanteAutorizado("AJENO", LocalDate.now().plusDays(10), LocalDate.now(), new BigDecimal("2500.00"))));
        when(facturaRepository.existsByPuntoVentaAndTipoComprobanteAndNumeroAndIdNot(5, 6, 42L, 10L)).thenReturn(true);
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(42L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "PROPIO", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        assertThat(f.getNumero()).isEqualTo(43L);
        assertThat(f.getCae()).isEqualTo("PROPIO");
    }

    @Test
    void autorizadaConAnulacionPedida_guardaLaNotaDeCreditoEnLaMismaTransaccion() {
        Factura f = pendiente();
        f.setNumeroIntentado(42L);
        f.setAnulacionPedida(true);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.of(
                new WsfeService.ComprobanteAutorizado("123", LocalDate.now().plusDays(10), LocalDate.now(), new BigDecimal("2500.00"))));
        // La emisión de la NC falla (ARCA caída): igual tiene que quedar guardada PENDIENTE.
        when(wsfe.ultimoAutorizado(5, 8)).thenThrow(new AfipException("caida", true));

        service.emitir(10L);

        Factura nc = capturarGuardadas().stream().filter(x -> x.getTipoComprobante() == 8).findFirst().orElseThrow();
        assertThat(nc.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
        assertThat(nc.getComprobanteAsociado()).isSameAs(f);
        verify(transactionManager, atLeastOnce()).commit(any());
    }

    @Test
    void reintentarUnaAnulada_seRechaza() {
        Factura f = pendiente();
        f.setEstado(EstadoFactura.ANULADA);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.reintentar(10L)).isInstanceOf(IllegalStateException.class);
        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
        verifyNoInteractions(wsfe);
    }

    @Test
    void rechazadaConLaVentaYaCancelada_quedaAnuladaEnVezDeError() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenAnswer(inv -> {
            f.setAnulacionPedida(true); // se canceló mientras ARCA procesaba
            return new WsfeService.ResultadoCae(false, null, null, List.of("10048 - x"), List.of(10048));
        });

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
    }

    // ---------- Segunda pasada de la review ----------

    @Test
    void anular_releeLaFacturaBloqueadaAntesDeDecidir() {
        Factura vieja = pendiente(); // como la había cargado la cancelación: sin número
        Factura actual = pendiente();
        actual.setNumeroIntentado(42L); // mientras tanto la emisión reservó el 42
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(vieja));
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(vieja));
        doAnswer(inv -> {
            vieja.setNumeroIntentado(actual.getNumeroIntentado());
            return null;
        }).when(em).refresh(vieja, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        service.alCancelarVenta(Compra.builder().id(1L).build());

        // Con el dato releído ve que hay un número en vuelo: no la anula, la marca para resolver.
        assertThat(vieja.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
        assertThat(vieja.getAnulacionPedida()).isTrue();
    }

    @Test
    void antesDePedirNumero_resuelveLaReservaSinRespuestaDeOtraFactura() {
        Factura a = pendiente();
        a.setId(20L);
        a.setNumeroIntentado(42L);
        a.setProximoIntento(java.time.LocalDateTime.now().plusMinutes(5)); // esperando su reintento
        Factura b = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(b));
        when(facturaRepository.findById(20L)).thenReturn(Optional.of(a));
        when(facturaRepository.reservasSinResolver(eq(5), eq(6), eq(10L), any())).thenReturn(List.of(20L));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.empty()); // el 42 de A no quedó autorizado
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "B", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        // A soltó el 42 antes de que B lo reservara: ya no puede adoptar la autorización de B.
        assertThat(a.getNumeroIntentado()).isNull();
        assertThat(b.getNumero()).isEqualTo(42L);
    }

    @Test
    void reservaDeOtraQueSiQuedoAutorizada_seLaAsignaAElla() {
        Factura a = pendiente();
        a.setId(20L);
        a.setNumeroIntentado(42L);
        Factura b = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(b));
        when(facturaRepository.findById(20L)).thenReturn(Optional.of(a));
        when(facturaRepository.reservasSinResolver(eq(5), eq(6), eq(10L), any())).thenReturn(List.of(20L));
        when(wsfe.consultar(5, 6, 42L)).thenReturn(Optional.of(
                new WsfeService.ComprobanteAutorizado("A", LocalDate.now().plusDays(10), LocalDate.now(), new BigDecimal("2500.00"))));
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(42L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "B", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        assertThat(a.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(a.getCae()).isEqualTo("A");
        assertThat(b.getNumero()).isEqualTo(43L);
    }

    @Test
    void fallaQueLlegaDespuesDeUnaAnulacion_noLaVuelveAError() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenAnswer(inv -> {
            f.setEstado(EstadoFactura.ANULADA); // se canceló mientras se consultaba
            throw new AfipException("credenciales", false);
        });

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
    }

    @Test
    void reintentoTransitorioDespuesDeUnaAnulacion_noLaToca() {
        Factura f = pendiente();
        f.setIntentos(19);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(wsfe.ultimoAutorizado(5, 6)).thenAnswer(inv -> {
            f.setEstado(EstadoFactura.ANULADA);
            throw new AfipException("timeout", true);
        });

        service.emitir(10L);

        assertThat(f.getEstado()).isEqualTo(EstadoFactura.ANULADA);
        assertThat(f.getIntentos()).isEqualTo(19);
    }

    // ---------- helpers ----------

    private static FacturacionPosDTO pedido(DestinoFactura destino, String email) {
        FacturacionPosDTO p = new FacturacionPosDTO();
        p.setDestino(destino);
        p.setEmail(email);
        return p;
    }

    private static Compra compra(String monto, CompraDetalle... detalles) {
        return Compra.builder()
                .id(1L)
                .montoTotal(new BigDecimal(monto))
                .fechaVisita(LocalDate.now())
                .detalles(List.of(detalles))
                .build();
    }

    private static CompraDetalle entrada() {
        return CompraDetalle.builder().tipoEntrada(TipoEntrada.builder().nombre("General").build()).cantidad(1).build();
    }

    private static CompraDetalle articulo() {
        return CompraDetalle.builder().descripcionLibre("Souvenir").precioUnitario(new BigDecimal("500")).cantidad(1).build();
    }

    private static Factura pendiente() {
        return Factura.builder()
                .id(10L)
                .compra(Compra.builder().id(1L).build())
                .estado(EstadoFactura.PENDIENTE)
                .destino(DestinoFactura.IMPRIMIR)
                .puntoVenta(5)
                .tipoComprobante(6)
                .concepto(2)
                .fechaServicio(LocalDate.now())
                .importeTotal(new BigDecimal("2500.00"))
                .importeNeto(new BigDecimal("2066.12"))
                .importeIva(new BigDecimal("433.88"))
                .build();
    }
}
