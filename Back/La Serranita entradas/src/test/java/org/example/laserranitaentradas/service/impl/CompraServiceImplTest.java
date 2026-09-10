package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.ArticuloVarioRepository;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.PromocionRepository;
import org.example.laserranitaentradas.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
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
    @Mock private CajaRepository cajaRepository;
    @Mock private PromocionRepository promocionRepository;
    @Mock private ArticuloVarioRepository articuloVarioRepository;
    @Mock private PagoService mercadoPagoEstrategia;
    @Mock private PagoService efectivoEstrategia;
    @Mock private PagoService reservaAdminEstrategia;
    @Mock private EntityManager em;
    /** El propio bean visto por el proxy de Spring. Sólo lo usa iniciarCompraConPago, que
     *  ningún test de acá ejercita (necesitaría el contexto de Spring para que el proxy exista
     *  de verdad): va un mock para poder construir el service. */
    @Mock private CompraService autoReferencia;

    private CompraServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(mercadoPagoEstrategia.getFormaPago()).thenReturn(FormaPago.MERCADO_PAGO);
        lenient().when(efectivoEstrategia.getFormaPago()).thenReturn(FormaPago.EFECTIVO_BOLETERIA);
        lenient().when(reservaAdminEstrategia.getFormaPago()).thenReturn(FormaPago.RESERVA_ADMIN);
        lenient().when(reservaAdminEstrategia.getEstadoInicial()).thenReturn(EstadoCompra.APROBADO);
        service = new CompraServiceImpl(compraRepository, tipoEntradaService, cuponService, diaAperturaService,
                clienteService, usuarioService, calculoPrecioService, emailService, cajaService, cajaRepository, promocionRepository,
                articuloVarioRepository, List.of(mercadoPagoEstrategia, efectivoEstrategia, reservaAdminEstrategia), em,
                // `self` sólo lo usa iniciarCompraConPago para cruzar el proxy de Spring; los
                // tests llaman a create() directo, así que alcanza con el propio service.
                autoReferencia);
    }

    // ---------- RESERVA_ADMIN: no cobra nada por acá, sin importar el precio de lista ----------

    @Test
    void create_conFormaPagoReservaAdmin_montoTotalQuedaEnCeroAunqueLaEntradaTengaPrecio() {
        org.example.laserranitaentradas.model.entity.TipoEntrada general = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("111")).build();
        when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(general));
        when(diaAperturaService.getAbiertoByDate(any())).thenReturn(true);
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any())).thenReturn(List.of());
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        org.example.laserranitaentradas.model.dto.DetalleCompraDTO detalle = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalle.setTipoEntradaId(1L);
        detalle.setCantidad(2);

        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(LocalDate.now().plusDays(1));
        request.setFormaPago(FormaPago.RESERVA_ADMIN);
        request.setEntradas(List.of(detalle));

        Compra resultado = service.create(request);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("0");
        assertThat(resultado.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        // No se le pide precio a calculoPrecioService para nada: es gratis, no hay "precio de grupo" que calcular.
        verify(calculoPrecioService, never()).calcularTotal(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    // ---------- Un tipo "Solo POS" no se puede comprar por la vía pública, aunque el request lo pida a mano ----------

    @Test
    void create_conTipoSoloPos_rechaza() {
        org.example.laserranitaentradas.model.entity.TipoEntrada cuponFisico = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("Cupón físico").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("100")).soloPos(true).build();
        when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(cuponFisico));
        when(diaAperturaService.getAbiertoByDate(any())).thenReturn(true);
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any())).thenReturn(List.of());
        when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new java.math.BigDecimal("100"));

        org.example.laserranitaentradas.model.dto.DetalleCompraDTO detalle = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalle.setTipoEntradaId(1L);
        detalle.setCantidad(1);

        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(LocalDate.now().plusDays(1));
        request.setFormaPago(FormaPago.MERCADO_PAGO);
        request.setEntradas(List.of(detalle));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está disponible");

        verify(compraRepository, never()).save(any());
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

    // ---------- Descuento en venta POS: promo con nombre o manual, mutuamente excluyentes ----------

    private void mockearVentaPosBasica() {
        org.example.laserranitaentradas.model.entity.TipoEntrada general = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L)
                .nombre("General")
                .tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true)
                .precio(new java.math.BigDecimal("100"))
                .build();
        lenient().when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(general));
        lenient().when(calculoPrecioService.calcularTotal(
                        org.mockito.ArgumentMatchers.eq(general), org.mockito.ArgumentMatchers.eq(2), any()))
                .thenReturn(new java.math.BigDecimal("200"));
        lenient().when(usuarioService.obtenerUsuarioPorId(anyLong())).thenReturn(Optional.of(new org.example.laserranitaentradas.model.entity.Usuario()));
        org.example.laserranitaentradas.model.entity.Caja caja = new org.example.laserranitaentradas.model.entity.Caja();
        caja.setId(1L);
        lenient().when(cajaService.getAbiertaOrThrow(anyLong())).thenReturn(caja);
        lenient().when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any())).thenReturn(List.of());
        lenient().when(compraRepository.countByFechaVisita(any())).thenReturn(0L);
        lenient().when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private org.example.laserranitaentradas.model.dto.VentaPosRequestDTO ventaPosBasica() {
        org.example.laserranitaentradas.model.dto.DetalleCompraDTO detalle = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalle.setTipoEntradaId(1L);
        detalle.setCantidad(2);
        org.example.laserranitaentradas.model.dto.VentaPosRequestDTO request = new org.example.laserranitaentradas.model.dto.VentaPosRequestDTO();
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        request.setEntradas(List.of(detalle));
        return request;
    }

    @Test
    void registrarVentaPos_conPromocionPorcentaje_aplicaElDescuentoYLoGuardaEnDescuentoAplicado() {
        mockearVentaPosBasica();
        org.example.laserranitaentradas.model.entity.Promocion promo = org.example.laserranitaentradas.model.entity.Promocion.builder()
                .id(5L).nombre("Folleto").porcentajeDescuento(new java.math.BigDecimal("10")).activo(true).build();
        when(promocionRepository.findById(5L)).thenReturn(Optional.of(promo));

        var request = ventaPosBasica();
        request.setPromocionId(5L);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("20"); // 10% de 200
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("180");
    }

    @Test
    void registrarVentaPos_conDescuentoManualMonto_loRestaDelTotal() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setDescuentoManualMonto(new java.math.BigDecimal("50"));

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("50");
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("150");
    }

    @Test
    void registrarVentaPos_conPromoYDescuentoManualJuntos_rechaza() {
        mockearVentaPosBasica();
        org.example.laserranitaentradas.model.entity.Promocion promo = org.example.laserranitaentradas.model.entity.Promocion.builder()
                .id(5L).nombre("Folleto").porcentajeDescuento(new java.math.BigDecimal("10")).activo(true).build();
        lenient().when(promocionRepository.findById(5L)).thenReturn(Optional.of(promo));

        var request = ventaPosBasica();
        request.setPromocionId(5L);
        request.setDescuentoManualMonto(new java.math.BigDecimal("50"));

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarVentaPos_conPromocionInactiva_rechaza() {
        mockearVentaPosBasica();
        org.example.laserranitaentradas.model.entity.Promocion promoInactiva = org.example.laserranitaentradas.model.entity.Promocion.builder()
                .id(5L).nombre("Vencida").porcentajeDescuento(new java.math.BigDecimal("10")).activo(false).build();
        when(promocionRepository.findById(5L)).thenReturn(Optional.of(promoInactiva));

        var request = ventaPosBasica();
        request.setPromocionId(5L);

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarVentaPos_conDescuentoQueSuperaElTotal_loTopeaEnElTotal() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setDescuentoManualMonto(new java.math.BigDecimal("9999"));

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("200");
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("0");
    }

    @Test
    void registrarVentaPos_sinDescuento_descuentoAplicadoQuedaEnCero() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("0");
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("200");
    }

    @Test
    void registrarVentaPos_sinCobro_conTotalCero_seRegistraComoVentaDePuerta() {
        mockearVentaPosBasica();
        // Precio del tipo en $0 (todo bonificado): el total queda en 0.
        org.example.laserranitaentradas.model.entity.TipoEntrada bonificada = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(2L).nombre("Bonificada").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(java.math.BigDecimal.ZERO).build();
        when(tipoEntradaService.findById(2L)).thenReturn(Optional.of(bonificada));
        when(calculoPrecioService.calcularTotal(org.mockito.ArgumentMatchers.eq(bonificada), org.mockito.ArgumentMatchers.eq(2), any()))
                .thenReturn(java.math.BigDecimal.ZERO);

        org.example.laserranitaentradas.model.dto.DetalleCompraDTO detalle = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalle.setTipoEntradaId(2L);
        detalle.setCantidad(2);
        var request = new org.example.laserranitaentradas.model.dto.VentaPosRequestDTO();
        request.setFormaPago(FormaPago.SIN_COBRO);
        request.setEntradas(List.of(detalle));

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getFormaPago()).isEqualTo(FormaPago.SIN_COBRO);
        assertThat(resultado.getEstado()).isEqualTo(EstadoCompra.VENDIDO_EN_PUERTA);
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("0");
    }

    @Test
    void registrarVentaPos_sinCobro_conMontoACobrar_rechaza() {
        mockearVentaPosBasica(); // el tipo General vale 200
        var request = ventaPosBasica();
        request.setFormaPago(FormaPago.SIN_COBRO);

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monto a cobrar");
    }

    // ---------- Cobrar una anticipada RESERVADO_EFECTIVO cargada en el POS ----------

    private Compra reservaEfectivo(Long id) {
        org.example.laserranitaentradas.model.entity.TipoEntrada general = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("100")).build();
        org.example.laserranitaentradas.model.entity.CompraDetalle det = org.example.laserranitaentradas.model.entity.CompraDetalle.builder()
                .tipoEntrada(general).cantidad(2).build();
        return Compra.builder()
                .id(id)
                .estado(EstadoCompra.RESERVADO_EFECTIVO)
                .formaPago(FormaPago.EFECTIVO_BOLETERIA)
                .fechaVisita(LocalDate.now())
                .codigoReserva("260101-9")
                .montoTotal(new java.math.BigDecimal("200"))
                .descuentoAplicado(java.math.BigDecimal.ZERO)
                .detalles(new ArrayList<>(List.of(det)))
                .cliente(Cliente.builder().dni("30111222").nombre("Ana").apellido("Gómez").build())
                .build();
    }

    @Test
    void registrarVentaPos_conCompraReservada_cierraLaReservaExistenteSinCrearOtra() {
        mockearVentaPosBasica();
        Compra reserva = reservaEfectivo(50L);
        when(compraRepository.findById(50L)).thenReturn(Optional.of(reserva));

        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getId()).isEqualTo(50L);
        assertThat(resultado.getCodigoReserva()).isEqualTo("260101-9"); // no se genera uno nuevo
        assertThat(resultado.getEstado()).isEqualTo(EstadoCompra.USADO);
        assertThat(resultado.getCaja()).isNotNull();
        assertThat(resultado.getFechaValidacion()).isNotNull();
        assertThat(resultado.getCliente()).isNotNull();
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("200");
    }

    @Test
    void registrarVentaPos_conCompraReservada_queNoEstaReservadoEfectivo_rechaza() {
        mockearVentaPosBasica();
        Compra reserva = reservaEfectivo(50L);
        reserva.setEstado(EstadoCompra.APROBADO);
        when(compraRepository.findById(50L)).thenReturn(Optional.of(reserva));

        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registrarVentaPos_conCompraReservada_repreciaSegunLaFormaDePagoElegida() {
        mockearVentaPosBasica();
        // Al pasar a tarjeta, el precio deja de tener el descuento de grupo: calcularTotal
        // devuelve más para esa forma de pago.
        org.example.laserranitaentradas.model.entity.TipoEntrada general = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("100")).build();
        lenient().when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(general));
        when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(FormaPago.TARJETA)))
                .thenReturn(new java.math.BigDecimal("260"));
        when(compraRepository.findById(50L)).thenReturn(Optional.of(reservaEfectivo(50L)));

        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.TARJETA);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getFormaPago()).isEqualTo(FormaPago.TARJETA);
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("260");
    }

    // ---------- Cola offline: reintentar no puede cobrar dos veces ----------

    @Test
    void registrarVentaPos_conClaveYaProcesada_devuelveLaCompraGuardadaSinCobrarDeNuevo() {
        // El caso que hace segura la cola offline: la venta original llegó y se guardó, pero la
        // respuesta se perdió en el corte. El reintento trae la misma clave y no cobra dos veces.
        Compra yaRegistrada = Compra.builder().id(123L).idempotencyKey("clave-venta-1").build();
        when(compraRepository.findByIdempotencyKey("clave-venta-1")).thenReturn(Optional.of(yaRegistrada));
        var request = ventaPosBasica();
        request.setIdempotencyKey("clave-venta-1");

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getId()).isEqualTo(123L);
        verify(compraRepository, never()).save(any());
    }

    @Test
    void registrarVentaPos_conFechaOriginal_registraEseMomentoYNoElDeSincronizacion() {
        // Una venta cobrada a las 23:50 sin señal y sincronizada pasada la medianoche tiene que
        // quedar como visita de ESE día, no del siguiente, y con la hora real en el detalle de caja.
        java.time.LocalDateTime cuandoSeCobro = java.time.LocalDateTime.of(2026, 8, 10, 23, 50);
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setIdempotencyKey("clave-venta-2");
        request.setFechaOriginal(cuandoSeCobro);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getFechaValidacion()).isEqualTo(cuandoSeCobro);
        assertThat(resultado.getFechaVisita()).isEqualTo(cuandoSeCobro.toLocalDate());
        assertThat(resultado.getIdempotencyKey()).isEqualTo("clave-venta-2");
    }

    // ---------- Cobro en dólares: sigue siendo EFECTIVO_BOLETERIA, sólo cambia la moneda física ----------

    @Test
    void registrarVentaPos_conCotizacionDolar_enFormaDePagoQueNoEsEfectivo_rechaza() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setFormaPago(FormaPago.TARJETA);
        request.setCotizacionDolar(new java.math.BigDecimal("1200"));
        request.setDolaresRecibidos(new java.math.BigDecimal("1"));

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarVentaPos_conCotizacionNegativaOCero_rechaza() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setCotizacionDolar(java.math.BigDecimal.ZERO);
        request.setDolaresRecibidos(new java.math.BigDecimal("1"));

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarVentaPos_conCotizacionSinDolaresRecibidos_rechaza() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setCotizacionDolar(new java.math.BigDecimal("1200"));

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarVentaPos_conDolaresRecibidosQueNoAlcanzanParaCubrirElTotal_rechaza() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setCotizacionDolar(new java.math.BigDecimal("1200"));
        // Total 200 (ver mockearVentaPosBasica); a 1200 hacen falta ~0.167 dólares, 0.1 no alcanza.
        request.setDolaresRecibidos(new java.math.BigDecimal("0.1"));

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarVentaPos_conDolares_guardaCotizacionYDolaresRecibidos_yQuedaComoEfectivo() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        request.setCotizacionDolar(new java.math.BigDecimal("1200"));
        request.setDolaresRecibidos(new java.math.BigDecimal("1"));

        Compra resultado = service.registrarVentaPos(request, 9L);

        // Sigue siendo EFECTIVO_BOLETERIA: no es una forma de pago aparte.
        assertThat(resultado.getFormaPago()).isEqualTo(FormaPago.EFECTIVO_BOLETERIA);
        assertThat(resultado.getCotizacionDolar()).isEqualByComparingTo("1200");
        assertThat(resultado.getDolaresRecibidos()).isEqualByComparingTo("1");
    }

    @Test
    void registrarVentaPos_sinCotizacion_noGuardaNadaDeDolares() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getCotizacionDolar()).isNull();
        assertThat(resultado.getDolaresRecibidos()).isNull();
    }

    // ---------- Artículos varios: catálogo o libres, no exigen pase obligatorio por sí solos ----------

    private org.example.laserranitaentradas.model.dto.LineaArticuloPosDTO articuloLibre(String descripcion, String precio, int cantidad) {
        var dto = new org.example.laserranitaentradas.model.dto.LineaArticuloPosDTO();
        dto.setDescripcionLibre(descripcion);
        dto.setPrecioUnitario(new java.math.BigDecimal(precio));
        dto.setCantidad(cantidad);
        return dto;
    }

    @Test
    void registrarVentaPos_soloArticulosSinEntradas_sePermiteYNoExigePaseObligatorio() {
        mockearVentaPosBasica();
        var request = new org.example.laserranitaentradas.model.dto.VentaPosRequestDTO();
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        request.setArticulos(List.of(articuloLibre("Cuadrito souvenir", "500", 2)));

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("1000");
        assertThat(resultado.getDetalles()).hasSize(1);
        assertThat(resultado.getDetalles().get(0).getTipoEntrada()).isNull();
        assertThat(resultado.getDetalles().get(0).getDescripcionLibre()).isEqualTo("Cuadrito souvenir");
        assertThat(resultado.getDetalles().get(0).getPrecioUnitario()).isEqualByComparingTo("500");
    }

    @Test
    void registrarVentaPos_ventaMixtaDeEntradasYArticulos_siguExigiendoElPaseObligatorio() {
        // Sólo un artículo, sin ningún pase obligatorio de entrada: tiene que rechazar igual
        // que rechazaría si sólo hubiera un pase de menor sin un adulto.
        lenient().when(usuarioService.obtenerUsuarioPorId(anyLong())).thenReturn(Optional.of(new org.example.laserranitaentradas.model.entity.Usuario()));
        org.example.laserranitaentradas.model.entity.Caja caja = new org.example.laserranitaentradas.model.entity.Caja();
        caja.setId(1L);
        lenient().when(cajaService.getAbiertaOrThrow(anyLong())).thenReturn(caja);

        org.example.laserranitaentradas.model.entity.TipoEntrada menor = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(2L).nombre("Menor").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(false).precio(new java.math.BigDecimal("0")).build();
        when(tipoEntradaService.findById(2L)).thenReturn(Optional.of(menor));
        lenient().when(calculoPrecioService.calcularTotal(
                        org.mockito.ArgumentMatchers.eq(menor), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(java.math.BigDecimal.ZERO);

        org.example.laserranitaentradas.model.dto.DetalleCompraDTO detalleMenor = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalleMenor.setTipoEntradaId(2L);
        detalleMenor.setCantidad(1);

        var request = new org.example.laserranitaentradas.model.dto.VentaPosRequestDTO();
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        request.setEntradas(List.of(detalleMenor));
        request.setArticulos(List.of(articuloLibre("Cuadrito souvenir", "500", 1)));

        assertThatThrownBy(() -> service.registrarVentaPos(request, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void registrarVentaPos_articuloDeCatalogo_resuelveElArticuloVarioYUsaElPrecioCargado() {
        mockearVentaPosBasica();
        org.example.laserranitaentradas.model.entity.ArticuloVario cuadrito = org.example.laserranitaentradas.model.entity.ArticuloVario.builder()
                .id(3L).nombre("Cuadrito").precioSugerido(new java.math.BigDecimal("500")).activo(true).build();
        when(articuloVarioRepository.findById(3L)).thenReturn(Optional.of(cuadrito));

        var articuloDto = new org.example.laserranitaentradas.model.dto.LineaArticuloPosDTO();
        articuloDto.setArticuloVarioId(3L);
        articuloDto.setPrecioUnitario(new java.math.BigDecimal("450")); // el cajero lo bajó de 500 a 450
        articuloDto.setCantidad(1);

        var request = ventaPosBasica(); // 2x General a 200 total
        request.setArticulos(List.of(articuloDto));

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("650"); // 200 + 450
        assertThat(resultado.getDetalles()).hasSize(2);
        var lineaArticulo = resultado.getDetalles().stream().filter(d -> d.getArticuloVario() != null).findFirst().orElseThrow();
        assertThat(lineaArticulo.getArticuloVario().getNombre()).isEqualTo("Cuadrito");
        assertThat(lineaArticulo.getPrecioUnitario()).isEqualByComparingTo("450");
    }

    @Test
    void construirDetalles_noExplotaCuandoUnaCompraPreviaDelDiaTieneUnaLineaDeArticulo() {
        // El loop de cupo diario itera TODAS las compras del día, incluidas las que ya
        // tengan líneas de artículo (sin tipoEntrada): no puede explotar con NPE ahí.
        mockearVentaPosBasica();

        Compra compraConArticulo = new Compra();
        compraConArticulo.setEstado(EstadoCompra.VENDIDO_EN_PUERTA);
        org.example.laserranitaentradas.model.entity.CompraDetalle detalleArticulo = new org.example.laserranitaentradas.model.entity.CompraDetalle();
        detalleArticulo.setTipoEntrada(null);
        detalleArticulo.setDescripcionLibre("Cuadrito souvenir");
        detalleArticulo.setCantidad(1);
        compraConArticulo.setDetalles(List.of(detalleArticulo));
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any())).thenReturn(List.of(compraConArticulo));

        var request = ventaPosBasica();

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("200");
    }

    // ---------- actualizarContacto: el DNI del titular vive en un Cliente compartido entre
    // compras (reutilizado por similitud de nombre al comprar, ver create()) — cambiarlo ahí
    // pisaría el dato de cualquier otra compra que apunte al mismo registro. ----------

    @Test
    void actualizarContacto_cambiaElDniDelTitular_creaUnClienteNuevoYNoTocaElCompartido() {
        Cliente clienteCompartido = Cliente.builder().id(1L).dni("30111222").nombre("Juan").apellido("Pérez").build();
        Compra compra = new Compra();
        compra.setId(7L);
        compra.setCliente(clienteCompartido);
        compra.setFechaVisita(LocalDate.now().plusDays(1)); // no es regalo
        when(compraRepository.findById(7L)).thenReturn(Optional.of(compra));
        when(clienteService.create(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        EditarContactoRequest request = new EditarContactoRequest();
        request.setDni("30999888");

        Compra resultado = service.actualizarContacto(7L, request);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteService).create(captor.capture());
        assertThat(captor.getValue().getDni()).isEqualTo("30999888");
        assertThat(captor.getValue().getNombre()).isEqualTo("Juan"); // se conserva lo que ya tenía
        // El cliente compartido original queda exactamente como estaba: otras compras que lo
        // usen no se ven afectadas por este cambio puntual.
        assertThat(clienteCompartido.getDni()).isEqualTo("30111222");
        assertThat(resultado.getCliente()).isNotSameAs(clienteCompartido);
        assertThat(resultado.getCliente().getDni()).isEqualTo("30999888");
    }

    @Test
    void actualizarContacto_conElMismoDniQueYaTenia_noCreaUnClienteNuevo() {
        Cliente cliente = Cliente.builder().id(1L).dni("30111222").nombre("Juan").apellido("Pérez").build();
        Compra compra = new Compra();
        compra.setId(7L);
        compra.setCliente(cliente);
        compra.setFechaVisita(LocalDate.now().plusDays(1));
        when(compraRepository.findById(7L)).thenReturn(Optional.of(compra));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        EditarContactoRequest request = new EditarContactoRequest();
        request.setDni("30111222"); // idéntico al que ya tenía
        request.setApellido("González");

        Compra resultado = service.actualizarContacto(7L, request);

        verify(clienteService, never()).create(any());
        assertThat(resultado.getCliente()).isSameAs(cliente);
        assertThat(resultado.getCliente().getApellido()).isEqualTo("González");
    }

    @Test
    void actualizarContacto_deUnRegalo_editaElReceptorDniDirectoSinTocarNingunCliente() {
        Compra compra = new Compra();
        compra.setId(7L);
        compra.setCliente(null); // un regalo no necesariamente tiene un Cliente del comprador cargado
        compra.setFechaVisita(null); // regalo
        compra.setReceptorDni("40111222");
        when(compraRepository.findById(7L)).thenReturn(Optional.of(compra));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        EditarContactoRequest request = new EditarContactoRequest();
        request.setReceptorDni("40999888");

        Compra resultado = service.actualizarContacto(7L, request);

        verify(clienteService, never()).create(any());
        assertThat(resultado.getReceptorDni()).isEqualTo("40999888");
    }

    // ---------- Cupo diario por tipo de entrada (maximoPorDia) ----------

    /**
     * Arma el escenario "un tipo de entrada con tope diario" y devuelve el request de compra.
     * `yaVendidos` se simula con compras existentes de ese día, que es de donde sale el conteo
     * real (cantidadVendidaPorTipoEnElDia recorre las compras de la fecha).
     */
    private CompraRequestDTO pedidoConTope(Integer maximoPorDia, int cantidadPedida, int yaVendidos) {
        var tipo = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("100")).maximoPorDia(maximoPorDia).build();
        when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(tipo));
        when(diaAperturaService.getAbiertoByDate(any())).thenReturn(true);

        List<Compra> yaExistentes = yaVendidos == 0 ? List.of() : List.of(Compra.builder()
                .id(999L).estado(EstadoCompra.APROBADO)
                .detalles(new ArrayList<>(List.of(
                        org.example.laserranitaentradas.model.entity.CompraDetalle.builder()
                                .tipoEntrada(tipo).cantidad(yaVendidos).build())))
                .build());
        lenient().when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any()))
                .thenReturn(yaExistentes);

        lenient().when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new java.math.BigDecimal("100"));
        lenient().when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        var detalle = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalle.setTipoEntradaId(1L);
        detalle.setCantidad(cantidadPedida);

        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(LocalDate.now().plusDays(1));
        request.setFormaPago(FormaPago.MERCADO_PAGO);
        request.setEntradas(List.of(detalle));
        return request;
    }

    @Test
    void create_conCupoDiarioAgotado_rechazaLaCompra() {
        CompraRequestDTO request = pedidoConTope(10, 1, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Se alcanzó el cupo diario");

        verify(compraRepository, never()).save(any());
    }

    @Test
    void create_pidiendoMasDeLoQueQueda_rechaza() {
        CompraRequestDTO request = pedidoConTope(10, 4, 8); // quedan 2, pide 4

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Se alcanzó el cupo diario");

        verify(compraRepository, never()).save(any());
    }

    @Test
    void create_justoHastaElTope_dejaPasar() {
        CompraRequestDTO request = pedidoConTope(10, 2, 8); // quedan 2, pide 2

        Compra resultado = service.create(request);

        assertThat(resultado).isNotNull();
        verify(compraRepository).save(any(Compra.class));
    }

    @Test
    void create_conFechaDeVisita_tomaElLockQueSerializaElCupoYLaNumeracion() {
        CompraRequestDTO request = pedidoConTope(10, 1, 0);

        service.create(request);

        // Sin este lock, dos compras simultáneas para el último lugar leen las dos "queda 1".
        verify(compraRepository, org.mockito.Mockito.atLeastOnce()).bloquearFechaDeVisita(anyLong());
    }

    // ---------- Cupón: el límite de usos lo hace valer la base, no una lectura previa ----------

    @Test
    void create_conCuponQueOtraCompraAgotoRecienAhora_rechazaEnVezDeRegalarElDescuento() {
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("PROMO").activo(true).usosMaximos(1).usosActuales(0)
                .porcentajeDescuento(new java.math.BigDecimal("50"))
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        when(cuponService.getByCode("PROMO")).thenReturn(Optional.of(cupon));
        // La lectura de arriba dice que queda 1 uso, pero entre esa lectura y el consumo otra
        // compra se llevó el último: el consumo atómico devuelve false.
        when(cuponService.consumirUso(9L)).thenReturn(false);

        CompraRequestDTO request = pedidoConTope(null, 1, 0);
        request.setCuponCodigo("PROMO");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya no está disponible");

        verify(compraRepository, never()).save(any());
    }

    @Test
    void create_conCupon_elMontoACobrarYaVieneConElDescuentoRestado() {
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("MITAD").activo(true).usosMaximos(5).usosActuales(0)
                .porcentajeDescuento(new java.math.BigDecimal("50"))
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        when(cuponService.getByCode("MITAD")).thenReturn(Optional.of(cupon));
        when(cuponService.consumirUso(9L)).thenReturn(true);

        // pedidoConTope stubea el precio en 100 por unidad.
        CompraRequestDTO request = pedidoConTope(null, 1, 0);
        request.setCuponCodigo("MITAD");

        Compra resultado = service.create(request);

        // montoTotal es lo que se le cobra, no el bruto: si no, el front muestra 50 y Mercado
        // Pago cobra 100.
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("50");
        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("50");
    }

    @Test
    void create_conCuponQueCubreTodoYPagoOnline_rechazaSinQuemarElUsoDelCupon() {
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("GRANDE").activo(true).usosMaximos(5).usosActuales(0)
                .montoDescuento(new java.math.BigDecimal("999999"))
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        when(cuponService.getByCode("GRANDE")).thenReturn(Optional.of(cupon));

        CompraRequestDTO request = pedidoConTope(null, 1, 0);
        request.setCuponCodigo("GRANDE");

        // Mercado Pago no acepta una preferencia por $0: se corta antes, con un mensaje que
        // explica qué hacer, en vez de reventar al pedir el checkout.
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cubre el total");

        // Y sobre todo: no se consumió el uso. Antes el consumo iba primero, así que una
        // compra que igual iba a fallar se llevaba puesto un uso del cupón.
        verify(cuponService, never()).consumirUso(any());
        verify(compraRepository, never()).save(any());
    }

    @Test
    void create_conCuponDisponible_loConsumeUnaSolaVezYAplicaElDescuento() {
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("PROMO").activo(true).usosMaximos(5).usosActuales(0)
                .porcentajeDescuento(new java.math.BigDecimal("50"))
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        when(cuponService.getByCode("PROMO")).thenReturn(Optional.of(cupon));
        when(cuponService.consumirUso(9L)).thenReturn(true);

        CompraRequestDTO request = pedidoConTope(null, 1, 0);
        request.setCuponCodigo("PROMO");

        Compra resultado = service.create(request);

        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("50");
        verify(cuponService, org.mockito.Mockito.times(1)).consumirUso(9L);
        // El contador ya no se toca a mano: lo movió la misma sentencia que autorizó el uso.
        verify(cuponService, never()).update(any());
    }

    // ---------- Checkout abandonado: qué pasa con una compra que SÍ se pagó ----------
    //
    // Es el escenario más caro de todos: el cliente pagó, el webhook se perdió, y a las 3 h
    // pasa el barrido de checkouts abandonados. Si ahí se cancela la reserva, esa persona
    // llega al parque y no existe — sin ningún aviso ni forma de enterarse.

    /** Compra en PENDIENTE_PAGO, por Mercado Pago, vieja: candidata a que la barra el scheduler. */
    private Compra checkoutPendiente(Long id) {
        return Compra.builder()
                .id(id)
                .estado(EstadoCompra.PENDIENTE_PAGO)
                .formaPago(FormaPago.MERCADO_PAGO)
                .fechaVisita(LocalDate.now().plusDays(10))
                .codigoReserva("260101-1")
                .montoTotal(new java.math.BigDecimal("34300"))
                .descuentoAplicado(java.math.BigDecimal.ZERO)
                .detalles(new ArrayList<>())
                .build();
    }

    @Test
    void expirarCheckoutAbandonado_siMercadoPagoNoResponde_noCancelaLaCompra() throws Exception {
        Compra pendiente = checkoutPendiente(70L);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        when(compraRepository.findById(70L)).thenReturn(Optional.of(pendiente));
        // Mercado Pago caído / timeout / error de red: NO sabemos si pagó.
        org.mockito.Mockito.doThrow(new RuntimeException("Mercado Pago no responde"))
                .when(espia).hayPagoAprobadoEnMercadoPago(any());

        espia.expirarCheckoutAbandonado(70L);

        // Lo importante: sigue viva. Antes se cancelaba, porque "no pude preguntar" y
        // "no pagó" se veían exactamente igual desde acá.
        assertThat(pendiente.getEstado()).isEqualTo(EstadoCompra.PENDIENTE_PAGO);
        verify(compraRepository, never()).save(any());
    }

    @Test
    void expirarCheckoutAbandonado_siMercadoPagoConfirmaElPago_apruebaEnVezDeCancelar() throws Exception {
        Compra pendiente = checkoutPendiente(71L);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        when(compraRepository.findById(71L)).thenReturn(Optional.of(pendiente));
        org.mockito.Mockito.doReturn(true).when(espia).hayPagoAprobadoEnMercadoPago(any());

        espia.expirarCheckoutAbandonado(71L);

        assertThat(pendiente.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        // Y se le manda el comprobante, que es lo que nunca le llegó por el webhook perdido.
        verify(emailService).enviarComprobanteCompra(71L);
    }

    @Test
    void expirarCheckoutAbandonado_siMercadoPagoDiceQueNoPago_siLaCancela() throws Exception {
        Compra pendiente = checkoutPendiente(72L);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        when(compraRepository.findById(72L)).thenReturn(Optional.of(pendiente));
        org.mockito.Mockito.doReturn(false).when(espia).hayPagoAprobadoEnMercadoPago(any());

        espia.expirarCheckoutAbandonado(72L);

        // Este es el caso legítimo: se verificó y no hay pago, así que libera el lugar.
        assertThat(pendiente.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        verify(compraRepository).save(pendiente);
    }

    @Test
    void iniciarCompraConPago_siNoSePuedeIniciarElPago_cancelaYDevuelveElUsoDelCupon() throws Exception {
        Compra creada = checkoutPendiente(76L);
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("PROMO").activo(false).usosMaximos(1).usosActuales(1)
                .porcentajeDescuento(new java.math.BigDecimal("50"))
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        creada.setCupon(cupon);

        // create() se llama a través del proxy de Spring (self), que en el test es un mock.
        when(autoReferencia.create(any())).thenReturn(creada);
        when(compraRepository.findById(76L)).thenReturn(Optional.of(creada));
        when(mercadoPagoEstrategia.procesarPago(creada))
                .thenThrow(new RuntimeException("Mercado Pago no responde"));

        assertThatThrownBy(() -> service.iniciarCompraConPago(new CompraRequestDTO()))
                .hasMessageContaining("Mercado Pago no responde");

        // El cliente nunca vio el checkout, así que no pagó: no tiene sentido dejarle tomado
        // el cupón ni el lugar del día hasta que pase el barrido tres horas después.
        assertThat(creada.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        assertThat(cupon.getUsosActuales()).isZero();
        assertThat(cupon.getActivo()).isTrue();
    }

    @Test
    void confirmarAprobado_sobreUnaCompraReembolsada_noLaRevive() {
        Compra reembolsada = checkoutPendiente(74L);
        reembolsada.setEstado(EstadoCompra.REEMBOLSADA);
        when(compraRepository.findById(74L)).thenReturn(Optional.of(reembolsada));

        // Aviso tardío de Mercado Pago sobre una compra a la que ya se le devolvió la plata.
        assertThat(service.confirmarAprobado(74L)).isFalse();

        assertThat(reembolsada.getEstado()).isEqualTo(EstadoCompra.REEMBOLSADA);
        verify(emailService, never()).enviarComprobanteCompra(74L);
    }

    @Test
    void confirmarAprobado_sobreUnaCanceladaQueSePagoDespues_siLaRevive() {
        Compra cancelada = checkoutPendiente(75L);
        cancelada.setEstado(EstadoCompra.CANCELADO);
        when(compraRepository.findById(75L)).thenReturn(Optional.of(cancelada));

        // Decisión deliberada: el cliente pagó, así que tiene que tener su entrada. Se prefiere
        // un lugar de más en el día antes que dejarlo afuera habiendo pagado.
        assertThat(service.confirmarAprobado(75L)).isTrue();

        assertThat(cancelada.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(emailService).enviarComprobanteCompra(75L);
    }

    @Test
    void confirmarAprobado_dosVeces_noReenviaElComprobante() {
        Compra pendiente = checkoutPendiente(73L);
        when(compraRepository.findById(73L)).thenReturn(Optional.of(pendiente));

        assertThat(service.confirmarAprobado(73L)).isTrue();
        // Segunda notificación de Mercado Pago para el mismo pago (las reintenta).
        assertThat(service.confirmarAprobado(73L)).isFalse();

        verify(emailService, org.mockito.Mockito.times(1)).enviarComprobanteCompra(73L);
    }
}
