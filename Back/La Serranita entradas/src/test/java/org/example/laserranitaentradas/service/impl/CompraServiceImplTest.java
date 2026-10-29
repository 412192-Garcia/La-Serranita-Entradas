package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.entity.ConfirmacionPago;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
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
 * Reglas de CompraServiceImpl que no se ven en el modelo: un regalo no se reserva en efectivo,
 * confirmarAprobado es idempotente y sólo se reembolsa lo pagado online sin usar.
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
    @Mock private FacturaService facturaService;
    @Mock private PagoService mercadoPagoEstrategia;
    @Mock private PagoService efectivoEstrategia;
    @Mock private PagoService reservaAdminEstrategia;
    @Mock private EntityManager em;
    @Mock private org.example.laserranitaentradas.service.point.CobroPointService cobroPointService;
    /** El bean a través del proxy; sólo lo usa iniciarCompraConPago, que acá no se prueba. */
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
                articuloVarioRepository, facturaService, List.of(mercadoPagoEstrategia, efectivoEstrategia, reservaAdminEstrategia), em,
                // Los tests llaman a create() directo: alcanza con el propio service.
                autoReferencia, cobroPointService);
        // Webhook y verificación directa confirman a través de `self`: el mock delega al service real.
        lenient().when(autoReferencia.confirmarAprobado(any(), any(), any())).thenAnswer(inv ->
                service.confirmarAprobado(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
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
        // Es gratis: no se pide precio.
        verify(calculoPrecioService, never()).calcularTotal(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    // ---------- Límite de compra online para el mismo día ----------

    private CompraRequestDTO pedidoParaHoy(FormaPago formaPago) {
        var detalle = new org.example.laserranitaentradas.model.dto.DetalleCompraDTO();
        detalle.setTipoEntradaId(1L);
        detalle.setCantidad(1);
        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(LocalDate.now());
        request.setFormaPago(formaPago);
        request.setEntradas(List.of(detalle));
        return request;
    }

    @Test
    void create_paraHoyPasadoElLimiteDeCompra_rechazaYNoGuardaNada() {
        when(diaAperturaService.getAbiertoByDate(any())).thenReturn(true);
        when(diaAperturaService.compraDelDiaCerrada(any(), any())).thenReturn(true);
        when(diaAperturaService.getLimiteDeCompra(any())).thenReturn(java.time.LocalDateTime.of(LocalDate.now(), java.time.LocalTime.of(17, 0)));

        assertThatThrownBy(() -> service.create(pedidoParaHoy(FormaPago.MERCADO_PAGO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya cerró")
                .hasMessageContaining("17:00");

        verify(compraRepository, never()).save(any());
    }

    @Test
    void create_reservaDeAdminParaHoy_noSeCortaPorElLimite() {
        // Una reserva de admin no es compra del público: el corte no la toca.
        org.example.laserranitaentradas.model.entity.TipoEntrada general = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("111")).build();
        when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(general));
        when(diaAperturaService.getAbiertoByDate(any())).thenReturn(true);
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any())).thenReturn(List.of());
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        Compra resultado = service.create(pedidoParaHoy(FormaPago.RESERVA_ADMIN));

        assertThat(resultado.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(diaAperturaService, never()).compraDelDiaCerrada(any(), any());
    }

    // ---------- Un tipo "Solo POS" no se compra online aunque se pida a mano ----------

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
        // Con Mercado Pago sigue hasta la siguiente validación (receptor).
        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(null);
        request.setFormaPago(FormaPago.MERCADO_PAGO);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recibe");
    }

    @Test
    void create_regaloSinDniDelComprador_guardaAlCompradorSinDni() {
        // A quien compra un regalo no se le pide DNI, pero se guarda como cliente.
        CompraRequestDTO request = new CompraRequestDTO();
        request.setFecha(null);
        request.setFormaPago(FormaPago.MERCADO_PAGO);
        org.example.laserranitaentradas.model.dto.ClienteDTO comprador = new org.example.laserranitaentradas.model.dto.ClienteDTO();
        comprador.setNombre("Ana");
        comprador.setApellido("Pérez");
        comprador.setEmail("ana@example.com");
        request.setCliente(comprador);
        org.example.laserranitaentradas.model.dto.ReceptorRegaloDTO receptor = new org.example.laserranitaentradas.model.dto.ReceptorRegaloDTO();
        receptor.setNombre("Juan Gómez");
        receptor.setEmail("juan@example.com");
        receptor.setDni("30111222");
        request.setReceptor(receptor);
        request.setEntradas(List.of());
        when(clienteService.create(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        // Lo que sigue (entradas, pago) no importa acá: sin entradas la compra no prospera.
        try {
            service.create(request);
        } catch (RuntimeException ignorada) {
        }

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteService).create(captor.capture());
        assertThat(captor.getValue().getDni()).isNull();
        assertThat(captor.getValue().getNombre()).isEqualTo("Ana");
        verify(clienteService, never()).findAllByDni(any());
    }

    // ---------- confirmarAprobado es idempotente ----------

    @Test
    void confirmarAprobado_desdePendientePago_apruebaYMandaComprobante() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.PENDIENTE_PAGO);
        compra.setFechaVisita(LocalDate.now().plusDays(5));
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));
        when(compraRepository.aprobarSiSigueSinPagar(1L, "555")).thenReturn(1);

        boolean resultado = service.confirmarAprobado(1L, List.of(555L), ConfirmacionPago.WEBHOOK);

        assertThat(resultado).isTrue();
        assertThat(compra.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        // Es con lo que después se reembolsa, sin buscar por external_reference.
        assertThat(compra.getMpPaymentIds()).isEqualTo("555");
        // Sin facturación online (el mock devuelve false), la confirmación sale ya, como siempre.
        verify(facturaService).solicitarOnline(compra);
        verify(emailService).enviarComprobanteCompra(1L);
    }

    @Test
    void confirmarAprobado_conFacturacionOnline_laConfirmacionSaleConLaFactura() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.PENDIENTE_PAGO);
        compra.setFechaVisita(LocalDate.now().plusDays(5));
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));
        when(compraRepository.aprobarSiSigueSinPagar(1L, "555")).thenReturn(1);
        when(facturaService.solicitarOnline(compra)).thenReturn(true);

        assertThat(service.confirmarAprobado(1L, List.of(555L), ConfirmacionPago.WEBHOOK)).isTrue();

        // La manda la facturación online, con la factura adjunta (un solo mail).
        verify(emailService, never()).enviarComprobanteCompra(anyLong());
    }

    @Test
    void confirmarAprobado_siOtraConfirmacionSimultaneaGanoLaTransicion_noRepiteMailNiCupon() {
        // Las dos confirmaciones leyeron la compra sin pagar, pero el UPDATE condicional lo gana una sola.
        Compra cancelada = checkoutPendiente(79L);
        cancelada.setEstado(EstadoCompra.CANCELADO);
        cancelada.setCupon(org.example.laserranitaentradas.model.entity.Cupon.builder().id(9L).codigo("PROMO").build());
        when(compraRepository.findById(79L)).thenReturn(Optional.of(cancelada));
        when(compraRepository.aprobarSiSigueSinPagar(79L, "555")).thenReturn(0);

        assertThat(service.confirmarAprobado(79L, List.of(555L), ConfirmacionPago.WEBHOOK)).isFalse();

        verify(emailService, never()).enviarComprobanteCompra(anyLong());
        verify(cuponService, never()).consumirUso(anyLong());
        verify(facturaService, never()).solicitarOnline(any());
    }

    @Test
    void confirmarAprobado_siYaEstabaAprobada_noHaceNadaDeNuevo() {
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.APROBADO);
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        boolean resultado = service.confirmarAprobado(1L, List.of(555L), ConfirmacionPago.WEBHOOK);

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

        boolean resultado = service.confirmarAprobado(1L, List.of(555L), ConfirmacionPago.WEBHOOK);

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
    void reembolsarCompra_sinPagoDeMercadoPagoRegistrado_rechazaSinTocarMercadoPago() {
        // Por external_reference podía aparecer el pago de otra compra: sin el id guardado no se adivina.
        Compra compra = new Compra();
        compra.setId(1L);
        compra.setEstado(EstadoCompra.APROBADO);
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        assertThatThrownBy(() -> service.reembolsarCompra(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("panel de Mercado Pago");

        assertThat(compra.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(compraRepository, never()).save(any());
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
    void registrarVentaPos_pasaLaOpcionDeFacturaElegida() {
        mockearVentaPosBasica();
        var request = ventaPosBasica();
        var facturacion = new org.example.laserranitaentradas.model.dto.FacturacionPosDTO();
        facturacion.setDestino(org.example.laserranitaentradas.model.entity.DestinoFactura.IMPRIMIR);
        request.setFacturacion(facturacion);

        Compra resultado = service.registrarVentaPos(request, 9L);

        verify(facturaService).solicitar(resultado, facturacion);
    }

    @Test
    void registrarVentaPos_cobroDeReservaEnPuerta_tambienPasaLaOpcionDeFactura() {
        mockearVentaPosBasica();
        when(compraRepository.findById(50L)).thenReturn(Optional.of(reservaEfectivo(50L)));
        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        var facturacion = new org.example.laserranitaentradas.model.dto.FacturacionPosDTO();
        facturacion.setDestino(org.example.laserranitaentradas.model.entity.DestinoFactura.MAIL);
        facturacion.setEmail("cliente@mail.com");
        request.setFacturacion(facturacion);

        Compra resultado = service.registrarVentaPos(request, 9L);

        verify(facturaService).solicitar(resultado, facturacion);
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
        // En tarjeta no hay precio de grupo: calcularTotal devuelve más.
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

    // ---------- Reserva cobrada tras un aumento: el cliente paga lo que reservó ----------

    private static final java.math.BigDecimal PRECIO_AL_RESERVAR = new java.math.BigDecimal("100");

    private static java.math.BigDecimal igualA(String valor) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && p.compareTo(new java.math.BigDecimal(valor)) == 0);
    }

    /** Reserva de 2 General a 100 con escalón de grupo (total 180); el tipo hoy vale 150. */
    private Compra reservaAntesDelAumento(Long id) {
        org.example.laserranitaentradas.model.entity.TipoEntrada generalAumentado = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("150")).build();
        lenient().when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(generalAumentado));
        Compra reserva = reservaEfectivo(id);
        reserva.setMontoTotal(new java.math.BigDecimal("180"));
        reserva.getDetalles().get(0).setTipoEntrada(generalAumentado);
        reserva.getDetalles().get(0).setPrecioUnitario(PRECIO_AL_RESERVAR);
        when(compraRepository.findById(id)).thenReturn(Optional.of(reserva));
        // Cálculo de hoy en efectivo: no debería usarse si paga igual que como reservó.
        lenient().when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.eq(FormaPago.EFECTIVO_BOLETERIA), igualA("100")))
                .thenReturn(new java.math.BigDecimal("190"));
        lenient().when(calculoPrecioService.calcularAhorro(any(), org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.eq(FormaPago.EFECTIVO_BOLETERIA), igualA("100")))
                .thenReturn(new java.math.BigDecimal("10"));
        return reserva;
    }

    @Test
    void registrarVentaPos_reservaEnEfectivoSinCambios_cobraElMontoReservadoAunqueHayaSubidoElPrecio() {
        mockearVentaPosBasica();
        reservaAntesDelAumento(50L);
        // Aunque hoy el cálculo da 190 (escalones cambiados), se cobra lo reservado: 180.

        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("180");
        assertThat(resultado.getEstado()).isEqualTo(EstadoCompra.USADO);
    }

    @Test
    void registrarVentaPos_reservaCobradaConTarjeta_usaElPrecioDeListaDeCuandoSeReservo() {
        mockearVentaPosBasica();
        reservaAntesDelAumento(50L);
        // Sin escalón por grupo: 2 x 100 (el precio al reservar), no 2 x 150 (el de hoy).
        when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.eq(FormaPago.TARJETA), igualA("100")))
                .thenReturn(new java.math.BigDecimal("200"));

        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.TARJETA);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("200");
    }

    @Test
    void registrarVentaPos_reservaEnEfectivoConOtraCantidad_noReusaElMontoReservadoPeroSiElPrecioDeLista() {
        mockearVentaPosBasica();
        reservaAntesDelAumento(50L);
        when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.eq(3),
                        org.mockito.ArgumentMatchers.eq(FormaPago.EFECTIVO_BOLETERIA), igualA("100")))
                .thenReturn(new java.math.BigDecimal("270"));

        var request = ventaPosBasica();
        request.getEntradas().get(0).setCantidad(3);
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("270");
    }

    @Test
    void registrarVentaPos_reservaEnEfectivoSinCambios_conservaElDescuentoDelCuponYSumaElDelPos() {
        mockearVentaPosBasica();
        Compra reserva = reservaAntesDelAumento(50L);
        reserva.setMontoTotal(new java.math.BigDecimal("160"));          // 180 - cupón de 20
        reserva.setDescuentoAplicado(new java.math.BigDecimal("20"));
        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        request.setDescuentoManualMonto(new java.math.BigDecimal("10"));

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("150");
        assertThat(resultado.getDescuentoAplicado()).isEqualByComparingTo("30");
    }

    @Test
    void registrarVentaPos_laLineaCobradaGuardaElPrecioDeListaCongelado() {
        mockearVentaPosBasica();
        reservaAntesDelAumento(50L);
        lenient().when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.eq(FormaPago.TARJETA), igualA("100")))
                .thenReturn(new java.math.BigDecimal("200"));
        var request = ventaPosBasica();
        request.setCompraReservadaId(50L);
        request.setFormaPago(FormaPago.TARJETA);

        Compra resultado = service.registrarVentaPos(request, 9L);

        assertThat(resultado.getDetalles()).hasSize(1);
        assertThat(resultado.getDetalles().get(0).getPrecioUnitario()).isEqualByComparingTo("100");
    }

    @Test
    void cotizar_reservaEnEfectivoSinCambios_devuelveElMontoReservado() {
        mockearVentaPosBasica();
        reservaAntesDelAumento(50L);
        var request = new org.example.laserranitaentradas.model.dto.CotizacionRequestDTO();
        request.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        request.setEntradas(ventaPosBasica().getEntradas());
        request.setCompraReservadaId(50L);

        var cotizacion = service.cotizar(request);

        assertThat(cotizacion.getSubtotal()).isEqualByComparingTo("180");
        assertThat(cotizacion.getAhorro()).isEqualByComparingTo("20"); // lista congelada 200 - 180
    }

    @Test
    void cotizar_reservaConTarjeta_usaElPrecioDeListaCongelado() {
        mockearVentaPosBasica();
        reservaAntesDelAumento(50L);
        when(calculoPrecioService.calcularTotal(any(), org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.eq(FormaPago.TARJETA), igualA("100")))
                .thenReturn(new java.math.BigDecimal("200"));
        when(calculoPrecioService.calcularAhorro(any(), org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.eq(FormaPago.TARJETA), igualA("100")))
                .thenReturn(java.math.BigDecimal.ZERO);
        var request = new org.example.laserranitaentradas.model.dto.CotizacionRequestDTO();
        request.setFormaPago(FormaPago.TARJETA);
        request.setEntradas(ventaPosBasica().getEntradas());
        request.setCompraReservadaId(50L);

        var cotizacion = service.cotizar(request);

        assertThat(cotizacion.getSubtotal()).isEqualByComparingTo("200");
    }

    // ---------- Cola offline: reintentar no puede cobrar dos veces ----------

    @Test
    void registrarVentaPos_conClaveYaProcesada_devuelveLaCompraGuardadaSinCobrarDeNuevo() {
        // La venta original se guardó pero se perdió la respuesta: el reintento con la misma clave no cobra
        // dos veces.
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
        // Cobrada a las 23:50 sin señal y sincronizada después de medianoche: visita de ESE día y hora real.
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

    // ---------- Cobro en dólares: sigue siendo EFECTIVO_BOLETERIA ----------

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
        // Sólo un artículo, sin pase obligatorio: se rechaza.
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
        // El cupo diario recorre también compras con artículos (sin tipoEntrada): no puede tirar NPE.
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

    // ---------- actualizarContacto: el DNI del titular está en un Cliente compartido; cambiarlo ahí pisaría
    // otras compras ----------

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
        // El cliente compartido queda igual.
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

    /** Un tipo con tope diario; `yaVendidos` se simula con compras existentes del día. */
    private CompraRequestDTO pedidoConTope(Integer maximoPorDia, int cantidadPedida, int yaVendidos) {
        var tipo = org.example.laserranitaentradas.model.entity.TipoEntrada.builder()
                .id(1L).nombre("General").tipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA)
                .obligatorio(true).precio(new java.math.BigDecimal("100")).maximoPorDia(maximoPorDia).build();
        when(tipoEntradaService.findById(1L)).thenReturn(Optional.of(tipo));
        // lenient: un regalo (sin fecha) no pregunta si el día está abierto.
        lenient().when(diaAperturaService.getAbiertoByDate(any())).thenReturn(true);

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

    // ---------- Reintento de checkout: se reusa la compra pendiente ----------

    /** Un checkout de Mercado Pago sin pagar de hace un rato, de Santiago (DNI 30111222), por 2 Generales. */
    private Compra checkoutPendienteDe(String dni, String email, LocalDate fecha, int cantidad, String monto) {
        var tipo = tipoEntradaService.findById(1L).orElseThrow();
        Compra pendiente = Compra.builder()
                .id(500L).codigoReserva("261012-4")
                .estado(EstadoCompra.PENDIENTE_PAGO).formaPago(FormaPago.MERCADO_PAGO)
                .cliente(Cliente.builder().id(7L).dni(dni).nombre("Santiago").apellido("Larraya").build())
                .contactEmail(email)
                .fechaVisita(fecha)
                .montoTotal(new java.math.BigDecimal(monto)).descuentoAplicado(java.math.BigDecimal.ZERO)
                .detalles(new ArrayList<>(List.of(org.example.laserranitaentradas.model.entity.CompraDetalle.builder()
                        .tipoEntrada(tipo).cantidad(cantidad).build())))
                .build();
        pendiente.setFechaCreacion(java.time.LocalDateTime.now().minusMinutes(2));
        return pendiente;
    }

    /** El pedido de pago de Santiago, con sus datos de contacto (pedidoConTope arma el tipo y el cupo). */
    private CompraRequestDTO reintentoDeSantiago(CompraRequestDTO request, String email) {
        var cliente = new org.example.laserranitaentradas.model.dto.ClienteDTO();
        cliente.setDni("30111222");
        cliente.setNombre("Santiago");
        cliente.setApellido("Larraya");
        cliente.setEmail(email);
        cliente.setTelefono("3516854089");
        request.setCliente(cliente);
        lenient().when(clienteService.findAllByDni("30111222")).thenReturn(List.of(
                Cliente.builder().id(7L).dni("30111222").nombre("Santiago").apellido("Larraya").build()));
        return request;
    }

    @Test
    void create_reintentoDelMismoCheckout_reusaLaCompraPendienteSinContarSuCupo() {
        // Los 2 lugares que quedan son los de su propio checkout pendiente: no cuentan contra el reintento.
        CompraRequestDTO request = reintentoDeSantiago(pedidoConTope(10, 2, 0), "otro.mail@gmail.com");
        Compra pendiente = checkoutPendienteDe("30111222", "santiago@gmail.com", request.getFecha(), 2, "100");
        Compra otraDelDia = Compra.builder().id(999L).estado(EstadoCompra.APROBADO)
                .detalles(new ArrayList<>(List.of(org.example.laserranitaentradas.model.entity.CompraDetalle.builder()
                        .tipoEntrada(tipoEntradaService.findById(1L).orElseThrow()).cantidad(8).build())))
                .build();
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(any())).thenReturn(List.of(otraDelDia, pendiente));
        when(compraRepository.findCheckoutsPendientesDesde(any())).thenReturn(List.of(pendiente));

        Compra resultado = service.create(request);

        assertThat(resultado).isSameAs(pendiente);
        assertThat(resultado.getCodigoReserva()).isEqualTo("261012-4");
        // Cambió de mail entre intentos (el DNI es el mismo): manda lo último que tipeó.
        assertThat(resultado.getContactEmail()).isEqualTo("otro.mail@gmail.com");
        verify(compraRepository).save(pendiente);
        verify(compraRepository, org.mockito.Mockito.times(1)).save(any(Compra.class));
    }

    @Test
    void create_checkoutPendienteDeOtraPersona_creaUnaCompraNueva() {
        CompraRequestDTO request = reintentoDeSantiago(pedidoConTope(10, 2, 0), "santiago@gmail.com");
        Compra deOtro = checkoutPendienteDe("99888777", "otra@gmail.com", request.getFecha(), 2, "100");
        when(compraRepository.findCheckoutsPendientesDesde(any())).thenReturn(List.of(deOtro));

        Compra resultado = service.create(request);

        assertThat(resultado).isNotSameAs(deOtro);
    }

    @Test
    void create_checkoutPendienteConOtrasCantidades_creaUnaCompraNueva() {
        CompraRequestDTO request = reintentoDeSantiago(pedidoConTope(10, 3, 0), "santiago@gmail.com");
        Compra conDos = checkoutPendienteDe("30111222", "santiago@gmail.com", request.getFecha(), 2, "100");
        when(compraRepository.findCheckoutsPendientesDesde(any())).thenReturn(List.of(conDos));

        Compra resultado = service.create(request);

        assertThat(resultado).isNotSameAs(conDos);
    }

    @Test
    void create_checkoutPendienteConOtroPrecio_creaUnaCompraNueva() {
        // Mismas entradas, pero el precio cambió desde el primer intento: ya no es la misma compra.
        CompraRequestDTO request = reintentoDeSantiago(pedidoConTope(10, 2, 0), "santiago@gmail.com");
        Compra precioViejo = checkoutPendienteDe("30111222", "santiago@gmail.com", request.getFecha(), 2, "90");
        when(compraRepository.findCheckoutsPendientesDesde(any())).thenReturn(List.of(precioViejo));

        Compra resultado = service.create(request);

        assertThat(resultado).isNotSameAs(precioViejo);
        assertThat(resultado.getMontoTotal()).isEqualByComparingTo("100");
    }

    @Test
    void create_checkoutPendienteConElMismoMailPeroOtroDni_creaUnaCompraNueva() {
        // Una familia que comparte el mail: el checkout de uno no se le puede pasar al otro.
        CompraRequestDTO request = reintentoDeSantiago(pedidoConTope(10, 2, 0), "familia@gmail.com");
        Compra deSuHermana = checkoutPendienteDe("99888777", "familia@gmail.com", request.getFecha(), 2, "100");
        when(compraRepository.findCheckoutsPendientesDesde(any())).thenReturn(List.of(deSuHermana));

        Compra resultado = service.create(request);

        assertThat(resultado).isNotSameAs(deSuHermana);
        assertThat(deSuHermana.getCliente().getDni()).isEqualTo("99888777");
    }

    @Test
    void create_reintentoDeUnRegalo_reusaElClienteDeLaCompraEnVezDeCrearOtro() {
        CompraRequestDTO request = pedidoConTope(null, 2, 0);
        request.setFecha(null);
        var comprador = new org.example.laserranitaentradas.model.dto.ClienteDTO();
        comprador.setNombre("Santiago");
        comprador.setApellido("Larraya");
        comprador.setEmail("santiago@gmail.com");
        request.setCliente(comprador);
        var receptor = new org.example.laserranitaentradas.model.dto.ReceptorRegaloDTO();
        receptor.setNombre("Ana");
        receptor.setEmail("ana@gmail.com");
        receptor.setDni("40111222");
        request.setReceptor(receptor);
        Compra pendiente = checkoutPendienteDe(null, "santiago@gmail.com", null, 2, "100");
        pendiente.setReceptorDni("40111222");
        when(compraRepository.findCheckoutsPendientesDesde(any())).thenReturn(List.of(pendiente));

        Compra resultado = service.create(request);

        assertThat(resultado).isSameAs(pendiente);
        assertThat(resultado.isReusada()).isTrue();
        assertThat(resultado.getCliente().getId()).isEqualTo(7L);
        verify(clienteService, never()).create(any());
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
        // Otra compra se llevó el último uso entre la lectura y el consumo: devuelve false.
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

        // MP no acepta $0: se corta antes con un mensaje claro.
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cubre el total");

        // Y no se consumió el uso.
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

    // ---------- Checkout abandonado que SÍ se pagó ----------
    //
    // El cliente pagó, el webhook se perdió y pasa el barrido: si se cancela, llega al parque sin reserva.

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
                .when(espia).buscarPagosEnMercadoPago(any());

        espia.expirarCheckoutAbandonado(70L);

        // Sigue viva: "no pude preguntar" no es "no pagó".
        assertThat(pendiente.getEstado()).isEqualTo(EstadoCompra.PENDIENTE_PAGO);
        verify(compraRepository, never()).save(any());
    }

    @Test
    void expirarCheckoutAbandonado_siMercadoPagoConfirmaElPago_apruebaEnVezDeCancelar() throws Exception {
        Compra pendiente = checkoutPendiente(71L);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        when(compraRepository.findById(71L)).thenReturn(Optional.of(pendiente));
        when(compraRepository.aprobarSiSigueSinPagar(71L, "555")).thenReturn(1);
        var pago = pagoAprobado(555L, "34300", java.time.LocalDateTime.now());
        org.mockito.Mockito.doReturn(List.of(pago)).when(espia).buscarPagosEnMercadoPago(any());

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
        org.mockito.Mockito.doReturn(List.of()).when(espia).buscarPagosEnMercadoPago(any());

        espia.expirarCheckoutAbandonado(72L);

        // Este es el caso legítimo: se verificó y no hay pago, así que libera el lugar.
        assertThat(pendiente.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        assertThat(pendiente.getMotivoCancelacion()).isEqualTo("CHECKOUT_ABANDONADO");
        assertThat(pendiente.getDetalleCancelacion()).isEqualTo("No llegó a intentar el pago.");
        assertThat(pendiente.getCanceladaPor()).isEqualTo("SISTEMA");
        assertThat(pendiente.getFechaCancelacion()).isNotNull();
        verify(compraRepository).save(pendiente);
    }

    @Test
    void expirarCheckoutAbandonado_conIntentosRechazados_anotaQueContestoMercadoPago() throws Exception {
        Compra pendiente = checkoutPendiente(73L);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        when(compraRepository.findById(73L)).thenReturn(Optional.of(pendiente));
        var fondos = pagoConEstado(901L, "rejected", "cc_rejected_insufficient_amount", "visa");
        var fraude = pagoConEstado(902L, "rejected", "cc_rejected_high_risk", "master");
        org.mockito.Mockito.doReturn(List.of(fraude, fondos)).when(espia).buscarPagosEnMercadoPago(any());

        espia.expirarCheckoutAbandonado(73L);

        assertThat(pendiente.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        assertThat(pendiente.getMotivoCancelacion()).isEqualTo("PAGO_RECHAZADO");
        // En el orden en que se intentaron (por id de pago).
        assertThat(pendiente.getDetalleCancelacion()).isEqualTo(
                "visa: rechazado, fondos insuficientes · master: rechazado, rechazado por prevención de fraude");
    }

    @Test
    void describirIntentoDePago_statusDetailDesconocido_vaTalCual() {
        var pago = pagoConEstado(1L, "rejected", "cc_rejected_algo_nuevo", "amex");
        assertThat(CompraServiceImpl.describirIntentoDePago(pago)).isEqualTo("amex: rechazado, cc_rejected_algo_nuevo");
    }

    private com.mercadopago.resources.payment.Payment pagoConEstado(long id, String estado, String detalle, String medio) {
        var pago = org.mockito.Mockito.mock(com.mercadopago.resources.payment.Payment.class);
        lenient().when(pago.getId()).thenReturn(id);
        lenient().when(pago.getStatus()).thenReturn(estado);
        lenient().when(pago.getStatusDetail()).thenReturn(detalle);
        lenient().when(pago.getPaymentMethodId()).thenReturn(medio);
        lenient().when(pago.getTransactionAmount()).thenReturn(new java.math.BigDecimal("34300"));
        return pago;
    }

    // ---------- cancelarVenta pide motivo ----------

    @Test
    void cancelarVenta_sinMotivo_laCancelaIgual() {
        Compra venta = ventaDeCaja(80L);
        when(compraRepository.findById(80L)).thenReturn(Optional.of(venta));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelarVenta(80L, "  ");

        assertThat(venta.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        assertThat(venta.getMotivoCancelacion()).isEqualTo("ANULADA_EN_CAJA");
        assertThat(venta.getDetalleCancelacion()).isNull();
    }

    // ---------- Cambiar la fecha de una reserva ----------

    private static Compra reservaParaElDia(Long id, LocalDate fecha, int cantidad, Integer maximoPorDia) {
        TipoEntrada tipo = TipoEntrada.builder().id(1L).nombre("General").tipo(Tipo.ENTRADA)
                .precio(new java.math.BigDecimal("100")).maximoPorDia(maximoPorDia).build();
        Compra reserva = new Compra();
        reserva.setId(id);
        reserva.setCodigoReserva("261030-2");
        reserva.setEstado(EstadoCompra.APROBADO);
        reserva.setFormaPago(FormaPago.MERCADO_PAGO);
        reserva.setFechaVisita(fecha);
        reserva.setDetalles(new java.util.ArrayList<>(List.of(
                CompraDetalle.builder().tipoEntrada(tipo).cantidad(cantidad).compra(reserva).build())));
        return reserva;
    }

    @Test
    void cambiarFechaVisita_aUnDiaAbiertoConCupo_laMueveYAvisaPorMail() {
        LocalDate nueva = LocalDate.now().plusDays(5);
        Compra reserva = reservaParaElDia(90L, LocalDate.now().plusDays(2), 2, 10);
        when(compraRepository.findById(90L)).thenReturn(Optional.of(reserva));
        when(diaAperturaService.getAbiertoByDate(nueva)).thenReturn(true);
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(nueva)).thenReturn(List.of());
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cambiarFechaVisita(90L, nueva, true);

        assertThat(reserva.getFechaVisita()).isEqualTo(nueva);
        assertThat(reserva.getCodigoReserva()).isEqualTo("261030-2");
        verify(emailService).enviarComprobanteCompra(90L);
    }

    @Test
    void cambiarFechaVisita_sinCupoEseDia_noLaMueve() {
        LocalDate nueva = LocalDate.now().plusDays(5);
        Compra reserva = reservaParaElDia(91L, LocalDate.now().plusDays(2), 3, 10);
        Compra otraDeEseDia = reservaParaElDia(92L, nueva, 8, 10);
        when(compraRepository.findById(91L)).thenReturn(Optional.of(reserva));
        when(diaAperturaService.getAbiertoByDate(nueva)).thenReturn(true);
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(nueva)).thenReturn(List.of(otraDeEseDia));

        assertThatThrownBy(() -> service.cambiarFechaVisita(91L, nueva, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cupo");
        assertThat(reserva.getFechaVisita()).isEqualTo(LocalDate.now().plusDays(2));
        verify(compraRepository, never()).save(any());
    }

    @Test
    void cambiarFechaVisita_aUnDiaCerrado_noLaMueve() {
        LocalDate nueva = LocalDate.now().plusDays(5);
        Compra reserva = reservaParaElDia(93L, LocalDate.now().plusDays(2), 1, null);
        when(compraRepository.findById(93L)).thenReturn(Optional.of(reserva));
        when(diaAperturaService.getAbiertoByDate(nueva)).thenReturn(false);

        assertThatThrownBy(() -> service.cambiarFechaVisita(93L, nueva, false))
                .hasMessageContaining("cerrado");
        verify(compraRepository, never()).save(any());
    }

    @Test
    void cambiarFechaVisita_yaUsada_oRegalo_noSePuede() {
        LocalDate nueva = LocalDate.now().plusDays(5);
        Compra usada = reservaParaElDia(94L, LocalDate.now(), 1, null);
        usada.setEstado(EstadoCompra.USADO);
        Compra regalo = reservaParaElDia(95L, null, 1, null);
        regalo.setReceptorDni("40111222");
        when(compraRepository.findById(94L)).thenReturn(Optional.of(usada));
        when(compraRepository.findById(95L)).thenReturn(Optional.of(regalo));

        assertThatThrownBy(() -> service.cambiarFechaVisita(94L, nueva, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.cambiarFechaVisita(95L, nueva, false)).isInstanceOf(IllegalStateException.class);
        verify(compraRepository, never()).save(any());
    }

    @Test
    void cambiarFechaVisita_reservaGeneradaSinFecha_recibeElDia() {
        LocalDate nueva = LocalDate.now().plusDays(1);
        Compra reserva = reservaParaElDia(96L, null, 2, null);
        reserva.setFormaPago(FormaPago.RESERVA_ADMIN);
        when(compraRepository.findById(96L)).thenReturn(Optional.of(reserva));
        when(diaAperturaService.getAbiertoByDate(nueva)).thenReturn(true);
        when(compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(nueva)).thenReturn(List.of());
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cambiarFechaVisita(96L, nueva, false);

        assertThat(reserva.getFechaVisita()).isEqualTo(nueva);
        verify(emailService, never()).enviarComprobanteCompra(any());
    }

    private static Compra ventaDeCaja(Long id) {
        Compra venta = new Compra();
        venta.setId(id);
        venta.setCodigoReserva("261017-4");
        venta.setMontoTotal(new java.math.BigDecimal("34300"));
        venta.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        venta.setEstado(EstadoCompra.VENDIDO_EN_PUERTA);
        venta.setCaja(new org.example.laserranitaentradas.model.entity.Caja());
        return venta;
    }

    @Test
    void cancelarVenta_conMotivo_loGuardaEnLaCompra() {
        Compra venta = ventaDeCaja(81L);
        when(compraRepository.findById(81L)).thenReturn(Optional.of(venta));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelarVenta(81L, " Se cargó dos veces ");

        assertThat(venta.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        assertThat(venta.getMotivoCancelacion()).isEqualTo("ANULADA_EN_CAJA");
        assertThat(venta.getDetalleCancelacion()).isEqualTo("Se cargó dos veces");
        assertThat(venta.getFechaCancelacion()).isNotNull();
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

        // Nunca vio el checkout: se liberan cupón y lugar ya.
        assertThat(creada.getEstado()).isEqualTo(EstadoCompra.CANCELADO);
        // La devolución del uso la hace la base en una sentencia.
        verify(cuponService).liberarUso(9L);
        verify(cuponService, never()).update(any());
    }

    @Test
    void iniciarCompraConPago_reusadaYFallaMercadoPago_noLaCancelaNiDevuelveElCupon() throws Exception {
        // Ya tiene el link del intento anterior: puede estar pagando con ése.
        Compra reusada = checkoutPendiente(77L);
        reusada.setReusada(true);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn(List.of()).when(espia).buscarPagosEnMercadoPago(any());
        when(autoReferencia.create(any())).thenReturn(reusada);
        when(mercadoPagoEstrategia.procesarPago(reusada)).thenThrow(new RuntimeException("Mercado Pago no responde"));

        assertThatThrownBy(() -> espia.iniciarCompraConPago(new CompraRequestDTO()))
                .hasMessageContaining("Mercado Pago no responde");

        assertThat(reusada.getEstado()).isEqualTo(EstadoCompra.PENDIENTE_PAGO);
        verify(cuponService, never()).liberarUso(any());
        verify(compraRepository, never()).save(any());
    }

    @Test
    void iniciarCompraConPago_reusadaConUnPagoEnProceso_noDaOtroLink() throws Exception {
        Compra reusada = checkoutPendiente(78L);
        reusada.setReusada(true);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        var enProceso = pagoConEstado(903L, "in_process", "pending_contingency", "visa");
        org.mockito.Mockito.doReturn(List.of(enProceso)).when(espia).buscarPagosEnMercadoPago(any());
        when(autoReferencia.create(any())).thenReturn(reusada);

        assertThatThrownBy(() -> espia.iniciarCompraConPago(new CompraRequestDTO()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pago en proceso");

        verify(mercadoPagoEstrategia, never()).procesarPago(any());
    }

    @Test
    void iniciarCompraConPago_reusadaYaPaga_laConfirmaEnVezDeCobrarDeNuevo() throws Exception {
        Compra reusada = checkoutPendiente(79L);
        reusada.setReusada(true);
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        var pago = pagoAprobado(556L, "34300", java.time.LocalDateTime.now());
        org.mockito.Mockito.doReturn(List.of(pago)).when(espia).buscarPagosEnMercadoPago(any());
        when(autoReferencia.create(any())).thenReturn(reusada);
        when(compraRepository.findById(79L)).thenReturn(Optional.of(reusada));
        when(compraRepository.aprobarSiSigueSinPagar(79L, "556")).thenReturn(1);

        assertThatThrownBy(() -> espia.iniciarCompraConPago(new CompraRequestDTO()))
                .hasMessageContaining("ya se acreditó");

        assertThat(reusada.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(mercadoPagoEstrategia, never()).procesarPago(any());
    }

    @Test
    void confirmarAprobado_sobreUnaCompraReembolsada_noLaRevive() {
        Compra reembolsada = checkoutPendiente(74L);
        reembolsada.setEstado(EstadoCompra.REEMBOLSADA);
        when(compraRepository.findById(74L)).thenReturn(Optional.of(reembolsada));

        // Aviso tardío de Mercado Pago sobre una compra a la que ya se le devolvió la plata.
        assertThat(service.confirmarAprobado(74L, List.of(555L), ConfirmacionPago.WEBHOOK)).isFalse();

        assertThat(reembolsada.getEstado()).isEqualTo(EstadoCompra.REEMBOLSADA);
        verify(emailService, never()).enviarComprobanteCompra(74L);
    }

    @Test
    void confirmarAprobado_sobreUnaCanceladaQueSePagoDespues_siLaRevive() {
        Compra cancelada = checkoutPendiente(75L);
        cancelada.setEstado(EstadoCompra.CANCELADO);
        when(compraRepository.findById(75L)).thenReturn(Optional.of(cancelada));
        when(compraRepository.aprobarSiSigueSinPagar(75L, "555")).thenReturn(1);

        // Pagó, así que tiene su entrada: mejor un lugar de más.
        assertThat(service.confirmarAprobado(75L, List.of(555L), ConfirmacionPago.WEBHOOK)).isTrue();

        assertThat(cancelada.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(emailService).enviarComprobanteCompra(75L);
    }

    @Test
    void confirmarAprobado_alRevivirUnaCancelada_vuelveATomarElUsoDelCupon() {
        Compra cancelada = checkoutPendiente(77L);
        cancelada.setEstado(EstadoCompra.CANCELADO);
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("PROMO").activo(true).usosMaximos(1).usosActuales(0)
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        cancelada.setCupon(cupon);
        when(compraRepository.findById(77L)).thenReturn(Optional.of(cancelada));
        when(compraRepository.aprobarSiSigueSinPagar(77L, "555")).thenReturn(1);
        when(cuponService.consumirUso(9L)).thenReturn(true);

        assertThat(service.confirmarAprobado(77L, List.of(555L), ConfirmacionPago.WEBHOOK)).isTrue();

        // Hay que volver a tomar el uso, o el cupón se aplica dos veces.
        verify(cuponService).consumirUso(9L);
    }

    @Test
    void confirmarAprobado_alRevivirYNoQuedarUsosDelCupon_apruebaIgualPeroAvisa() {
        Compra cancelada = checkoutPendiente(78L);
        cancelada.setEstado(EstadoCompra.CANCELADO);
        var cupon = org.example.laserranitaentradas.model.entity.Cupon.builder()
                .id(9L).codigo("PROMO").activo(false).usosMaximos(1).usosActuales(1)
                .fechaExpiracion(LocalDate.now().plusYears(1)).build();
        cancelada.setCupon(cupon);
        when(compraRepository.findById(78L)).thenReturn(Optional.of(cancelada));
        when(compraRepository.aprobarSiSigueSinPagar(78L, "555")).thenReturn(1);
        when(cuponService.consumirUso(9L)).thenReturn(false); // otro se llevó el último uso

        // Se aprueba igual y el cupón sobreaplicado queda logueado.
        assertThat(service.confirmarAprobado(78L, List.of(555L), ConfirmacionPago.WEBHOOK)).isTrue();

        assertThat(cancelada.getEstado()).isEqualTo(EstadoCompra.APROBADO);
        verify(emailService).enviarComprobanteCompra(78L);
    }

    @Test
    void confirmarAprobado_dosVeces_noReenviaElComprobante() {
        Compra pendiente = checkoutPendiente(73L);
        when(compraRepository.findById(73L)).thenReturn(Optional.of(pendiente));
        when(compraRepository.aprobarSiSigueSinPagar(73L, "555")).thenReturn(1);

        assertThat(service.confirmarAprobado(73L, List.of(555L), ConfirmacionPago.WEBHOOK)).isTrue();
        // Segunda notificación de Mercado Pago para el mismo pago (las reintenta).
        assertThat(service.confirmarAprobado(73L, List.of(555L), ConfirmacionPago.WEBHOOK)).isFalse();

        verify(emailService, org.mockito.Mockito.times(1)).enviarComprobanteCompra(73L);
    }

    // ---------- Webhook: los pagos avisados tienen que pagar de verdad esta compra ----------

    private static final java.time.LocalDateTime CREACION_COMPRA = java.time.LocalDateTime.of(2026, 9, 24, 10, 0);

    private Compra compraDelWebhook() {
        Compra compra = checkoutPendiente(80L);
        compra.setFechaCreacion(CREACION_COMPRA);
        when(compraRepository.findByCodigoReserva("260101-1")).thenReturn(Optional.of(compra));
        return compra;
    }

    private com.mercadopago.resources.payment.Payment pagoAprobado(long id, String monto, java.time.LocalDateTime creado) {
        var pago = org.mockito.Mockito.mock(com.mercadopago.resources.payment.Payment.class);
        lenient().when(pago.getId()).thenReturn(id);
        lenient().when(pago.getStatus()).thenReturn("approved");
        lenient().when(pago.getExternalReference()).thenReturn("260101-1");
        lenient().when(pago.getTransactionAmount()).thenReturn(new java.math.BigDecimal(monto));
        lenient().when(pago.getDateCreated()).thenReturn(creado.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
        return pago;
    }

    /** El service con la búsqueda de pagos en Mercado Pago sustituida por lo que se le pase. */
    private CompraServiceImpl conPagosEnMercadoPago(com.mercadopago.resources.payment.Payment... pagos) throws Exception {
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn(List.of(pagos)).when(espia).buscarPagosEnMercadoPago(any());
        return espia;
    }

    private void aprobacionGana(Compra compra, String idsPagos) {
        when(compraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
        when(compraRepository.aprobarSiSigueSinPagar(compra.getId(), idsPagos)).thenReturn(1);
    }

    @Test
    void confirmarPagoMercadoPago_conUnPagoPorElTotal_apruebaYGuardaSuId() throws Exception {
        Compra compra = compraDelWebhook();
        aprobacionGana(compra, "999");

        assertThat(conPagosEnMercadoPago().confirmarPagoMercadoPago(
                pagoAprobado(999L, "34300", CREACION_COMPRA.plusMinutes(5)))).isTrue();

        assertThat(compra.getMpPaymentIds()).isEqualTo("999");
        verify(emailService).enviarComprobanteCompra(80L);
    }

    @Test
    void confirmarPagoMercadoPago_conDosTarjetasQueSumanElTotal_apruebaConLosDosPagos() throws Exception {
        // Dos tarjetas con el mismo external_reference; la avisada puede no aparecer todavía en la búsqueda.
        Compra compra = compraDelWebhook();
        aprobacionGana(compra, "998,999");
        var primeraTarjeta = pagoAprobado(998L, "20000", CREACION_COMPRA.plusMinutes(4));

        assertThat(conPagosEnMercadoPago(primeraTarjeta).confirmarPagoMercadoPago(
                pagoAprobado(999L, "14300", CREACION_COMPRA.plusMinutes(5)))).isTrue();

        // Los dos: el reembolso tiene que devolver cada tarjeta.
        assertThat(compra.pagosMercadoPago()).containsExactly(998L, 999L);
    }

    @Test
    void confirmarPagoMercadoPago_conUnaTarjetaAprobadaYLaOtraEnCurso_todaviaNoAprueba() throws Exception {
        compraDelWebhook();

        assertThat(conPagosEnMercadoPago().confirmarPagoMercadoPago(
                pagoAprobado(998L, "20000", CREACION_COMPRA.plusMinutes(4)))).isFalse();

        verify(compraRepository, never()).aprobarSiSigueSinPagar(anyLong(), any());
    }

    @Test
    void confirmarPagoMercadoPago_conUnPagoAnteriorALaCompra_noLaAprueba() throws Exception {
        // Un pago viejo con el mismo external_reference y monto no aprueba la compra nueva.
        compraDelWebhook();

        assertThat(conPagosEnMercadoPago().confirmarPagoMercadoPago(
                pagoAprobado(999L, "34300", CREACION_COMPRA.minusDays(30)))).isFalse();

        verify(compraRepository, never()).aprobarSiSigueSinPagar(anyLong(), any());
    }

    @Test
    void confirmarPagoMercadoPago_siLaBusquedaFalla_decideConElPagoAvisado() throws Exception {
        Compra compra = compraDelWebhook();
        aprobacionGana(compra, "999");
        CompraServiceImpl espia = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doThrow(new RuntimeException("Mercado Pago no responde"))
                .when(espia).buscarPagosEnMercadoPago(any());

        assertThat(espia.confirmarPagoMercadoPago(pagoAprobado(999L, "34300", CREACION_COMPRA.plusMinutes(5)))).isTrue();
    }

    @Test
    void confirmarPagoMercadoPago_otroPagoSobreUnaCompraYaPaga_noLaTocaDeNuevo() throws Exception {
        // Pagó dos veces: se avisa en el log para devolverlo, pero no se reaprueba ni se reenvía nada.
        Compra compra = compraDelWebhook();
        compra.setEstado(EstadoCompra.APROBADO);
        compra.setMpPaymentIds("998");

        assertThat(service.confirmarPagoMercadoPago(
                pagoAprobado(999L, "34300", CREACION_COMPRA.plusMinutes(9)))).isFalse();

        verify(compraRepository, never()).aprobarSiSigueSinPagar(anyLong(), any());
        verify(emailService, never()).enviarComprobanteCompra(anyLong());
    }
}
