package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.DescuentoEfectivo;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.DescuentoEfectivoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El precio por grupo (efectivo en boletería) tiene tres reglas que importan probar
 * porque no son obvias a simple vista: sin promo definida se cobra precio de lista,
 * grupos más chicos que el primer escalón NO tienen descuento, y grupos más grandes
 * que el último escalón pagan el precio por persona de ese escalón en vez de volver a
 * precio de lista completo (el bug que se corrigió en esta clase).
 */
@ExtendWith(MockitoExtension.class)
class CalculoPrecioServiceImplTest {

    @Mock
    private DescuentoEfectivoRepository descuentoEfectivoRepository;

    private CalculoPrecioServiceImpl service;

    private TipoEntrada paseGeneral;

    @BeforeEach
    void setUp() {
        service = new CalculoPrecioServiceImpl(descuentoEfectivoRepository);
        paseGeneral = TipoEntrada.builder()
                .id(1L)
                .nombre("Pase General")
                .precio(new BigDecimal("34300.00"))
                .tipo(Tipo.ENTRADA)
                .build();
    }

    private DescuentoEfectivo escalon(int cantidadPases, String precioTotal) {
        DescuentoEfectivo d = new DescuentoEfectivo();
        d.setTipoEntrada(paseGeneral);
        d.setCantidadPases(cantidadPases);
        d.setPrecioPromocionalTotal(new BigDecimal(precioTotal));
        return d;
    }

    @Test
    void mercadoPago_siempreCobraPrecioDeLista_sinImportarSiHayPromoDeEfectivo() {
        BigDecimal total = service.calcularTotal(paseGeneral, 10, FormaPago.MERCADO_PAGO);

        assertThat(total).isEqualByComparingTo("343000.00");
        verifyNoInteractions(descuentoEfectivoRepository);
    }

    @Test
    void efectivo_conEscalonExacto_cobraElPrecioPromocional() {
        when(descuentoEfectivoRepository.findByTipoEntradaAndCantidadPases(paseGeneral, 3))
                .thenReturn(Optional.of(escalon(3, "95700.00")));

        BigDecimal total = service.calcularTotal(paseGeneral, 3, FormaPago.EFECTIVO_BOLETERIA);
        BigDecimal ahorro = service.calcularAhorro(paseGeneral, 3, FormaPago.EFECTIVO_BOLETERIA);

        assertThat(total).isEqualByComparingTo("95700.00");
        assertThat(ahorro).isEqualByComparingTo("7200.00"); // 3*34300 - 95700
    }

    @Test
    void efectivo_porDebajoDelEscalonMinimo_noTieneDescuento() {
        // El escalón más chico configurado es de 3 pases; para 2 no hay promo, no se extrapola "hacia abajo".
        when(descuentoEfectivoRepository.findByTipoEntradaAndCantidadPases(paseGeneral, 2))
                .thenReturn(Optional.empty());
        when(descuentoEfectivoRepository.findFirstByTipoEntradaOrderByCantidadPasesDesc(paseGeneral))
                .thenReturn(Optional.of(escalon(10, "307000.00")));

        BigDecimal total = service.calcularTotal(paseGeneral, 2, FormaPago.EFECTIVO_BOLETERIA);

        assertThat(total).isEqualByComparingTo("68600.00"); // 2 * 34300, precio de lista
    }

    @Test
    void efectivo_sinNingunEscalonConfigurado_cobraPrecioDeLista() {
        when(descuentoEfectivoRepository.findByTipoEntradaAndCantidadPases(eq(paseGeneral), any()))
                .thenReturn(Optional.empty());
        when(descuentoEfectivoRepository.findFirstByTipoEntradaOrderByCantidadPasesDesc(paseGeneral))
                .thenReturn(Optional.empty());

        BigDecimal total = service.calcularTotal(paseGeneral, 5, FormaPago.EFECTIVO_BOLETERIA);

        assertThat(total).isEqualByComparingTo("171500.00"); // 5 * 34300
    }

    @Test
    void efectivo_porEncimaDelEscalonMaximo_extrapolaElPrecioPorPersonaDelUltimoEscalon() {
        // Escalón máximo: 10 pases por 307000 -> 30700 por persona. Para 11, no vuelve a
        // precio de lista: sigue pagando 30700 por persona (era el bug original).
        when(descuentoEfectivoRepository.findByTipoEntradaAndCantidadPases(paseGeneral, 11))
                .thenReturn(Optional.empty());
        when(descuentoEfectivoRepository.findFirstByTipoEntradaOrderByCantidadPasesDesc(paseGeneral))
                .thenReturn(Optional.of(escalon(10, "307000.00")));

        BigDecimal total = service.calcularTotal(paseGeneral, 11, FormaPago.EFECTIVO_BOLETERIA);

        assertThat(total).isEqualByComparingTo("337700.00"); // 11 * 30700
    }
}
