package org.example.laserranitaentradas.service.impresion;

import org.example.laserranitaentradas.model.entity.EstadoTrabajoImpresion;
import org.example.laserranitaentradas.model.entity.Factura;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.repository.TrabajoImpresionRepository;
import org.example.laserranitaentradas.service.factura.ComprobanteFacturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImpresionServiceTest {

    @Mock private TrabajoImpresionRepository trabajoRepository;
    @Mock private FacturaRepository facturaRepository;
    @Mock private ComprobanteFacturaService comprobanteService;
    @Mock private TicketEscPosGenerator escPos;
    @Mock private PlatformTransactionManager transactionManager;

    private ImpresionService service;
    private TrabajoImpresion guardado;

    @BeforeEach
    void setUp() {
        service = new ImpresionService(trabajoRepository, facturaRepository, comprobanteService, escPos, transactionManager);
        when(facturaRepository.findById(1L)).thenReturn(Optional.of(Factura.builder().id(1L).build()));
        when(trabajoRepository.save(any(TrabajoImpresion.class))).thenAnswer(inv -> {
            guardado = inv.getArgument(0);
            if (guardado.getId() == null) guardado.setId(50L);
            return guardado;
        });
        when(trabajoRepository.findById(50L)).thenAnswer(inv -> Optional.ofNullable(guardado));
        when(trabajoRepository.findByEstadoInAndImpresoraInOrderByIdAsc(any(), any())).thenReturn(List.of());
        when(trabajoRepository.marcarEnviado(eq(50L), anyString(), any(), any(), any())).thenReturn(1);
        when(escPos.generar(any())).thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void sinAgenteConectado_elTrabajoQuedaPendienteParaCualquierImpresora() {
        TrabajoImpresion t = service.imprimirFactura(1L, null);

        assertThat(t.getEstado()).isEqualTo(EstadoTrabajoImpresion.PENDIENTE);
        assertThat(t.getImpresora()).isEqualTo(ImpresionService.CUALQUIERA);
        verify(trabajoRepository, never()).marcarEnviado(any(), any(), any(), any(), any());
    }

    @Test
    void conUnaSolaImpresoraConectada_laUsaAunqueLaTabletNoHayaElegido() {
        service.conectarAgente("PC Entrada", Set.of("Boletería"));

        service.imprimirFactura(1L, null);

        assertThat(guardado.getImpresora()).isEqualTo("Boletería");
        verify(trabajoRepository).marcarEnviado(eq(50L), eq("Boletería"), any(), eq(EstadoTrabajoImpresion.ENVIADO), any());
        assertThat(service.impresorasConectadas())
                .extracting(ImpresionService.ImpresoraConectada::nombre)
                .containsExactly("Boletería");
    }

    @Test
    void seMarcaEnviadoAntesDeMandarlo_paraQueUnaConfirmacionRapidaNoSePise() {
        service.conectarAgente("PC Entrada", Set.of("Boletería"));
        InOrder orden = inOrder(escPos, trabajoRepository);

        service.imprimirFactura(1L, "Boletería");

        // Primero se arma el ticket y se marca ENVIADO (condicional); recién después sale.
        orden.verify(escPos).generar(any());
        orden.verify(trabajoRepository).marcarEnviado(eq(50L), eq("Boletería"), any(), any(), any());
        verify(trabajoRepository, never()).volverAPendiente(any(), any(), any());
    }

    @Test
    void facturaAnuladaMientrasEsperaba_noSeImprime() {
        Factura anulada = Factura.builder().id(1L).anulacionPedida(true).build();
        when(facturaRepository.findById(1L)).thenReturn(Optional.of(anulada));
        service.conectarAgente("PC Entrada", Set.of("Boletería"));

        service.imprimirFactura(1L, "Boletería");

        verify(escPos, never()).generar(any());
        verify(trabajoRepository, never()).marcarEnviado(any(), any(), any(), any(), any());
        verify(trabajoRepository).registrarResultado(eq(50L), eq(EstadoTrabajoImpresion.ERROR), any(), any(), any());
    }

    @Test
    void cancelarTrabajos_soloLosQueNoTienenResultado() {
        service.cancelarTrabajos(7L);

        verify(trabajoRepository).cancelarDeFactura(eq(7L), any(), any(), eq(EstadoTrabajoImpresion.ERROR),
                org.mockito.ArgumentMatchers.argThat(estados -> estados.size() == 2
                        && estados.containsAll(List.of(EstadoTrabajoImpresion.PENDIENTE, EstadoTrabajoImpresion.ENVIADO))));
    }

    @Test
    void siYaTeniaResultado_noSeVuelveAMandar() {
        service.conectarAgente("PC Entrada", Set.of("Boletería"));
        when(trabajoRepository.marcarEnviado(eq(50L), anyString(), any(), any(), any())).thenReturn(0);

        service.imprimirFactura(1L, "Boletería");

        verify(trabajoRepository, never()).volverAPendiente(any(), any(), any());
    }

    @Test
    void impresoraPedidaQueNoEstaConectada_quedaPendiente() {
        service.conectarAgente("PC Entrada", Set.of("Boletería"));

        TrabajoImpresion t = service.imprimirFactura(1L, "Cantina");

        assertThat(t.getEstado()).isEqualTo(EstadoTrabajoImpresion.PENDIENTE);
        verifyNoInteractions(escPos);
    }

    @Test
    void estadoInformadoPorElAgente_seVeEnLaListaDeImpresoras() {
        service.conectarAgente("PC Entrada", Set.of("Boletería", "Caja"));

        service.actualizarEstados("PC Entrada", java.util.Map.of(
                "Boletería", new ImpresionService.EstadoImpresora(false, "apagada o desconectada"),
                "Inventada", new ImpresionService.EstadoImpresora(false, "x")));

        assertThat(service.impresorasConectadas()).containsExactly(
                new ImpresionService.ImpresoraConectada("Boletería", "PC Entrada", false, "apagada o desconectada"),
                // Sin informe todavía: se la da por disponible.
                new ImpresionService.ImpresoraConectada("Caja", "PC Entrada", true, null));
    }

    @Test
    void agenteSinNoticias_dejaDeFigurarAunqueLaConexionParezcaAbierta() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "segundosSinNoticias", 0);
        service.conectarAgente("PC Entrada", Set.of("Boletería"));
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}

        assertThat(service.impresorasConectadas()).isEmpty();
        service.latido();
        assertThat(service.impresorasConectadas()).isEmpty();
    }

    @Test
    void estadoDeUnAgenteQueNoEstaConectado_seIgnora() {
        service.actualizarEstados("Fantasma", java.util.Map.of("Boletería", new ImpresionService.EstadoImpresora(false, "x")));

        assertThat(service.impresorasConectadas()).isEmpty();
    }

    @Test
    void resultado_esUnUpdateQueNuncaPisaUnImpreso() {
        service.registrarResultado(50L, false, "Sin papel");

        verify(trabajoRepository).registrarResultado(eq(50L), eq(EstadoTrabajoImpresion.ERROR), eq("Sin papel"), any(),
                eq(EstadoTrabajoImpresion.IMPRESO));
        verify(trabajoRepository, never()).save(any());
    }

    @Test
    void latido_venceLosViejosConUnUpdateCondicional() {
        service.latido();

        verify(trabajoRepository).vencer(any(), anyString(), any(), eq(EstadoTrabajoImpresion.ERROR), any());
        verify(trabajoRepository, never()).save(any());
    }
}
