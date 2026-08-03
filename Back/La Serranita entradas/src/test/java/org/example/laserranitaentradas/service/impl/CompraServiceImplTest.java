package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre tres reglas de negocio de CompraServiceImpl que no son evidentes leyendo el
 * modelo de datos: un regalo no puede reservarse en efectivo (el receptor terminaría
 * pagando lo que le "regalaron"), confirmarAprobado es idempotente (Mercado Pago puede
 * reenviar la misma notificación), y sólo se reembolsa lo pagado online y no usado.
 */
@ExtendWith(MockitoExtension.class)
class CompraServiceImplTest {

    @Mock private CompraRepository compraRepository;
    @Mock private TipoEntradaService tipoEntradaService;
    @Mock private CuponService cuponService;
    @Mock private DiaAperturaService diaAperturaService;
    @Mock private ClienteService clienteService;
    @Mock private UsuarioService usuarioService;
    @Mock private CalculoPrecioService calculoPrecioService;
    @Mock private EmailService emailService;
    @Mock private CajaService cajaService;
    @Mock private PagoService mercadoPagoEstrategia;
    @Mock private PagoService efectivoEstrategia;

    private CompraServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(mercadoPagoEstrategia.getFormaPago()).thenReturn(FormaPago.MERCADO_PAGO);
        lenient().when(efectivoEstrategia.getFormaPago()).thenReturn(FormaPago.EFECTIVO_BOLETERIA);

        service = new CompraServiceImpl(compraRepository, tipoEntradaService, cuponService, diaAperturaService,
                clienteService, usuarioService, calculoPrecioService, emailService, cajaService,
                List.of(mercadoPagoEstrategia, efectivoEstrategia));
    }

    // ---------- Regalo no puede reservarse en efectivo ----------

    @Test
    void create_regaloConEfectivo_rechaza() {
        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(null); // null = regalo
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("efectivo");

        verify(compraRepository, never()).save(any());
    }

    @Test
    void create_regaloConMercadoPagoSinDatosDeReceptor_rechazaPorFaltaDeReceptor() {
        // No debe confundirse con el guard de efectivo: con Mercado Pago la compra sigue
        // adelante hasta la siguiente validación (datos del receptor).
        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(null);
        request.setFormaPago(FormaPago.MERCADO_PAGO);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recibe");
    }

    // ---------- confirmarAprobado es idempotente ----------

    @Test
    void confirmarAprobado_desdePendientePago_apruebaYMandaComprobante() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.PENDIENTE_PAGO);
        compra.setFechaVisita(LocalDate.now().plusDays(5));
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean resultado = service.confirmarAprobado(1L);

        assertThat(resultado).isTrue();
        assertThat(compra.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(emailService).enviarComprobanteCompra(1L);
    }

    @Test
    void confirmarAprobado_siYaEstabaAprobada_noHaceNadaDeNuevo() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.APROBADO);
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        boolean resultado = service.confirmarAprobado(1L);

        assertThat(resultado).isFalse();
        verify(compraRepository, never()).save(any());
        verify(emailService, never()).enviarComprobanteCompra(anyLong());
    }

    @Test
    void confirmarAprobado_siYaEstabaUsada_noHaceNadaDeNuevo() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.USADO);
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        boolean resultado = service.confirmarAprobado(1L);

        assertThat(resultado).isFalse();
        verify(compraRepository, never()).save(any());
    }

    // ---------- Sólo se reembolsa lo pagado online y no usado ----------

    @Test
    void reembolsarCompra_siYaFueUsada_rechazaSinTocarMercadoPago() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.USADO);
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        assertThatThrownBy(() -> service.reembolsarCompra(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USADO");

        verify(compraRepository, never()).save(any());
    }

    @Test
    void reembolsarCompra_siEsReservaEnEfectivoSinCobrar_rechaza() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.RESERVADO_EFECTIVO);
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        assertThatThrownBy(() -> service.reembolsarCompra(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reembolsarCompra_siNoExiste_rechaza() {
        when(compraRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reembolsarCompra(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
