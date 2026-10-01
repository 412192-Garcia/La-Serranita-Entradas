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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacturaServiceImplTest {

    @Mock private FacturaRepository facturaRepository;
    @Mock private WsfeService wsfe;
    @Mock private AfipSdkClient afipClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private org.example.laserranitaentradas.service.factura.ComprobanteFacturaService comprobanteFacturaService;
    @Mock private org.example.laserranitaentradas.service.factura.FacturaPdfGenerator pdfGenerator;
    @Mock private org.example.laserranitaentradas.service.EmailService emailService;
    @Mock private org.example.laserranitaentradas.service.impresion.ImpresionService impresionService;

    private FacturaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FacturaServiceImpl(facturaRepository, wsfe, afipClient, eventPublisher, transactionManager,
                comprobanteFacturaService, pdfGenerator, emailService, impresionService);
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
        Factura existente = Factura.builder().id(7L).build();
        when(facturaRepository.findByCompraId(1L)).thenReturn(Optional.of(existente));

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
        return CompraDetalle.builder().tipoEntrada(new TipoEntrada()).cantidad(1).build();
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
