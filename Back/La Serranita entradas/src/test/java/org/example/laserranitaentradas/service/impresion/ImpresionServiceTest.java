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
    }

    @Test
    void sinAgenteConectado_elTrabajoQuedaPendienteParaCualquierImpresora() {
        TrabajoImpresion t = service.imprimirFactura(1L, null);

        assertThat(t.getEstado()).isEqualTo(EstadoTrabajoImpresion.PENDIENTE);
        assertThat(t.getImpresora()).isEqualTo(ImpresionService.CUALQUIERA);
        verifyNoInteractions(escPos);
    }

    @Test
    void conUnaSolaImpresoraConectada_laUsaAunqueLaTabletNoHayaElegido() {
        service.conectarAgente("PC Entrada", Set.of("Boletería"));
        when(escPos.generar(any())).thenReturn(new byte[]{1, 2, 3});

        TrabajoImpresion t = service.imprimirFactura(1L, null);

        assertThat(t.getImpresora()).isEqualTo("Boletería");
        assertThat(t.getEstado()).isEqualTo(EstadoTrabajoImpresion.ENVIADO);
        assertThat(service.impresorasConectadas())
                .extracting(ImpresionService.ImpresoraConectada::nombre)
                .containsExactly("Boletería");
    }

    @Test
    void impresoraPedidaQueNoEstaConectada_quedaPendiente() {
        service.conectarAgente("PC Entrada", Set.of("Boletería"));

        TrabajoImpresion t = service.imprimirFactura(1L, "Cantina");

        assertThat(t.getEstado()).isEqualTo(EstadoTrabajoImpresion.PENDIENTE);
        verifyNoInteractions(escPos);
    }

    @Test
    void confirmacionRepetida_noPisaUnImpreso() {
        guardado = TrabajoImpresion.builder().id(50L).estado(EstadoTrabajoImpresion.IMPRESO).impresora("Boletería").build();

        service.registrarResultado(50L, false, "papel");

        assertThat(guardado.getEstado()).isEqualTo(EstadoTrabajoImpresion.IMPRESO);
    }

    @Test
    void resultadoConError_quedaEnErrorConElMotivo() {
        guardado = TrabajoImpresion.builder().id(50L).estado(EstadoTrabajoImpresion.ENVIADO).impresora("Boletería").build();

        service.registrarResultado(50L, false, "Sin papel");

        assertThat(guardado.getEstado()).isEqualTo(EstadoTrabajoImpresion.ERROR);
        assertThat(guardado.getError()).isEqualTo("Sin papel");
    }
}
