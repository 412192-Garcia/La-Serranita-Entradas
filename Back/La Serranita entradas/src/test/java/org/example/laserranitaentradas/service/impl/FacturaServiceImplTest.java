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
        // Fecha y hora fiscales en hora de Argentina, sin depender de la zona del servidor.
        java.time.ZoneId arg = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
        assertThat(f.getFechaEmision()).isEqualTo(LocalDate.now(arg));
        assertThat(f.getEmitidaEn()).isNotNull();
        assertThat(java.time.Duration.between(f.getEmitidaEn(), java.time.LocalDateTime.now(arg)).abs().getSeconds()).isLessThan(60);
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

    // ---------- compras online (Mercado Pago): factura + confirmación en un solo mail ----------

    private Compra compraOnline(EstadoCompra estado, String email) {
        Compra c = compra("50000", entrada());
        c.setFormaPago(FormaPago.MERCADO_PAGO);
        c.setEstado(estado);
        c.setContactEmail(email);
        c.setCodigoReserva("261005-1");
        when(em.find(Compra.class, 1L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)).thenReturn(c);
        when(em.find(Compra.class, 1L)).thenReturn(c);
        return c;
    }

    /** La factura que se guarda queda disponible para la emisión (findById), como en la base. */
    private void guardarYEncontrar() {
        java.util.concurrent.atomic.AtomicReference<Factura> guardada = new java.util.concurrent.atomic.AtomicReference<>();
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> {
            Factura f = inv.getArgument(0);
            if (f.getId() == null) f.setId(99L);
            if (f.getTipoComprobante() == 6) guardada.set(f);
            return f;
        });
        when(facturaRepository.findById(99L)).thenAnswer(inv -> Optional.ofNullable(guardada.get()));
        when(wsfe.ultimoAutorizado(12, 6)).thenReturn(4L);
    }

    @Test
    void online_pagoConMercadoPago_seEncargaDeLaConfirmacionYPideLaFacturaEnSegundoPlano() {
        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        Compra c = compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");

        assertThat(service.solicitarOnline(c)).isTrue();

        verify(eventPublisher).publishEvent(new FacturaServiceImpl.CompraOnlinePagadaEvent(1L));
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void online_sinPuntoDeVentaOnline_oPagoEnPuerta_laConfirmacionLaMandaElQueLlama() {
        Compra c = compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");
        assertThat(service.solicitarOnline(c)).isFalse();

        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        c.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        assertThat(service.solicitarOnline(c)).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void online_arcaAutoriza_unSoloMailConLaFacturaAdjunta() {
        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");
        guardarYEncontrar();
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.procesarCompraOnlinePagada(1L);

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository, atLeastOnce()).save(captor.capture());
        Factura f = captor.getAllValues().get(0);
        assertThat(f.getPuntoVenta()).isEqualTo(12);
        // Sin mail propio: viaja adjunta a la confirmación.
        assertThat(f.getDestino()).isEqualTo(DestinoFactura.NINGUNO);
        verify(emailService).enviarComprobanteCompraConFactura(1L, 99L);
        verify(emailService, never()).enviarComprobanteCompra(anyLong());
        verify(emailService, never()).enviarFactura(anyLong());
    }

    @Test
    void online_arcaFalla_laConfirmacionSaleSolaYLaFacturaDespuesPorMail() {
        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");
        guardarYEncontrar();
        when(wsfe.solicitarCae(any())).thenThrow(new AfipException("ARCA no responde", true));
        when(facturaRepository.pasarAMailSiNoEmitida(99L, "cliente@mail.com")).thenReturn(1);

        service.procesarCompraOnlinePagada(1L);

        verify(emailService).enviarComprobanteCompra(1L);
        verify(emailService, never()).enviarComprobanteCompraConFactura(anyLong(), anyLong());
        // Queda para mandarse sola cuando ARCA la autorice.
        verify(facturaRepository).pasarAMailSiNoEmitida(99L, "cliente@mail.com");
        verify(emailService, never()).enviarFactura(anyLong());
    }

    @Test
    void online_siFallaElMailConFactura_seMandanPorSeparado() {
        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");
        guardarYEncontrar();
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));
        doThrow(new RuntimeException("SMTP")).when(emailService).enviarComprobanteCompraConFactura(1L, 99L);
        when(facturaRepository.pasarAMailSiNoEmitida(99L, "cliente@mail.com")).thenReturn(0); // ya está emitida

        service.procesarCompraOnlinePagada(1L);

        verify(emailService).enviarComprobanteCompra(1L);
        verify(facturaRepository).pasarAMail(99L, "cliente@mail.com");
        verify(emailService).enviarFactura(99L);
    }

    @Test
    void online_unErrorAlFacturar_igualSaleLaConfirmacion() {
        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");
        when(facturaRepository.save(any(Factura.class))).thenThrow(new RuntimeException("base caída"));

        org.assertj.core.api.Assertions.assertThatCode(() -> service.procesarCompraOnlinePagada(1L)).doesNotThrowAnyException();

        verify(emailService).enviarComprobanteCompra(1L);
    }

    @Test
    void online_siYaTieneFactura_noDuplica() {
        ReflectionTestUtils.setField(service, "puntoVentaOnline", "12");
        compraOnline(EstadoCompra.APROBADO, "cliente@mail.com");
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(emitida()));

        service.procesarCompraOnlinePagada(1L);

        verify(facturaRepository, never()).save(any());
        verify(emailService).enviarComprobanteCompra(1L);
    }

    // ---------- control de facturas ----------

    @Test
    void controlNumeracion_avisaSoloLosDesfasesReales() {
        when(afipClient.esProduccion()).thenReturn(true);
        when(facturaRepository.puntosDeVentaUsados()).thenReturn(List.of(5));
        // Facturas: coincide.
        when(facturaRepository.ultimoNumeroEmitido(5, 6)).thenReturn(10L);
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(10L);
        // Notas de crédito: ARCA tiene una más que la base, y no es una reservada sin resolver.
        when(facturaRepository.ultimoNumeroEmitido(5, 8)).thenReturn(2L);
        when(wsfe.ultimoAutorizado(5, 8)).thenReturn(3L);

        var desfases = service.controlarNumeracion();

        assertThat(desfases).containsExactly(new org.example.laserranitaentradas.model.dto.ControlFacturacionDTO.Desfase(5, 8, 2, 3));
        assertThat(service.controlFacturacion().desfases()).hasSize(1);
        assertThat(service.idsAlertasFacturacion()).contains(-2L);
    }

    @Test
    void controlNumeracion_unNumeroReservadoQueArcaAutorizo_noEsDesfase() {
        when(afipClient.esProduccion()).thenReturn(true);
        when(facturaRepository.puntosDeVentaUsados()).thenReturn(List.of(5));
        when(facturaRepository.ultimoNumeroEmitido(anyInt(), anyInt())).thenReturn(10L);
        when(facturaRepository.ultimoNumeroReservadoSinResolver(anyInt(), anyInt())).thenReturn(11L);
        when(wsfe.ultimoAutorizado(anyInt(), anyInt())).thenReturn(11L);

        assertThat(service.controlarNumeracion()).isEmpty();
    }

    @Test
    void controlNumeracion_unPuntoDeVentaQueArcaNoReconoce_noFrenaElControlDeLosDemas() {
        when(afipClient.esProduccion()).thenReturn(true);
        // Facturas viejas de homologación con el pto vta 37, que para el CUIT real no existe.
        when(facturaRepository.puntosDeVentaUsados()).thenReturn(List.of(37));
        when(wsfe.ultimoAutorizado(eq(37), anyInt())).thenThrow(new org.example.laserranitaentradas.service.afip.AfipException(
                "ARCA: 11002 - El punto de venta no se encuentra habilitado a usar en el presente WS", false));

        assertThatThrownBy(() -> service.controlarNumeracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("punto de venta 37 (ARCA: 11002")
                .hasMessageContaining("Los demás coinciden con ARCA");
        // El de boletería sí se controló.
        verify(wsfe, atLeastOnce()).ultimoAutorizado(intThat(pv -> pv != 37), anyInt());
        assertThat(service.controlFacturacion().numeracionControladaEn()).isNotNull();
    }

    @Test
    void controlNumeracion_enHomologacion_noConsultaArca() {
        assertThat(service.controlarNumeracion()).isEmpty();
        verify(wsfe, never()).ultimoAutorizado(anyInt(), anyInt());
    }

    @Test
    void alertas_facturasConProblemaYCertificadoPorVencer() {
        Factura error = pendiente();
        error.setEstado(EstadoFactura.ERROR);
        when(facturaRepository.conProblemas(any())).thenReturn(List.of(error));
        when(afipClient.vencimientoCertificado()).thenReturn(Optional.of(LocalDate.now().plusDays(10)));

        assertThat(service.idsAlertasFacturacion()).containsExactlyInAnyOrder(10L, -1L);
        assertThat(service.controlFacturacion().diasParaVencer()).isBetween(9L, 11L);
    }

    @Test
    void alertas_todoBien_sinAviso() {
        when(afipClient.vencimientoCertificado()).thenReturn(Optional.of(LocalDate.now().plusYears(1)));

        assertThat(service.idsAlertasFacturacion()).isEmpty();
    }

    @Test
    void exportarCsv_paraExcelEnEspanol() {
        Factura f = emitida();
        f.setPuntoVenta(11);
        f.setNumero(7L);
        f.setImporteTotal(new BigDecimal("34300.00"));
        f.setImporteNeto(new BigDecimal("28347.11"));
        f.setImporteIva(new BigDecimal("5952.89"));
        f.setEmitidaEn(java.time.LocalDateTime.of(2026, 10, 5, 14, 32));
        f.setFechaEmision(LocalDate.of(2026, 10, 5));
        f.setCompra(Compra.builder().id(1L).codigoReserva("261005-3").build());
        Factura nc = emitida();
        nc.setTipoComprobante(8);
        nc.setPuntoVenta(11);
        nc.setNumero(1L);
        nc.setComprobanteAsociado(f);
        nc.setFechaEmision(LocalDate.of(2026, 10, 5));
        f.setAnulacionPedida(true);
        when(facturaRepository.emitidasEntre(any(), any())).thenReturn(List.of(f, nc));

        String csv = service.exportarCsv(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(csv).startsWith("\uFEFFFecha;Hora;Comprobante");
        assertThat(csv).contains("05/10/2026;14:32;Factura B;0011;00000007;123;");
        assertThat(csv).contains(";34300,00;28347,11;5952,89;Anulada con nota de crédito;261005-3;");
        assertThat(csv).contains("Nota de Crédito B;0011;00000001").contains(";0011-00000007\r\n");
    }

    @Test
    void totales_porPuntoDeVentaYTipo() {
        Factura a = emitida();
        a.setPuntoVenta(11);
        a.setImporteTotal(new BigDecimal("100"));
        a.setImporteNeto(new BigDecimal("82.64"));
        a.setImporteIva(new BigDecimal("17.36"));
        Factura b = emitida();
        b.setPuntoVenta(11);
        b.setImporteTotal(new BigDecimal("200"));
        b.setImporteNeto(new BigDecimal("165.29"));
        b.setImporteIva(new BigDecimal("34.71"));
        when(facturaRepository.emitidasEntre(any(), any())).thenReturn(List.of(a, b));

        var totales = service.totales(LocalDate.now(), LocalDate.now());

        assertThat(totales).hasSize(1);
        assertThat(totales.get(0).cantidad()).isEqualTo(2);
        assertThat(totales.get(0).total()).isEqualByComparingTo("300");
    }

    // ---------- factura manual ----------

    private static org.example.laserranitaentradas.model.dto.FacturaManualDTO.Item item(String tipo, int cant, String desc, String subtotal) {
        return new org.example.laserranitaentradas.model.dto.FacturaManualDTO.Item(tipo, cant, desc, new BigDecimal(subtotal));
    }

    private static org.example.laserranitaentradas.model.dto.FacturaManualDTO manual(DestinoFactura destino, String email,
            org.example.laserranitaentradas.model.dto.FacturaManualDTO.Item... items) {
        return new org.example.laserranitaentradas.model.dto.FacturaManualDTO(List.of(items), destino, email, null);
    }

    @Test
    void manual_sinVenta_conLosItemsEImportesCargados() {
        var r = service.emitirManual(manual(DestinoFactura.NINGUNO, null,
                item("ENTRADA", 3, "General", "90000"), item("LIBRE", 1, "Uso del quincho\tgrupo", "21000")));

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(captor.capture());
        Factura f = captor.getValue();
        assertThat(f.getCompra()).isNull();
        assertThat(f.getImporteTotal()).isEqualByComparingTo("111000");
        assertThat(f.getImporteNeto()).isEqualByComparingTo("91735.54");
        assertThat(f.getImporteIva()).isEqualByComparingTo("19264.46");
        assertThat(f.getPuntoVenta()).isEqualTo(5);
        assertThat(f.getConcepto()).isEqualTo(2); // entradas y texto libre = servicios
        // El tab de la descripción no rompe el formato de las líneas.
        assertThat(f.getDetalle()).isEqualTo("3\tGeneral\t90000.00\n1\tUso del quincho grupo\t21000.00");
        assertThat(r.getCompraId()).isNull();
        verify(eventPublisher).publishEvent(any(FacturaServiceImpl.FacturaSolicitadaEvent.class));
    }

    @Test
    void manual_conArticulos_esConceptoProductosYServicios() {
        service.emitirManual(manual(DestinoFactura.NINGUNO, null,
                item("ENTRADA", 1, "General", "30000"), item("ARTICULO", 2, "Gorra", "10000")));

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(captor.capture());
        assertThat(captor.getValue().getConcepto()).isEqualTo(3);
    }

    @Test
    void manual_validaciones() {
        assertThatThrownBy(() -> service.emitirManual(manual(DestinoFactura.NINGUNO, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(DestinoFactura.NINGUNO, null, item("LIBRE", 1, "  ", "100"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(DestinoFactura.NINGUNO, null, item("LIBRE", 1, "Algo", "0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(DestinoFactura.NINGUNO, null, item("LIBRE", 0, "Algo", "10"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(DestinoFactura.MAIL, "no-es-mail", item("LIBRE", 1, "Algo", "10"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(facturaRepository, never()).save(any());
    }

    @Test
    void manual_sinFacturacionConfigurada_error() {
        when(afipClient.estaConfigurado()).thenReturn(false);

        assertThatThrownBy(() -> service.emitirManual(manual(DestinoFactura.NINGUNO, null, item("LIBRE", 1, "Algo", "10"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anularManual_emiteNotaDeCredito() {
        Factura f = emitida();
        f.setCompra(null);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));

        service.anularFactura(10L);

        Factura nc = capturarGuardadas().stream().filter(x -> x.getTipoComprobante() == 8).findFirst().orElseThrow();
        assertThat(nc.getComprobanteAsociado()).isSameAs(f);
        assertThat(nc.getCompra()).isNull();
        assertThat(nc.getDestino()).isEqualTo(DestinoFactura.NINGUNO);
    }

    @Test
    void anularFactura_deUnaVenta_notaDeCreditoYLaVentaQuedaIgual() {
        Factura f = emitida();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));

        service.anularFactura(10L);

        Factura nc = capturarGuardadas().stream().filter(x -> x.getTipoComprobante() == 8).findFirst().orElseThrow();
        assertThat(nc.getComprobanteAsociado()).isSameAs(f);
        assertThat(f.getAnulacionPedida()).isTrue();
    }

    @Test
    void anularFactura_yaAnulada_error() {
        Factura f = emitida();
        f.setAnulacionPedida(true);
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.anularFactura(10L)).isInstanceOf(IllegalStateException.class);
    }

    // ---------- numeración: en producción sale de la base ----------

    @Test
    void produccion_numeroDeLaBase_unSoloRequestPorFactura() {
        when(afipClient.esProduccion()).thenReturn(true);
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(facturaRepository.ultimoNumeroEmitido(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        assertThat(f.getNumero()).isEqualTo(42L);
        verify(wsfe, never()).ultimoAutorizado(anyInt(), anyInt());
    }

    @Test
    void produccion_primeraFactura_lePreguntaAArca() {
        when(afipClient.esProduccion()).thenReturn(true);
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(facturaRepository.ultimoNumeroEmitido(5, 6)).thenReturn(null);
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        assertThat(f.getNumero()).isEqualTo(42L);
    }

    @Test
    void produccion_baseDesfasada_reintentaEnseguidaConElNumeroDeArca() {
        when(afipClient.esProduccion()).thenReturn(true);
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(facturaRepository.ultimoNumeroEmitido(5, 6)).thenReturn(30L);
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any()))
                .thenReturn(new WsfeService.ResultadoCae(false, null, null, List.of("10016 - no es el proximo"), List.of(10016)))
                .thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        ArgumentCaptor<WsfeService.SolicitudCae> captor = ArgumentCaptor.forClass(WsfeService.SolicitudCae.class);
        verify(wsfe, times(2)).solicitarCae(captor.capture());
        assertThat(captor.getAllValues()).extracting(WsfeService.SolicitudCae::numero).containsExactly(31L, 42L);
        assertThat(f.getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(f.getNumero()).isEqualTo(42L);
    }

    @Test
    void homologacion_siempreLePreguntaAArca_porqueElCuitEsCompartido() {
        Factura f = pendiente();
        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f));
        when(facturaRepository.ultimoNumeroEmitido(5, 6)).thenReturn(99L);
        when(wsfe.ultimoAutorizado(5, 6)).thenReturn(41L);
        when(wsfe.solicitarCae(any())).thenReturn(new WsfeService.ResultadoCae(true, "1", LocalDate.now(), List.of(), List.of()));

        service.emitir(10L);

        assertThat(f.getNumero()).isEqualTo(42L);
        verify(facturaRepository, never()).ultimoNumeroEmitido(any(), any());
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
    void editarVenta_mismoTotalYMismosItems_noTocaLaFactura() {
        Factura f = emitida();
        f.setDetalle("1\tGeneral");
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        service.alEditarVenta(compra("2500", entrada()), new BigDecimal("2500.00"));

        verify(facturaRepository, never()).save(any());
    }

    @Test
    void editarVenta_mismoTotalPeroOtrosItems_notaDeCreditoYFacturaNueva() {
        Factura f = emitida();
        f.setDetalle("1\tGeneral");
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        // Se cambió la entrada por un artículo: mismo total, pero la factura ya no dice lo que se vendió.
        service.alEditarVenta(compra("2500", articulo()), new BigDecimal("2500"));

        List<Factura> guardadas = capturarGuardadas();
        assertThat(guardadas).anyMatch(x -> x.getTipoComprobante() == 8);
        assertThat(guardadas).anyMatch(x -> x.getTipoComprobante() == 6 && x != f && x.getDetalle().contains("Souvenir"));
    }

    @Test
    void anular_cancelaLosTrabajosDeImpresionEnCola() {
        Factura f = emitida();
        when(facturaRepository.findFirstByCompraIdAndTipoComprobanteOrderByIdDesc(1L, 6)).thenReturn(Optional.of(f));

        service.alCancelarVenta(Compra.builder().id(1L).build());

        verify(impresionService).cancelarTrabajos(10L);
    }

    @Test
    void solicitar_conDestinoNinguno_rechazado() {
        assertThatThrownBy(() -> service.solicitar(compra("2500", entrada()), pedido(DestinoFactura.NINGUNO, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(facturaRepository, never()).save(any());
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
