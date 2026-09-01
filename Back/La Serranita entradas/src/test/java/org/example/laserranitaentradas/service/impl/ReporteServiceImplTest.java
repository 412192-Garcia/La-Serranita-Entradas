package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.RecaudacionPorFormaPagoDTO;
import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.repository.TipoEntradaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * El reporte agrega TODO por fechaVisita de la compra, nunca por caja. El punto no obvio que
 * cubren estos tests: una caja deshabilitada por un admin tiene que sacar sus ventas de puerta
 * de la recaudación y el desglose (como si el turno nunca hubiera existido), sin tocar las
 * compras online (que no tienen caja).
 */
@ExtendWith(MockitoExtension.class)
class ReporteServiceImplTest {

    @Mock private CompraRepository compraRepository;
    @Mock private TipoEntradaRepository tipoEntradaRepository;
    @Mock private CajaRepository cajaRepository;
    @Mock private RetiroCajaRepository retiroCajaRepository;

    private ReporteServiceImpl service;

    private static final LocalDate DIA = LocalDate.of(2026, 8, 15);

    @BeforeEach
    void setUp() {
        service = new ReporteServiceImpl(compraRepository, tipoEntradaRepository, cajaRepository, retiroCajaRepository);
        lenient().when(tipoEntradaRepository.findAll()).thenReturn(List.of());
        lenient().when(cajaRepository.findAllByFechaCierreBetweenOrderByFechaCierreDesc(any(), any())).thenReturn(List.of());
        lenient().when(cajaRepository.findIdsDeshabilitadas()).thenReturn(List.of());
    }

    @Test
    void generarResumen_compraDeCajaDeshabilitada_noSumaEnRecaudacionNiFormasDePago() {
        Caja cajaDeshabilitada = new Caja();
        cajaDeshabilitada.setId(99L);

        Compra deTurnoDeshabilitado = compraCobrada("10000", FormaPago.EFECTIVO_BOLETERIA);
        deTurnoDeshabilitado.setCaja(cajaDeshabilitada);
        Compra online = compraCobrada("7000", FormaPago.MERCADO_PAGO); // sin caja

        when(cajaRepository.findIdsDeshabilitadas()).thenReturn(List.of(99L));
        when(compraRepository.findAllByFechaVisitaBetween(any(), any()))
                .thenReturn(List.of(deTurnoDeshabilitado, online));

        ReporteResumenDTO resumen = service.generarResumen(DIA, DIA);

        // Sólo la venta online cuenta.
        assertThat(resumen.getRecaudacionTotal()).isEqualByComparingTo("7000");
        assertThat(resumen.getCantidadCompras()).isEqualTo(1);
        assertThat(montoDe(resumen, FormaPago.EFECTIVO_BOLETERIA)).isEqualByComparingTo("0");
        assertThat(montoDe(resumen, FormaPago.MERCADO_PAGO)).isEqualByComparingTo("7000");
    }

    @Test
    void generarResumen_sinCajasDeshabilitadas_todasLasComprasCuentan() {
        Caja cajaHabilitada = new Caja();
        cajaHabilitada.setId(5L);

        Compra puerta = compraCobrada("10000", FormaPago.EFECTIVO_BOLETERIA);
        puerta.setCaja(cajaHabilitada);
        Compra online = compraCobrada("7000", FormaPago.MERCADO_PAGO);

        when(compraRepository.findAllByFechaVisitaBetween(any(), any())).thenReturn(List.of(puerta, online));

        ReporteResumenDTO resumen = service.generarResumen(DIA, DIA);

        assertThat(resumen.getRecaudacionTotal()).isEqualByComparingTo("17000");
        assertThat(resumen.getCantidadCompras()).isEqualTo(2);
    }

    private Compra compraCobrada(String monto, FormaPago formaPago) {
        Compra compra = new Compra();
        compra.setMontoTotal(new BigDecimal(monto));
        compra.setFormaPago(formaPago);
        compra.setEstado(EstadoCompra.APROBADO);
        compra.setDescuentoAplicado(BigDecimal.ZERO);
        compra.setDetalles(new ArrayList<>());
        compra.setFechaVisita(DIA);
        compra.setFechaCreacion(LocalDateTime.of(DIA, java.time.LocalTime.of(12, 0)));
        return compra;
    }

    private static BigDecimal montoDe(ReporteResumenDTO resumen, FormaPago formaPago) {
        return resumen.getRecaudacionPorFormaPago().stream()
                .filter(r -> r.getFormaPago() == formaPago)
                .map(RecaudacionPorFormaPagoDTO::getMonto)
                .findFirst()
                .orElseThrow();
    }
}
