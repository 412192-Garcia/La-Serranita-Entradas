package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.EditarVentaRequestDTO;
import org.example.laserranitaentradas.model.entity.*;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.CorreccionVentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorreccionVentaServiceTest {

    private final UsuarioAutenticado boletero = new UsuarioAutenticado(7L, "marta", RolUsuario.BOLETERO);
    private final UsuarioAutenticado otroBoletero = new UsuarioAutenticado(8L, "juan", RolUsuario.BOLETERO);
    private final UsuarioAutenticado admin = new UsuarioAutenticado(1L, "admin", RolUsuario.ADMIN);

    @Mock private CompraService compraService;
    @Mock private CompraRepository compraRepository;
    @Mock private CorreccionVentaRepository correccionRepository;

    private CorreccionVentaService service;
    private Caja caja;
    private Compra venta;

    @BeforeEach
    void setUp() {
        service = new CorreccionVentaService(compraService, compraRepository, correccionRepository);
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        caja = new Caja();
        caja.setId(3L);
        caja.setUsuario(usuario);
        venta = new Compra();
        venta.setId(10L);
        venta.setCaja(caja);
        venta.setEstado(EstadoCompra.VENDIDO_EN_PUERTA);
        venta.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        venta.setMontoTotal(new BigDecimal("68600"));
        venta.setFechaValidacion(LocalDateTime.now().minusMinutes(5));
        TipoEntrada general = new TipoEntrada();
        general.setNombre("Pase General");
        CompraDetalle detalle = new CompraDetalle();
        detalle.setTipoEntrada(general);
        detalle.setCantidad(2);
        venta.setDetalles(new ArrayList<>(List.of(detalle)));
        when(compraRepository.findById(10L)).thenReturn(Optional.of(venta));
    }

    @Test
    void boletero_editaSuVentaReciente_yQuedaRegistrada() {
        EditarVentaRequestDTO request = new EditarVentaRequestDTO();
        when(compraService.editarVenta(10L, request)).thenAnswer(inv -> {
            venta.setFormaPago(FormaPago.TARJETA);
            return venta;
        });

        service.editar(10L, request, boletero);

        ArgumentCaptor<CorreccionVenta> captor = ArgumentCaptor.forClass(CorreccionVenta.class);
        verify(correccionRepository).save(captor.capture());
        CorreccionVenta c = captor.getValue();
        assertThat(c.getTipo()).isEqualTo(CorreccionVenta.Tipo.EDICION);
        assertThat(c.getUsuario()).isEqualTo("marta");
        assertThat(c.getCaja()).isSameAs(caja);
        assertThat(c.getAntes()).startsWith("Efectivo · 2× Pase General · $");
        assertThat(c.getDespues()).startsWith("Tarjeta · 2× Pase General · $");
    }

    @Test
    void boletero_cancelaConMotivo_yQuedaRegistrada() {
        when(compraService.cancelarVenta(10L, "Se cargó dos veces")).thenReturn(venta);

        service.cancelar(10L, "Se cargó dos veces", boletero);

        ArgumentCaptor<CorreccionVenta> captor = ArgumentCaptor.forClass(CorreccionVenta.class);
        verify(correccionRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(CorreccionVenta.Tipo.CANCELACION);
        assertThat(captor.getValue().getMotivo()).isEqualTo("Se cargó dos veces");
        assertThat(captor.getValue().getDespues()).isNull();
    }

    @Test
    void boletero_cancelarSinMotivo_rechazado() {
        assertThatThrownBy(() -> service.cancelar(10L, "  ", boletero)).isInstanceOf(IllegalArgumentException.class);
        verify(compraService, never()).cancelarVenta(any(), any());
    }

    @Test
    void boletero_ventaDeOtraCaja_prohibido() {
        assertThatThrownBy(() -> service.editar(10L, new EditarVentaRequestDTO(), otroBoletero))
                .isInstanceOf(ResponseStatusException.class);
        verify(compraService, never()).editarVenta(any(), any());
    }

    @Test
    void boletero_pasados15Minutos_rechazado() {
        venta.setFechaValidacion(LocalDateTime.now().minusMinutes(16));

        assertThatThrownBy(() -> service.editar(10L, new EditarVentaRequestDTO(), boletero))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("15 minutos");
        verify(compraService, never()).editarVenta(any(), any());
    }

    @Test
    void boletero_cajaCerrada_rechazado() {
        caja.setFechaCierre(LocalDateTime.now());

        assertThatThrownBy(() -> service.cancelar(10L, "error", boletero))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cerró");
    }

    @Test
    void boletero_anticipada_rechazado() {
        venta.setEstado(EstadoCompra.USADO);

        assertThatThrownBy(() -> service.editar(10L, new EditarVentaRequestDTO(), boletero))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Anticipadas");
    }

    @Test
    void admin_sinLimites_niRegistro() {
        venta.setFechaValidacion(LocalDateTime.now().minusDays(2));
        caja.setFechaCierre(LocalDateTime.now());

        service.cancelar(10L, null, admin);

        verify(compraService).cancelarVenta(eq(10L), any());
        verify(correccionRepository, never()).save(any());
    }

    @Test
    void corregibleHasta_soloParaElBoleteroDentroDeLaVentana() {
        LocalDateTime ahora = LocalDateTime.now();
        assertThat(CorreccionVentaService.corregibleHasta(venta, boletero, ahora))
                .isEqualTo(venta.getFechaValidacion().plusMinutes(15));
        assertThat(CorreccionVentaService.corregibleHasta(venta, otroBoletero, ahora)).isNull();
        assertThat(CorreccionVentaService.corregibleHasta(venta, admin, ahora)).isNull();
        assertThat(CorreccionVentaService.corregibleHasta(venta, boletero, ahora.plusMinutes(11))).isNull();
    }
}
