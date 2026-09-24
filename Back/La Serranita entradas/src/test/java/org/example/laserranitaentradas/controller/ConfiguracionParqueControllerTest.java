package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.HorarioResponseDTO;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** El horario que ve el comprador para el día elegido: el especial de ese día si tiene, si no el general. */
@ExtendWith(MockitoExtension.class)
class ConfiguracionParqueControllerTest {

    private static final LocalDate DIA = LocalDate.of(2026, 12, 20);

    @Mock private ConfiguracionParqueService configuracionParqueService;
    @Mock private DiaAperturaService diaAperturaService;

    private ConfiguracionParqueController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfiguracionParqueController(configuracionParqueService, diaAperturaService);
        when(configuracionParqueService.getHorarioGeneral()).thenReturn(ConfiguracionParque.builder()
                .horaApertura(LocalTime.of(9, 0)).horaCierre(LocalTime.of(18, 0)).minutosLimiteCompra(60).build());
    }

    private DiaApertura dia(LocalTime apertura, LocalTime cierre) {
        return DiaApertura.builder().fecha(DIA).abierto(true).horaApertura(apertura).horaCierre(cierre).build();
    }

    @Test
    void sinFecha_devuelveElGeneral() {
        HorarioResponseDTO horario = controller.obtenerHorario(null).getBody();

        assertThat(horario.getHoraApertura()).isEqualTo(LocalTime.of(9, 0));
        assertThat(horario.getHoraCierre()).isEqualTo(LocalTime.of(18, 0));
        assertThat(horario.isEspecial()).isFalse();
    }

    @Test
    void conFechaConHorarioEspecial_devuelveElDelDia() {
        when(diaAperturaService.findByDate(DIA)).thenReturn(Optional.of(dia(LocalTime.of(10, 0), LocalTime.of(15, 30))));

        HorarioResponseDTO horario = controller.obtenerHorario(DIA).getBody();

        assertThat(horario.getHoraApertura()).isEqualTo(LocalTime.of(10, 0));
        assertThat(horario.getHoraCierre()).isEqualTo(LocalTime.of(15, 30));
        assertThat(horario.getMinutosLimiteCompra()).isEqualTo(60);
        assertThat(horario.isEspecial()).isTrue();
    }

    @Test
    void conFechaSinHorarioPropio_devuelveElGeneral() {
        // Día abierto sin horas cargadas (o sin registro): rige el general, y no se marca como especial.
        when(diaAperturaService.findByDate(DIA)).thenReturn(Optional.of(dia(null, null)));

        HorarioResponseDTO horario = controller.obtenerHorario(DIA).getBody();

        assertThat(horario.getHoraCierre()).isEqualTo(LocalTime.of(18, 0));
        assertThat(horario.isEspecial()).isFalse();
    }

    @Test
    void conSoloElCierreEspecial_completaLaAperturaConLaGeneral() {
        // Igual que el límite de compra: lo que el día no define lo toma del general.
        when(diaAperturaService.findByDate(DIA)).thenReturn(Optional.of(dia(null, LocalTime.of(14, 0))));

        HorarioResponseDTO horario = controller.obtenerHorario(DIA).getBody();

        assertThat(horario.getHoraApertura()).isEqualTo(LocalTime.of(9, 0));
        assertThat(horario.getHoraCierre()).isEqualTo(LocalTime.of(14, 0));
        assertThat(horario.isEspecial()).isTrue();
    }
}
