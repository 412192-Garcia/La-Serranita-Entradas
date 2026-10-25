package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.FacturaPredeterminadaDTO;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DestinoFactura;
import org.example.laserranitaentradas.repository.ConfiguracionParqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracionParqueServiceImplTest {

    @Mock private ConfiguracionParqueRepository repository;

    private ConfiguracionParqueServiceImpl service;
    private ConfiguracionParque existente;

    @BeforeEach
    void setUp() {
        service = new ConfiguracionParqueServiceImpl(repository);
        existente = ConfiguracionParque.builder().id(1L)
                .horaApertura(LocalTime.of(9, 0)).horaCierre(LocalTime.of(18, 0)).minutosLimiteCompra(60).build();
    }

    private void conConfiguracionExistente() {
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any(ConfiguracionParque.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void elLapsoPorDefectoEsDeUnaHora() {
        assertThat(ConfiguracionParque.builder().build().getMinutosLimiteCompra()).isEqualTo(60);
    }

    @Test
    void guardaElLapsoNuevoJuntoConElHorario() {
        conConfiguracionExistente();

        ConfiguracionParque r = service.actualizarHorarioGeneral(LocalTime.of(10, 0), LocalTime.of(19, 0), 90);

        assertThat(r.getMinutosLimiteCompra()).isEqualTo(90);
        assertThat(r.getHoraCierre()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void sinLapsoEnElPedidoConservaElQueYaEstaba() {
        conConfiguracionExistente();

        ConfiguracionParque r = service.actualizarHorarioGeneral(LocalTime.of(10, 0), LocalTime.of(19, 0), null);

        assertThat(r.getMinutosLimiteCompra()).isEqualTo(60);
    }

    @Test
    void aceptaCeroYElMaximo() {
        conConfiguracionExistente();

        assertThat(service.actualizarHorarioGeneral(LocalTime.of(9, 0), LocalTime.of(18, 0), 0).getMinutosLimiteCompra()).isZero();
        assertThat(service.actualizarHorarioGeneral(LocalTime.of(9, 0), LocalTime.of(18, 0),
                ConfiguracionParqueServiceImpl.MAX_MINUTOS_LIMITE_COMPRA).getMinutosLimiteCompra()).isEqualTo(720);
    }

    @Test
    void rechazaUnLapsoNegativoOPasadoDelMaximo() {
        assertThatThrownBy(() -> service.actualizarHorarioGeneral(LocalTime.of(9, 0), LocalTime.of(18, 0), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.actualizarHorarioGeneral(LocalTime.of(9, 0), LocalTime.of(18, 0), 721))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void laFacturaPorDefectoEsMailEnEfectivoEImprimirEnTarjetaYQr() {
        ConfiguracionParque config = ConfiguracionParque.builder().build();

        assertThat(config.getFacturaEfectivo()).isEqualTo(DestinoFactura.MAIL);
        assertThat(config.getFacturaTarjeta()).isEqualTo(DestinoFactura.IMPRIMIR);
        assertThat(config.getFacturaQr()).isEqualTo(DestinoFactura.IMPRIMIR);
    }

    @Test
    void guardaLaFacturaPorDefectoDeCadaFormaDePago() {
        conConfiguracionExistente();

        ConfiguracionParque r = service.actualizarFacturaPredeterminada(
                new FacturaPredeterminadaDTO(DestinoFactura.IMPRIMIR, DestinoFactura.MAIL, DestinoFactura.MAIL));

        assertThat(r.getFacturaEfectivo()).isEqualTo(DestinoFactura.IMPRIMIR);
        assertThat(r.getFacturaTarjeta()).isEqualTo(DestinoFactura.MAIL);
        assertThat(r.getFacturaQr()).isEqualTo(DestinoFactura.MAIL);
    }

    @Test
    void rechazaUnaFacturaPorDefectoSinElegirOQueNoEsDelPos() {
        assertThatThrownBy(() -> service.actualizarFacturaPredeterminada(
                new FacturaPredeterminadaDTO(null, DestinoFactura.IMPRIMIR, DestinoFactura.IMPRIMIR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.actualizarFacturaPredeterminada(
                new FacturaPredeterminadaDTO(DestinoFactura.MAIL, DestinoFactura.NINGUNO, DestinoFactura.IMPRIMIR)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }
}
