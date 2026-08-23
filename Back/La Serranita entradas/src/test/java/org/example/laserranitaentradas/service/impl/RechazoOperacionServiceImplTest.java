package org.example.laserranitaentradas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.dto.OperacionRechazadaResponseDTO;
import org.example.laserranitaentradas.model.dto.VentaPosRequestDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.OperacionRechazada;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoEntradas;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.OperacionRechazadaRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.CompraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Registro de operaciones del POS rechazadas por el servidor, para que un admin las revise desde
 * Cajas sin depender de mirar el navegador puntual del boletero que las hizo. reabrirYReintentar
 * reabre la caja de origen y vuelve a mandar cada operación por el camino REAL de siempre
 * (registrarRetiro/registrarIngresoEntradas/registrarVentaPos) — así cada una se revalida en
 * vivo en vez de forzarse a ciegas.
 */
@ExtendWith(MockitoExtension.class)
class RechazoOperacionServiceImplTest {

    @Mock private OperacionRechazadaRepository repository;
    @Mock private CajaService cajaService;
    @Mock private CompraService compraService;

    private RechazoOperacionServiceImpl service;

    private static final Long USUARIO_ID = 99L;

    @BeforeEach
    void setUp() {
        service = new RechazoOperacionServiceImpl(repository, new ObjectMapper(), cajaService, compraService);
    }

    private Caja cajaReabiertaDeUsuario(Long usuarioId) {
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        Caja caja = new Caja();
        caja.setUsuario(usuario);
        return caja;
    }

    @Test
    void registrar_guardaElPayloadComoJson() {
        service.registrar("INGRESO_ENTRADAS", Map.of("cantidad", 5, "tipo", "RETIRO"), "No tenés esa cantidad", "clave-1");

        ArgumentCaptor<OperacionRechazada> captor = ArgumentCaptor.forClass(OperacionRechazada.class);
        verify(repository).save(captor.capture());
        OperacionRechazada guardado = captor.getValue();
        assertThat(guardado.getTipoOperacion()).isEqualTo("INGRESO_ENTRADAS");
        assertThat(guardado.getMotivo()).isEqualTo("No tenés esa cantidad");
        assertThat(guardado.getIdempotencyKey()).isEqualTo("clave-1");
        assertThat(guardado.isResuelto()).isFalse();
        assertThat(guardado.getPayload()).contains("\"cantidad\":5").contains("\"tipo\":\"RETIRO\"");
    }

    @Test
    void resolver_marcaResueltoYGuardaLaNota() {
        OperacionRechazada existente = OperacionRechazada.builder()
                .id(9L)
                .tipoOperacion("VENTA")
                .payload("{}")
                .motivo("Caja ya cerrada")
                .resuelto(false)
                .build();
        when(repository.findById(9L)).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OperacionRechazadaResponseDTO dto = service.resolver(9L, "Le avisé al boletero");

        assertThat(dto.isResuelto()).isTrue();
        assertThat(dto.getNotaResolucion()).isEqualTo("Le avisé al boletero");
    }

    @Test
    void resolver_conIdInexistente_rechaza() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(404L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listar_conFiltroResuelto_usaElRepositorioCorrecto() {
        when(repository.findAllByResueltoOrderByFechaCreacionDesc(false)).thenReturn(List.of());

        List<OperacionRechazadaResponseDTO> resultado = service.listar(false);

        assertThat(resultado).isEmpty();
        verify(repository).findAllByResueltoOrderByFechaCreacionDesc(false);
    }

    @Test
    void listar_sinFiltro_traeTodas() {
        when(repository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());

        service.listar(null);

        verify(repository).findAllByOrderByFechaCreacionDesc();
    }

    @Test
    void reabrirYReintentar_retiroAporte_reabreYVuelveAMandarloPorElCaminoReal() {
        OperacionRechazada rechazo = OperacionRechazada.builder()
                .id(1L)
                .tipoOperacion("RETIRO_APORTE")
                .payload("{\"monto\":500,\"motivo\":\"Vuelto\",\"tipo\":\"RETIRO\",\"cajaId\":7,\"idempotencyKey\":\"clave-1\"}")
                .motivo("No hay una caja abierta")
                .resuelto(false)
                .build();
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(rechazo));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cajaService.reabrir(7L)).thenReturn(cajaReabiertaDeUsuario(USUARIO_ID));

        OperacionRechazadaResponseDTO dto = service.reabrirYReintentar(1L);

        verify(cajaService).registrarRetiro(USUARIO_ID, new BigDecimal("500"), "Vuelto", TipoMovimientoCaja.RETIRO, "clave-1", null);
        verify(cajaService).recerrarConElUltimoConteo(7L);
        assertThat(dto.isResuelto()).isTrue();
        assertThat(dto.getNotaResolucion()).isEqualTo("Reabierto y reintentado automáticamente");
    }

    @Test
    void reabrirYReintentar_ingresoEntradas_reabreYVuelveAMandarloPorElCaminoReal() {
        OperacionRechazada rechazo = OperacionRechazada.builder()
                .id(2L)
                .tipoOperacion("INGRESO_ENTRADAS")
                .payload("{\"cantidad\":10,\"tipo\":\"INGRESO\",\"cajaId\":8}")
                .motivo("No hay una caja abierta")
                .resuelto(false)
                .build();
        when(repository.findAllById(List.of(2L))).thenReturn(List.of(rechazo));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cajaService.reabrir(8L)).thenReturn(cajaReabiertaDeUsuario(USUARIO_ID));

        service.reabrirYReintentar(2L);

        verify(cajaService).registrarIngresoEntradas(USUARIO_ID, 10, null, TipoMovimientoEntradas.INGRESO, null, null);
        verify(cajaService).recerrarConElUltimoConteo(8L);
    }

    @Test
    void reabrirYReintentar_venta_reabreYVuelveAMandarlaPorElCaminoReal() {
        OperacionRechazada rechazo = OperacionRechazada.builder()
                .id(6L)
                .tipoOperacion("VENTA")
                .payload("{\"formaPago\":\"EFECTIVO_BOLETERIA\",\"cajaId\":7,\"idempotencyKey\":\"clave-venta\"}")
                .motivo("No hay una caja abierta")
                .resuelto(false)
                .build();
        when(repository.findAllById(List.of(6L))).thenReturn(List.of(rechazo));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cajaService.reabrir(7L)).thenReturn(cajaReabiertaDeUsuario(USUARIO_ID));

        service.reabrirYReintentar(6L);

        ArgumentCaptor<VentaPosRequestDTO> captor = ArgumentCaptor.forClass(VentaPosRequestDTO.class);
        verify(compraService).registrarVentaPos(captor.capture(), org.mockito.ArgumentMatchers.eq(USUARIO_ID));
        assertThat(captor.getValue().getFormaPago()).isEqualTo(FormaPago.EFECTIVO_BOLETERIA);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("clave-venta");
        verify(cajaService).recerrarConElUltimoConteo(7L);
    }

    @Test
    void reabrirYReintentar_sinCajaIdEnElPayload_rechazaYNoTocaNada() {
        OperacionRechazada rechazo = OperacionRechazada.builder()
                .id(3L)
                .tipoOperacion("RETIRO_APORTE")
                .payload("{\"monto\":500,\"motivo\":\"Vuelto\",\"tipo\":\"RETIRO\"}")
                .motivo("No hay una caja abierta")
                .resuelto(false)
                .build();
        when(repository.findAllById(List.of(3L))).thenReturn(List.of(rechazo));

        assertThatThrownBy(() -> service.reabrirYReintentar(3L))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(cajaService);
        verifyNoInteractions(compraService);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void reabrirYReintentar_yaResuelto_rechaza() {
        OperacionRechazada rechazo = OperacionRechazada.builder()
                .id(4L)
                .tipoOperacion("RETIRO_APORTE")
                .payload("{}")
                .motivo("x")
                .resuelto(true)
                .build();
        when(repository.findAllById(List.of(4L))).thenReturn(List.of(rechazo));

        assertThatThrownBy(() -> service.reabrirYReintentar(4L))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(cajaService);
    }

    @Test
    void reabrirYReintentar_tipoNoSoportado_rechaza() {
        OperacionRechazada rechazo = OperacionRechazada.builder()
                .id(5L)
                .tipoOperacion("COMPROBANTE_EMAIL")
                .payload("{}")
                .motivo("x")
                .resuelto(false)
                .build();
        when(repository.findAllById(List.of(5L))).thenReturn(List.of(rechazo));

        assertThatThrownBy(() -> service.reabrirYReintentar(5L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cajaService);
        verifyNoInteractions(compraService);
    }

    @Test
    void reabrirYReintentarLote_variosDeLaMismaCaja_reabreUnaSolaVezYMandaCadaUnoPorSuCamino() {
        OperacionRechazada r1 = OperacionRechazada.builder()
                .id(10L).tipoOperacion("RETIRO_APORTE")
                .payload("{\"monto\":500,\"motivo\":\"Vuelto\",\"tipo\":\"RETIRO\",\"cajaId\":7}")
                .motivo("No hay una caja abierta").resuelto(false).build();
        r1.setFechaCreacion(LocalDateTime.of(2026, 8, 20, 10, 0));
        OperacionRechazada r2 = OperacionRechazada.builder()
                .id(11L).tipoOperacion("INGRESO_ENTRADAS")
                .payload("{\"cantidad\":10,\"tipo\":\"INGRESO\",\"cajaId\":7}")
                .motivo("No hay una caja abierta").resuelto(false).build();
        r2.setFechaCreacion(LocalDateTime.of(2026, 8, 20, 10, 1));
        when(repository.findAllById(List.of(10L, 11L))).thenReturn(List.of(r1, r2));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cajaService.reabrir(7L)).thenReturn(cajaReabiertaDeUsuario(USUARIO_ID));

        List<OperacionRechazadaResponseDTO> resultado = service.reabrirYReintentarLote(List.of(10L, 11L));

        verify(cajaService).reabrir(7L);
        verify(cajaService).registrarRetiro(USUARIO_ID, new BigDecimal("500"), "Vuelto", TipoMovimientoCaja.RETIRO, null, null);
        verify(cajaService).registrarIngresoEntradas(USUARIO_ID, 10, null, TipoMovimientoEntradas.INGRESO, null, null);
        verify(cajaService).recerrarConElUltimoConteo(7L);
        assertThat(resultado).hasSize(2);
        assertThat(resultado).allSatisfy(dto -> assertThat(dto.isResuelto()).isTrue());
        assertThat(resultado.get(0).getNotaResolucion()).contains("junto con otras 1");
    }

    @Test
    void reabrirYReintentarLote_mezclaVentaConRetiroDeLaMismaCaja_losResuelveJuntos() {
        OperacionRechazada venta = OperacionRechazada.builder()
                .id(20L).tipoOperacion("VENTA")
                .payload("{\"formaPago\":\"EFECTIVO_BOLETERIA\",\"cajaId\":7}")
                .motivo("No hay una caja abierta").resuelto(false).build();
        venta.setFechaCreacion(LocalDateTime.of(2026, 8, 20, 10, 0));
        OperacionRechazada retiro = OperacionRechazada.builder()
                .id(21L).tipoOperacion("RETIRO_APORTE")
                .payload("{\"monto\":500,\"motivo\":\"Vuelto\",\"tipo\":\"RETIRO\",\"cajaId\":7}")
                .motivo("No hay una caja abierta").resuelto(false).build();
        retiro.setFechaCreacion(LocalDateTime.of(2026, 8, 20, 10, 1));
        when(repository.findAllById(List.of(20L, 21L))).thenReturn(List.of(venta, retiro));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cajaService.reabrir(7L)).thenReturn(cajaReabiertaDeUsuario(USUARIO_ID));

        service.reabrirYReintentarLote(List.of(20L, 21L));

        verify(cajaService, org.mockito.Mockito.times(1)).reabrir(7L);
        verify(compraService).registrarVentaPos(any(VentaPosRequestDTO.class), org.mockito.ArgumentMatchers.eq(USUARIO_ID));
        verify(cajaService).registrarRetiro(USUARIO_ID, new BigDecimal("500"), "Vuelto", TipoMovimientoCaja.RETIRO, null, null);
        verify(cajaService, org.mockito.Mockito.times(1)).recerrarConElUltimoConteo(7L);
    }

    @Test
    void reabrirYReintentarLote_deCajasDistintas_rechaza() {
        OperacionRechazada r1 = OperacionRechazada.builder()
                .id(10L).tipoOperacion("RETIRO_APORTE")
                .payload("{\"monto\":500,\"motivo\":\"Vuelto\",\"tipo\":\"RETIRO\",\"cajaId\":7}")
                .motivo("x").resuelto(false).build();
        r1.setFechaCreacion(LocalDateTime.now());
        OperacionRechazada r2 = OperacionRechazada.builder()
                .id(11L).tipoOperacion("RETIRO_APORTE")
                .payload("{\"monto\":100,\"motivo\":\"Otra\",\"tipo\":\"RETIRO\",\"cajaId\":8}")
                .motivo("x").resuelto(false).build();
        r2.setFechaCreacion(LocalDateTime.now().plusMinutes(1));
        when(repository.findAllById(List.of(10L, 11L))).thenReturn(List.of(r1, r2));

        assertThatThrownBy(() -> service.reabrirYReintentarLote(List.of(10L, 11L)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cajaService);
        verifyNoInteractions(compraService);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void reabrirYReintentarLote_conAlgunoYaResuelto_rechaza() {
        OperacionRechazada r1 = OperacionRechazada.builder()
                .id(10L).tipoOperacion("RETIRO_APORTE").payload("{}").motivo("x").resuelto(false).build();
        OperacionRechazada r2 = OperacionRechazada.builder()
                .id(11L).tipoOperacion("RETIRO_APORTE").payload("{}").motivo("x").resuelto(true).build();
        when(repository.findAllById(List.of(10L, 11L))).thenReturn(List.of(r1, r2));

        assertThatThrownBy(() -> service.reabrirYReintentarLote(List.of(10L, 11L)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(cajaService);
    }

    @Test
    void reabrirYReintentarLote_conIdInexistente_rechaza() {
        when(repository.findAllById(List.of(10L, 999L))).thenReturn(List.of(
                OperacionRechazada.builder().id(10L).tipoOperacion("RETIRO_APORTE").payload("{}").motivo("x").resuelto(false).build()
        ));

        assertThatThrownBy(() -> service.reabrirYReintentarLote(List.of(10L, 999L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reabrirYReintentarLote_listaVacia_rechaza() {
        assertThatThrownBy(() -> service.reabrirYReintentarLote(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
