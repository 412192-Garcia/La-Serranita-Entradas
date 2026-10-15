package org.example.laserranitaentradas.service.point;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.CobroPoint;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCobroPoint;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Modulo;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.CobroPointRepository;
import org.example.laserranitaentradas.repository.TerminalPointRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.ModulosService;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient.Orden;
import org.example.laserranitaentradas.service.point.MercadoPagoPointClient.PointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CobroPointServiceTest {

    @Mock private CobroPointRepository repository;
    @Mock private TerminalPointRepository terminalRepository;
    @Mock private MercadoPagoPointClient client;
    @Mock private ModulosService modulos;
    @Mock private CajaService cajaService;

    private CobroPointService service;
    private final UsuarioAutenticado boletero = new UsuarioAutenticado(7L, "boletero", RolUsuario.BOLETERO);
    private Caja caja;

    @BeforeEach
    void setUp() {
        service = new CobroPointService(repository, terminalRepository, client, modulos, cajaService);
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        caja = Caja.builder().id(3L).usuario(usuario).build();
        lenient().when(modulos.activo(Modulo.POINT)).thenReturn(true);
        lenient().when(repository.save(any(CobroPoint.class))).thenAnswer(inv -> {
            CobroPoint c = inv.getArgument(0);
            if (c.getId() == null) c.setId(50L);
            return c;
        });
    }

    private static Orden orden(String status, String pagoStatusDetail) {
        return new Orden("ORD1", status, status, "PAY1", status, pagoStatusDetail, "visa", "credit_card", 1, "POINT-50");
    }

    private CobroPoint cobro(EstadoCobroPoint estado, String monto) {
        return CobroPoint.builder().id(50L).claveIdempotencia("k").ordenId("ORD1").terminalId("T").monto(new BigDecimal(monto))
                .formaPago(FormaPago.TARJETA).estado(estado).caja(caja).build();
    }

    private Compra venta(FormaPago forma, String total, FormaPago secundaria, String montoSecundaria) {
        Compra c = new Compra();
        c.setId(900L);
        c.setCaja(caja);
        c.setCodigoReserva("P-1");
        c.setFormaPago(forma);
        c.setMontoTotal(new BigDecimal(total));
        c.setFormaPagoSecundaria(secundaria);
        c.setMontoFormaPagoSecundaria(montoSecundaria != null ? new BigDecimal(montoSecundaria) : null);
        return c;
    }

    // ---------- Crear ----------

    @Test
    void crear_mandaLaOrdenAlPointConLaReferenciaDelCobro() {
        when(repository.findByClaveIdempotencia("k")).thenReturn(Optional.empty());
        when(cajaService.getAbiertaOrThrow(7L)).thenReturn(caja);
        when(client.crearOrden(eq("T"), eq(new BigDecimal("15000")), eq("POINT-50"), eq("k"), eq(false))).thenReturn(orden("created", "created"));

        var dto = service.crear(new CobroPointService.CrearCobroRequest("T", new BigDecimal("15000"), "k", null), boletero);

        assertThat(dto.estado()).isEqualTo(EstadoCobroPoint.CREADO);
        assertThat(dto.ordenId()).isEqualTo("ORD1");
        assertThat(dto.enCurso()).isTrue();
    }

    @Test
    void crear_conLaMismaClave_devuelveElMismoCobroSinOtraOrden() {
        when(repository.findByClaveIdempotencia("k")).thenReturn(Optional.of(cobro(EstadoCobroPoint.EN_TERMINAL, "100")));

        var dto = service.crear(new CobroPointService.CrearCobroRequest("T", new BigDecimal("100"), "k", null), boletero);

        assertThat(dto.id()).isEqualTo(50L);
        verify(client, never()).crearOrden(anyString(), any(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void crear_siMercadoPagoFalla_quedaEnErrorConElMotivo() {
        when(repository.findByClaveIdempotencia("k")).thenReturn(Optional.empty());
        when(cajaService.getAbiertaOrThrow(7L)).thenReturn(caja);
        when(client.crearOrden(anyString(), any(), anyString(), anyString(), anyBoolean())).thenThrow(new PointException(404, "Terminal no encontrada"));

        var dto = service.crear(new CobroPointService.CrearCobroRequest("T", new BigDecimal("100"), "k", null), boletero);

        assertThat(dto.estado()).isEqualTo(EstadoCobroPoint.ERROR);
        assertThat(dto.motivo()).isEqualTo("Terminal no encontrada");
        assertThat(dto.enCurso()).isFalse();
    }

    @Test
    void crear_conElModuloApagado_rechaza() {
        when(modulos.activo(Modulo.POINT)).thenReturn(false);
        assertThatThrownBy(() -> service.crear(new CobroPointService.CrearCobroRequest("T", BigDecimal.TEN, "k", null), boletero))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Consultar / estados ----------

    @Test
    void consultar_traduceElRechazoDelBanco() {
        when(repository.findConCajaById(50L)).thenReturn(Optional.of(cobro(EstadoCobroPoint.EN_TERMINAL, "100")));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("failed", "rejected_by_issuer"));

        var dto = service.consultar(50L, boletero);

        assertThat(dto.estado()).isEqualTo(EstadoCobroPoint.RECHAZADO);
        assertThat(dto.motivo()).isEqualTo("El banco rechazó la tarjeta");
    }

    @Test
    void consultar_unCobroDeOtraCaja_noExisteParaUnBoletero() {
        Usuario otro = new Usuario();
        otro.setId(99L);
        CobroPoint ajeno = cobro(EstadoCobroPoint.APROBADO, "100");
        ajeno.setCaja(Caja.builder().id(4L).usuario(otro).build());
        when(repository.findConCajaById(50L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.consultar(50L, boletero)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void estadoDe_cubreLosEstadosDeLaOrden() {
        assertThat(CobroPointService.estadoDe("processed")).isEqualTo(EstadoCobroPoint.APROBADO);
        assertThat(CobroPointService.estadoDe("at_terminal")).isEqualTo(EstadoCobroPoint.EN_TERMINAL);
        assertThat(CobroPointService.estadoDe("action_required")).isEqualTo(EstadoCobroPoint.REVISAR_EN_POINT);
        assertThat(CobroPointService.estadoDe("expired")).isEqualTo(EstadoCobroPoint.EXPIRADO);
        assertThat(CobroPointService.estadoDe("algo_nuevo")).isNull();
    }

    @Test
    void ordenDesdeJson_leeElPagoYSuMedio() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"id":"ORD9","status":"processed","status_detail":"accredited",
                 "transactions":{"payments":[{"id":"PAY9","status":"processed","status_detail":"accredited",
                   "payment_method":{"id":"debvisa","type":"debit_card","installments":1}}]}}""");
        Orden o = MercadoPagoPointClient.orden(json);
        assertThat(o.id()).isEqualTo("ORD9");
        assertThat(o.pagoId()).isEqualTo("PAY9");
        assertThat(o.tipoMedio()).isEqualTo("debit_card");
        assertThat(o.cuotas()).isEqualTo(1);
    }

    // ---------- Vincular con la venta ----------

    @Test
    void vincular_ventaConTarjeta_ataElCobro() {
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(c));
        Compra v = venta(FormaPago.TARJETA, "15000", null, null);

        service.vincular(java.util.List.of(50L), v);

        assertThat(c.getCompra()).isSameAs(v);
    }

    @Test
    void vincular_pagoMixto_comparaContraLaParteEnTarjeta() {
        CobroPoint principal = cobro(EstadoCobroPoint.APROBADO, "6000");
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(principal));
        service.vincular(java.util.List.of(50L), venta(FormaPago.TARJETA, "10000", FormaPago.EFECTIVO_BOLETERIA, "4000"));
        assertThat(principal.getCompra()).isNotNull();

        CobroPoint secundaria = cobro(EstadoCobroPoint.APROBADO, "4000");
        secundaria.setId(51L);
        when(repository.findParaVincular(51L)).thenReturn(Optional.of(secundaria));
        service.vincular(java.util.List.of(51L), venta(FormaPago.EFECTIVO_BOLETERIA, "10000", FormaPago.TARJETA, "4000"));
        assertThat(secundaria.getCompra()).isNotNull();
    }

    @Test
    void vincular_montoDistinto_rechazaLaVenta() {
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(cobro(EstadoCobroPoint.APROBADO, "14000")));
        assertThatThrownBy(() -> service.vincular(java.util.List.of(50L), venta(FormaPago.TARJETA, "15000", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("14000");
    }

    @Test
    void vincular_cobroRechazado_rechazaLaVenta() {
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(cobro(EstadoCobroPoint.RECHAZADO, "15000")));
        assertThatThrownBy(() -> service.vincular(java.util.List.of(50L), venta(FormaPago.TARJETA, "15000", null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void vincular_cobroEnCursoAnotadoAMano_seAtaIgual() {
        // "Salió aprobado: anotar a mano": MP todavía no dio el resultado, pero el cobro queda de
        // esta venta y no aparece después como plata cobrada sin venta.
        CobroPoint c = cobro(EstadoCobroPoint.REVISAR_EN_POINT, "15000");
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(c));
        Compra v = venta(FormaPago.TARJETA, "15000", null, null);

        service.vincular(java.util.List.of(50L), v);

        assertThat(c.getCompra()).isSameAs(v);
    }

    @Test
    void vincular_cobroDeOtraCaja_rechaza() {
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        c.setCaja(Caja.builder().id(4L).usuario(new Usuario()).build());
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.vincular(java.util.List.of(50L), venta(FormaPago.TARJETA, "15000", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otra caja");
    }

    @Test
    void vincular_cobroDeOtraVenta_rechaza_peroLaMismaVentaEsUnReintento() {
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        Compra v = venta(FormaPago.TARJETA, "15000", null, null);
        c.setCompra(v);
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(c));

        service.vincular(java.util.List.of(50L), v);

        Compra otra = venta(FormaPago.TARJETA, "15000", null, null);
        otra.setId(901L);
        assertThatThrownBy(() -> service.vincular(java.util.List.of(50L), otra)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void vincular_ventaSinTarjeta_rechaza() {
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(cobro(EstadoCobroPoint.APROBADO, "15000")));
        assertThatThrownBy(() -> service.vincular(java.util.List.of(50L), venta(FormaPago.EFECTIVO_BOLETERIA, "15000", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Devolver ----------

    @Test
    void devolver_unCobroYaVinculado_noSeDevuelveDesdeAca() {
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        c.setCompra(venta(FormaPago.TARJETA, "15000", null, null));
        when(repository.findConCajaById(50L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.devolver(50L, boletero)).isInstanceOf(IllegalStateException.class);
        verify(client, never()).devolverOrden(anyString(), anyString());
    }

    @Test
    void devolver_cobroSinVenta_quedaDevuelto() {
        when(repository.findConCajaById(50L)).thenReturn(Optional.of(cobro(EstadoCobroPoint.APROBADO, "15000")));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("processed", "accredited"));
        when(client.devolverOrden("ORD1", "devolver-k")).thenReturn(orden("refunded", "refunded"));

        assertThat(service.devolver(50L, boletero).estado()).isEqualTo(EstadoCobroPoint.DEVUELTO);
        // Asentado en su propia transacción, sin pasar por el save de la entidad (que podría pisar la venta).
        verify(repository).asentarDevolucion(eq(50L), eq(EstadoCobroPoint.DEVUELTO), any(), any());
        verify(repository, never()).save(any(CobroPoint.class));
    }

    @Test
    void devolver_ordenYaDevueltaDesdeElPoint_noPideOtraDevolucion() {
        when(repository.findConCajaById(50L)).thenReturn(Optional.of(cobro(EstadoCobroPoint.APROBADO, "15000")));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("refunded", "refunded"));

        assertThat(service.devolver(50L, boletero).estado()).isEqualTo(EstadoCobroPoint.DEVUELTO);
        verify(client, never()).devolverOrden(anyString(), anyString());
    }

    // ---------- Orden sin confirmar (MP no respondió al crearla) ----------

    @Test
    void crear_siMercadoPagoNoResponde_quedaEnCursoParaVolverAPedirla() {
        when(repository.findByClaveIdempotencia("k")).thenReturn(Optional.empty());
        when(cajaService.getAbiertaOrThrow(7L)).thenReturn(caja);
        when(client.crearOrden(anyString(), any(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new PointException(504, "No se pudo contactar a Mercado Pago: timeout"));

        var dto = service.crear(new CobroPointService.CrearCobroRequest("T", new BigDecimal("100"), "k", null), boletero);

        // No es ERROR: la orden puede estar igual en el Point esperando la tarjeta.
        assertThat(dto.estado()).isEqualTo(EstadoCobroPoint.CREADO);
        assertThat(dto.enCurso()).isTrue();
        assertThat(dto.ordenId()).isNull();
    }

    @Test
    void consultar_sinOrdenConfirmada_laVuelveAPedirConLaMismaClave() {
        CobroPoint c = cobro(EstadoCobroPoint.CREADO, "100");
        c.setOrdenId(null);
        c.setDetalle(CobroPointService.DETALLE_SIN_CONFIRMAR);
        when(repository.findConCajaById(50L)).thenReturn(Optional.of(c));
        when(client.crearOrden(eq("T"), any(), eq("POINT-50"), eq("k"), eq(false))).thenReturn(orden("at_terminal", "at_terminal"));

        var dto = service.consultar(50L, boletero);

        assertThat(dto.ordenId()).isEqualTo("ORD1");
        assertThat(dto.estado()).isEqualTo(EstadoCobroPoint.EN_TERMINAL);
    }

    @Test
    void aviso_deUnaOrdenNoConfirmada_laEncuentraPorLaReferencia() {
        CobroPoint c = cobro(EstadoCobroPoint.CREADO, "100");
        c.setOrdenId(null);
        when(repository.findByOrdenId("ORD1")).thenReturn(Optional.empty());
        when(client.obtenerOrden("ORD1")).thenReturn(orden("processed", "accredited"));
        when(repository.findByIdAndOrdenIdIsNull(50L)).thenReturn(Optional.of(c));

        service.actualizarDesdeMercadoPago("ORD1");

        assertThat(c.getOrdenId()).isEqualTo("ORD1");
        assertThat(c.getEstado()).isEqualTo(EstadoCobroPoint.APROBADO);
    }

    @Test
    void aviso_siOtroGuardoElCobroMientrasTanto_noLoPisa() {
        CobroPoint c = cobro(EstadoCobroPoint.EN_TERMINAL, "100");
        when(repository.findByOrdenId("ORD1")).thenReturn(Optional.of(c));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("processed", "accredited"));
        when(repository.save(any(CobroPoint.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(CobroPoint.class, 50L));

        service.actualizarDesdeMercadoPago("ORD1"); // no explota: gana lo ya guardado (la venta vinculada)
    }

    // ---------- QR ----------

    @Test
    void crear_qr_leDiceAlPointQueMuestreElQr() {
        when(repository.findByClaveIdempotencia("k")).thenReturn(Optional.empty());
        when(cajaService.getAbiertaOrThrow(7L)).thenReturn(caja);
        when(client.crearOrden(eq("T"), any(), anyString(), eq("k"), eq(true))).thenReturn(orden("created", "created"));

        var dto = service.crear(new CobroPointService.CrearCobroRequest("T", new BigDecimal("5000"), "k", FormaPago.MERCADO_PAGO_QR), boletero);

        assertThat(dto.formaPago()).isEqualTo(FormaPago.MERCADO_PAGO_QR);
    }

    @Test
    void crear_conEfectivo_rechaza() {
        when(repository.findByClaveIdempotencia("k")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(
                new CobroPointService.CrearCobroRequest("T", BigDecimal.TEN, "k", FormaPago.EFECTIVO_BOLETERIA), boletero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vincular_pagoMixtoTarjetaYQr_cadaCobroContraSuParte() {
        CobroPoint tarjeta = cobro(EstadoCobroPoint.APROBADO, "6000");
        CobroPoint qr = cobro(EstadoCobroPoint.APROBADO, "4000");
        qr.setId(51L);
        qr.setFormaPago(FormaPago.MERCADO_PAGO_QR);
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(tarjeta));
        when(repository.findParaVincular(51L)).thenReturn(Optional.of(qr));
        Compra v = venta(FormaPago.TARJETA, "10000", FormaPago.MERCADO_PAGO_QR, "4000");

        service.vincular(java.util.List.of(50L, 51L), v);

        assertThat(tarjeta.getCompra()).isSameAs(v);
        assertThat(qr.getCompra()).isSameAs(v);
    }

    @Test
    void vincular_qrConMontoDeLaTarjeta_rechaza() {
        CobroPoint qr = cobro(EstadoCobroPoint.APROBADO, "6000");
        qr.setFormaPago(FormaPago.MERCADO_PAGO_QR);
        when(repository.findParaVincular(50L)).thenReturn(Optional.of(qr));

        assertThatThrownBy(() -> service.vincular(java.util.List.of(50L), venta(FormaPago.TARJETA, "10000", FormaPago.MERCADO_PAGO_QR, "4000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QR");
    }

    // ---------- Cancelar / editar la venta ----------

    @Test
    void devolverDeVenta_devuelveLosCobrosAprobados() {
        Compra v = venta(FormaPago.TARJETA, "15000", null, null);
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        c.setCompra(v);
        when(repository.findAllByCompraId(900L)).thenReturn(java.util.List.of(c));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("processed", "accredited"));
        when(client.devolverOrden("ORD1", "devolver-k")).thenReturn(orden("refunded", "refunded"));

        service.devolverDeVenta(v);

        verify(repository).asentarDevolucion(eq(50L), eq(EstadoCobroPoint.DEVUELTO), any(), any());
    }

    @Test
    void devolverDeVenta_siMercadoPagoNoAcepta_noSeCancela() {
        Compra v = venta(FormaPago.TARJETA, "15000", null, null);
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        when(repository.findAllByCompraId(900L)).thenReturn(java.util.List.of(c));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("processed", "accredited"));
        when(client.devolverOrden(anyString(), anyString())).thenThrow(new PointException(400, "refund_not_allowed"));

        assertThatThrownBy(() -> service.devolverDeVenta(v)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devolverDeVenta_siMercadoPagoNoLaDaPorDevuelta_noSeCancela() {
        // Tarjetas que sólo se devuelven desde el propio Point: MP toma el pedido pero la orden sigue processed.
        Compra v = venta(FormaPago.TARJETA, "15000", null, null);
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        when(repository.findAllByCompraId(900L)).thenReturn(java.util.List.of(c));
        when(client.obtenerOrden("ORD1")).thenReturn(orden("processed", "accredited"));
        when(client.devolverOrden("ORD1", "devolver-k")).thenReturn(orden("processed", "refund_pending"));

        assertThatThrownBy(() -> service.devolverDeVenta(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propio Point");
    }

    @Test
    void validarEdicion_cambiaLaParteCobradaPorElPoint_rechaza() {
        CobroPoint c = cobro(EstadoCobroPoint.APROBADO, "15000");
        when(repository.findAllByCompraId(900L)).thenReturn(java.util.List.of(c));

        assertThatThrownBy(() -> service.validarEdicion(venta(FormaPago.TARJETA, "20000", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelala");
        // Mismo monto en tarjeta (por ejemplo, cambió sólo un artículo sin cambiar el total): pasa.
        service.validarEdicion(venta(FormaPago.TARJETA, "15000", null, null));
    }

    @Test
    void validarEdicion_sinCobrosDelPoint_noHaceNada() {
        when(repository.findAllByCompraId(900L)).thenReturn(java.util.List.of());
        service.validarEdicion(venta(FormaPago.EFECTIVO_BOLETERIA, "20000", null, null));
    }

    // ---------- Cierre ----------

    @Test
    void resumenCaja_sumaPorFormaYListaLosCobrosSinVenta() {
        java.util.List<Object[]> filas = java.util.List.of(
                new Object[]{FormaPago.TARJETA, new BigDecimal("30000"), 2L},
                new Object[]{FormaPago.MERCADO_PAGO_QR, new BigDecimal("5000"), 1L});
        when(repository.totalesAprobadosPorForma(3L)).thenReturn(filas);
        when(repository.aprobadosSinVenta(3L)).thenReturn(java.util.List.of(cobro(EstadoCobroPoint.APROBADO, "8000")));

        var r = service.resumenCaja(3L);

        assertThat(r.tarjeta()).isEqualByComparingTo("30000");
        assertThat(r.qr()).isEqualByComparingTo("5000");
        assertThat(r.cantidad()).isEqualTo(3);
        assertThat(r.sinVenta()).hasSize(1);
    }
}
