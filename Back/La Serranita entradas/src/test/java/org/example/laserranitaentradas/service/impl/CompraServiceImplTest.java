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

    private CompraServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(mercadoPagoEstrategia.getFormaPago()).thenReturn(FormaPago.MERCADO_PAGO);
        lenient().when(efectivoEstrategia.getFormaPago()).thenReturn(FormaPago.EFECTIVO_BOLETERIA);
        lenient().when(reservaAdminEstrategia.getFormaPago()).thenReturn(FormaPago.RESERVA_ADMIN);
        lenient().when(reservaAdminEstrategia.getEstadoInicial()).thenReturn(EstadoCompra.APROBADO);

        service = new CompraServiceImpl(compraRepository, tipoEntradaService, cuponService, diaAperturaService,
                clienteService, usuarioService, calculoPrecioService, emailService, cajaService, cajaRepository, promocionRepository,
                articuloVarioRepository, List.of(mercadoPagoEstrategia, efectivoEstrategia, reservaAdminEstrategia), em);
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
}
