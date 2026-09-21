package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.repository.DiaAperturaRepository;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * El límite de compra online para el mismo día: el parque puede seguir abierto pero ya no se
 * vende para hoy un rato antes del cierre. Se mide contra el cierre de ESE día (el especial si
 * lo tiene) y sólo corta el día de hoy.
 */
@ExtendWith(MockitoExtension.class)
class DiaAperturaServiceImplTest {

    @Mock private DiaAperturaRepository diaAperturaRepository;
    @Mock private ConfiguracionParqueService configuracionParqueService;

    private DiaAperturaServiceImpl service;

    private static final LocalDate HOY = LocalDate.of(2026, 9, 21);

    @BeforeEach
    void setUp() {
        service = new DiaAperturaServiceImpl(diaAperturaRepository, configuracionParqueService);
        configurarHorarioGeneral(60);
        lenient().when(diaAperturaRepository.findByFecha(any())).thenReturn(Optional.empty());
    }

    private void configurarHorarioGeneral(int minutosLimite) {
        lenient().when(configuracionParqueService.getHorarioGeneral()).thenReturn(ConfiguracionParque.builder()
                .id(1L).horaApertura(LocalTime.of(9, 0)).horaCierre(LocalTime.of(18, 0))
                .minutosLimiteCompra(minutosLimite).build());
    }

    private LocalDateTime a(int hora, int minuto) {
        return LocalDateTime.of(HOY, LocalTime.of(hora, minuto));
    }

    @Test
    void elLimiteEsElCierreMenosElLapso() {
        assertThat(service.getLimiteDeCompra(HOY)).isEqualTo(a(17, 0));
    }

    @Test
    void antesDelLimiteSeSigueComprandoParaHoy() {
        assertThat(service.compraDelDiaCerrada(HOY, a(16, 59))).isFalse();
    }

    @Test
    void enElLimiteYDespuesYaNoSeCompraParaHoy() {
        assertThat(service.compraDelDiaCerrada(HOY, a(17, 0))).isTrue();
        assertThat(service.compraDelDiaCerrada(HOY, a(17, 45))).isTrue();
        assertThat(service.compraDelDiaCerrada(HOY, a(23, 30))).isTrue();
    }

    @Test
    void unaFechaFuturaNuncaSeCorta() {
        // Aunque "ahora" ya esté pasado el límite de hoy, mañana se puede comprar.
        assertThat(service.compraDelDiaCerrada(HOY.plusDays(1), a(23, 59))).isFalse();
    }

    @Test
    void unaFechaPasadaNoEsAsuntoDeEstaRegla() {
        assertThat(service.compraDelDiaCerrada(HOY.minusDays(1), a(23, 59))).isFalse();
    }

    @Test
    void conLapsoCeroSeCompraHastaLaHoraDeCierre() {
        configurarHorarioGeneral(0);

        assertThat(service.compraDelDiaCerrada(HOY, a(17, 59))).isFalse();
        assertThat(service.compraDelDiaCerrada(HOY, a(18, 0))).isTrue();
    }

    @Test
    void conHorarioEspecialElLimiteSeMideContraElCierreDeEseDia() {
        // Ese día el parque cierra a las 14: el corte es 13:00, aunque el general (18) diría 17:00.
        when(diaAperturaRepository.findByFecha(HOY)).thenReturn(Optional.of(DiaApertura.builder()
                .fecha(HOY).abierto(true).horaApertura(LocalTime.of(9, 0)).horaCierre(LocalTime.of(14, 0)).build()));

        assertThat(service.getLimiteDeCompra(HOY)).isEqualTo(a(13, 0));
        assertThat(service.compraDelDiaCerrada(HOY, a(13, 30))).isTrue();
    }

    @Test
    void unDiaSinCierreEspecialUsaElGeneral() {
        when(diaAperturaRepository.findByFecha(HOY)).thenReturn(Optional.of(DiaApertura.builder()
                .fecha(HOY).abierto(true).build()));

        assertThat(service.getLimiteDeCompra(HOY)).isEqualTo(a(17, 0));
    }

    // ---------- El calendario público ----------

    private void abiertosHoyYManana() {
        when(diaAperturaRepository.findAllByFechaBetween(any(), any())).thenReturn(List.of(
                DiaApertura.builder().fecha(HOY).abierto(true).build(),
                DiaApertura.builder().fecha(HOY.plusDays(1)).abierto(true).build()));
    }

    @Test
    void elCalendarioOfreceHoyMientrasSeaPosibleComprar() {
        abiertosHoyYManana();

        assertThat(service.getDiasAbiertos(2026, 9, a(10, 0))).contains("2026-09-21", "2026-09-22");
    }

    @Test
    void elCalendarioSigueDevolviendoLosDiasPasadosAbiertos() {
        // Sólo "hoy" sale de la lista: un día pasado que estuvo abierto se sigue devolviendo, así el
        // calendario lo pinta como "pasado abierto" y no como "pasado cerrado".
        when(diaAperturaRepository.findAllByFechaBetween(any(), any())).thenReturn(List.of(
                DiaApertura.builder().fecha(HOY.minusDays(4)).abierto(true).build(),
                DiaApertura.builder().fecha(HOY.minusDays(3)).abierto(false).build(),
                DiaApertura.builder().fecha(HOY).abierto(true).build()));

        assertThat(service.getDiasAbiertos(2026, 9, a(17, 30))).containsExactly("2026-09-17");
    }

    @Test
    void elCalendarioDejaDeOfrecerHoyPasadoElLimiteYSigueOfreciendoManana() {
        abiertosHoyYManana();

        assertThat(service.getDiasAbiertos(2026, 9, a(17, 30))).containsExactly("2026-09-22");
    }
}
