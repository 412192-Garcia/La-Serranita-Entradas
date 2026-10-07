package org.example.laserranitaentradas.service.factura;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.dto.VentaFacturaDTO;
import org.example.laserranitaentradas.model.entity.*;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.FacturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentasFacturasServiceTest {

    private static final long BOLETERO_ID = 7L;
    private final UsuarioAutenticado boletero = new UsuarioAutenticado(BOLETERO_ID, "boletero1", RolUsuario.BOLETERO);
    private final UsuarioAutenticado otroBoletero = new UsuarioAutenticado(8L, "boletero2", RolUsuario.BOLETERO);
    private final UsuarioAutenticado admin = new UsuarioAutenticado(1L, "admin", RolUsuario.ADMIN);

    @Mock private CajaRepository cajaRepository;
    @Mock private CompraRepository compraRepository;
    @Mock private FacturaRepository facturaRepository;
    @Mock private FacturaService facturaService;
    @Mock private EmailService emailService;

    private VentasFacturasService service;
    private Caja caja;

    @BeforeEach
    void setUp() {
        service = new VentasFacturasService(cajaRepository, compraRepository, facturaRepository, facturaService, emailService);
        Usuario usuario = new Usuario();
        usuario.setId(BOLETERO_ID);
        caja = new Caja();
        caja.setId(3L);
        caja.setUsuario(usuario);
        when(cajaRepository.findById(3L)).thenReturn(Optional.of(caja));
        when(facturaService.obtenerPorCompra(any())).thenReturn(Optional.empty());
        when(facturaService.obtenerPorCompras(any())).thenReturn(Map.of());
    }

    // ---------- ventasDeCaja ----------

    @Test
    void ventasDeCaja_soloLasCobradas_deLaMasNuevaALaMasVieja_conResumenYFactura() {
        Compra vieja = compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.of(2026, 10, 2, 10, 0));
        Compra nueva = compra(11L, EstadoCompra.USADO, LocalDateTime.of(2026, 10, 2, 12, 0));
        Compra cancelada = compra(12L, EstadoCompra.CANCELADO, LocalDateTime.of(2026, 10, 2, 13, 0));
        TipoEntrada general = new TipoEntrada();
        general.setNombre("General");
        CompraDetalle detalle = new CompraDetalle();
        detalle.setTipoEntrada(general);
        detalle.setCantidad(2);
        nueva.setDetalles(List.of(detalle));
        when(compraRepository.findAllByCajaIdConDetalles(3L)).thenReturn(List.of(vieja, nueva, cancelada));
        FacturaResponseDTO factura = FacturaResponseDTO.builder().id(50L).estado(EstadoFactura.EMITIDA).build();
        when(facturaService.obtenerPorCompras(List.of(11L, 10L))).thenReturn(Map.of(11L, factura));

        List<VentaFacturaDTO> ventas = service.ventasDeCaja(3L, boletero);

        assertThat(ventas).extracting(VentaFacturaDTO::compraId).containsExactly(11L, 10L);
        assertThat(ventas.get(0).detalle()).isEqualTo("2x General");
        assertThat(ventas.get(0).factura()).isSameAs(factura);
        assertThat(ventas.get(1).factura()).isNull();
    }

    @Test
    void ventasDeCaja_deOtroBoletero_prohibido() {
        assertThatThrownBy(() -> service.ventasDeCaja(3L, otroBoletero)).isInstanceOf(ResponseStatusException.class);
        verify(compraRepository, never()).findAllByCajaIdConDetalles(any());
    }

    @Test
    void ventasDeCaja_elAdminVeCualquiera() {
        when(compraRepository.findAllByCajaIdConDetalles(3L)).thenReturn(List.of());

        assertThat(service.ventasDeCaja(3L, admin)).isEmpty();
    }

    // ---------- facturar ----------

    @Test
    void facturar_ventaSinFactura_laPideComoEnElPos() {
        Compra c = compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now());
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(c));
        Factura nueva = new Factura();
        nueva.setId(60L);
        FacturacionPosDTO pedido = pedido(DestinoFactura.IMPRIMIR, null);
        when(facturaService.solicitar(c, pedido)).thenReturn(Optional.of(nueva));
        FacturaResponseDTO dto = FacturaResponseDTO.builder().id(60L).estado(EstadoFactura.PENDIENTE).build();
        when(facturaService.obtenerPorCompra(10L)).thenReturn(Optional.empty(), Optional.of(dto));

        assertThat(service.facturar(10L, pedido, boletero)).isSameAs(dto);
        verify(facturaService).solicitar(c, pedido);
    }

    @Test
    void facturar_siYaTieneFactura_noDuplica() {
        Compra c = compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now());
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(c));
        when(facturaService.obtenerPorCompra(10L))
                .thenReturn(Optional.of(FacturaResponseDTO.builder().estado(EstadoFactura.ERROR).build()));

        assertThatThrownBy(() -> service.facturar(10L, pedido(DestinoFactura.IMPRIMIR, null), boletero))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ya tiene factura");
        verify(facturaService, never()).solicitar(any(), any());
    }

    @Test
    void facturar_siLaAnteriorSeAnulo_sePuedeVolverAFacturar() {
        Compra c = compra(10L, EstadoCompra.USADO, LocalDateTime.now());
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(c));
        FacturaResponseDTO anulada = FacturaResponseDTO.builder().estado(EstadoFactura.ANULADA).build();
        FacturaResponseDTO nueva = FacturaResponseDTO.builder().estado(EstadoFactura.PENDIENTE).build();
        when(facturaService.obtenerPorCompra(10L)).thenReturn(Optional.of(anulada), Optional.of(nueva));
        when(facturaService.solicitar(any(), any())).thenReturn(Optional.of(new Factura()));

        assertThat(service.facturar(10L, pedido(DestinoFactura.MAIL, "cliente@mail.com"), boletero)).isSameAs(nueva);
    }

    @Test
    void facturar_siLaAnteriorTieneNotaDeCredito_sePuedeVolverAFacturar() {
        Compra c = compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now());
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(c));
        FacturaResponseDTO conNc = FacturaResponseDTO.builder().estado(EstadoFactura.EMITIDA).anulacionPedida(true).build();
        FacturaResponseDTO nueva = FacturaResponseDTO.builder().estado(EstadoFactura.PENDIENTE).build();
        when(facturaService.obtenerPorCompra(10L)).thenReturn(Optional.of(conNc), Optional.of(nueva));
        when(facturaService.solicitar(any(), any())).thenReturn(Optional.of(new Factura()));

        assertThat(service.facturar(10L, pedido(DestinoFactura.MAIL, "cliente@mail.com"), admin)).isSameAs(nueva);
    }

    @Test
    void facturar_porMailSinEmail_error() {
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now())));

        assertThatThrownBy(() -> service.facturar(10L, pedido(DestinoFactura.MAIL, " "), boletero))
                .isInstanceOf(IllegalArgumentException.class);
        verify(facturaService, never()).solicitar(any(), any());
    }

    @Test
    void facturar_ventaCancelada_error() {
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(compra(10L, EstadoCompra.CANCELADO, LocalDateTime.now())));

        assertThatThrownBy(() -> service.facturar(10L, pedido(DestinoFactura.IMPRIMIR, null), boletero))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void facturar_ventaDeOtraCaja_prohibido() {
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now())));

        assertThatThrownBy(() -> service.facturar(10L, pedido(DestinoFactura.IMPRIMIR, null), otroBoletero))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void facturar_siNoSePudoCrear_avisa() {
        when(compraRepository.findByIdBloqueando(10L)).thenReturn(Optional.of(compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now())));
        when(facturaService.solicitar(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.facturar(10L, pedido(DestinoFactura.IMPRIMIR, null), boletero))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No se pudo facturar");
    }

    // ---------- enviarPorMail ----------

    @Test
    void enviarPorMail_guardaElEmailYManda() {
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura(EstadoFactura.EMITIDA, false)));

        service.enviarPorMail(50L, "  cliente@mail.com ", boletero);

        // El email va directo al envío; la factura no se toca antes (dos envíos no se cruzan).
        verify(emailService).enviarFacturaA(50L, "cliente@mail.com");
        verify(facturaRepository, never()).marcarMailEnviadoA(any(), any(), any());
    }

    @Test
    void enviarPorMail_emailInvalido_error() {
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura(EstadoFactura.EMITIDA, false)));

        assertThatThrownBy(() -> service.enviarPorMail(50L, "no-es-un-mail", boletero))
                .isInstanceOf(IllegalArgumentException.class);
        verify(emailService, never()).enviarFacturaA(any(), any());
    }

    @Test
    void enviarPorMail_facturaSinEmitir_error() {
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura(EstadoFactura.PENDIENTE, false)));

        assertThatThrownBy(() -> service.enviarPorMail(50L, "cliente@mail.com", boletero))
                .isInstanceOf(IllegalStateException.class);
        verify(emailService, never()).enviarFacturaA(any(), any());
    }

    @Test
    void enviarPorMail_facturaConAnulacionPedida_error() {
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura(EstadoFactura.EMITIDA, true)));

        assertThatThrownBy(() -> service.enviarPorMail(50L, "cliente@mail.com", boletero))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("anuló");
    }

    @Test
    void enviarPorMail_facturaDeOtraCaja_prohibido() {
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura(EstadoFactura.EMITIDA, false)));

        assertThatThrownBy(() -> service.enviarPorMail(50L, "cliente@mail.com", otroBoletero))
                .isInstanceOf(ResponseStatusException.class);
        verify(emailService, never()).enviarFacturaA(any(), any());
    }

    @Test
    void validarAccesoAFactura_deUnaCompraSinCaja_soloElAdmin() {
        Factura online = factura(EstadoFactura.EMITIDA, false);
        online.getCompra().setCaja(null);
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(online));

        assertThatThrownBy(() -> service.validarAccesoAFactura(50L, boletero)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.validarAccesoAFactura(50L, admin)).isSameAs(online);
    }

    @Test
    void validarAccesoAFactura_deSuCaja_elBoleteroPuede() {
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura(EstadoFactura.EMITIDA, false)));

        assertThat(service.validarAccesoAFactura(50L, boletero)).isNotNull();
        assertThatThrownBy(() -> service.validarAccesoAFactura(50L, otroBoletero)).isInstanceOf(ResponseStatusException.class);
    }

    // ---------- helpers ----------

    private Compra compra(Long id, EstadoCompra estado, LocalDateTime fecha) {
        Compra c = new Compra();
        c.setId(id);
        c.setEstado(estado);
        c.setFechaValidacion(fecha);
        c.setMontoTotal(new BigDecimal("2500"));
        c.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        c.setCaja(caja);
        return c;
    }

    private Factura factura(EstadoFactura estado, boolean anulacionPedida) {
        Factura f = new Factura();
        f.setId(50L);
        f.setEstado(estado);
        f.setAnulacionPedida(anulacionPedida);
        f.setCompra(compra(10L, EstadoCompra.VENDIDO_EN_PUERTA, LocalDateTime.now()));
        return f;
    }

    private static FacturacionPosDTO pedido(DestinoFactura destino, String email) {
        FacturacionPosDTO p = new FacturacionPosDTO();
        p.setDestino(destino);
        p.setEmail(email);
        return p;
    }
}
