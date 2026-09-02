package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.AfluenciaDiariaDTO;
import org.example.laserranitaentradas.model.dto.RecaudacionPorFormaPagoDTO;
import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.repository.TipoEntradaRepository;
import org.example.laserranitaentradas.service.CajaService;
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
 * El reporte atribuye cada métrica a la fecha que le corresponde y NO a un único "día de la
 * compra": la recaudación va al día de cobro (Mercado Pago = fecha de compra; efectivo/tarjeta/QR
 * = fecha de validación, que es cuando se cobra en la puerta), la afluencia y las personas
 * ingresadas van SIEMPRE a la fecha de validación, y la demanda ("reservado para ese día") va a
 * la fecha de visita. Estos tests cubren los casos donde esas fechas caen en meses distintos.
 * También: una caja deshabilitada por un admin saca sus ventas de puerta de todo el reporte.
 */
@ExtendWith(MockitoExtension.class)
class ReporteServiceImplTest {

    @Mock private CompraRepository compraRepository;
    @Mock private TipoEntradaRepository tipoEntradaRepository;
    @Mock private CajaRepository cajaRepository;
    @Mock private RetiroCajaRepository retiroCajaRepository;
    @Mock private CajaService cajaService;

    private ReporteServiceImpl service;

    private static final LocalDate DIA = LocalDate.of(2026, 8, 15);
    private static final LocalDate ENERO = LocalDate.of(2026, 1, 10);
    private static final LocalDate MARZO = LocalDate.of(2026, 3, 20);

    @BeforeEach
    void setUp() {
        service = new ReporteServiceImpl(compraRepository, tipoEntradaRepository, cajaRepository, retiroCajaRepository, cajaService);
        lenient().when(tipoEntradaRepository.findAll()).thenReturn(List.of());
        lenient().when(cajaRepository.findAllByFechaCierreBetweenOrderByFechaCierreDesc(any(), any())).thenReturn(List.of());
        lenient().when(cajaRepository.findIdsDeshabilitadas()).thenReturn(List.of());
        lenient().when(cajaService.diferenciaPosnetPorCaja(any())).thenReturn(java.util.Map.of());
    }

    @Test
    void generarResumen_compraDeCajaDeshabilitada_noSumaEnRecaudacionNiFormasDePago() {
        Caja cajaDeshabilitada = new Caja();
        cajaDeshabilitada.setId(99L);

        Compra deTurnoDeshabilitado = ventaPuerta("10000", FormaPago.EFECTIVO_BOLETERIA, DIA);
        deTurnoDeshabilitado.setCaja(cajaDeshabilitada);
        Compra online = mpAprobada("7000", DIA, DIA); // sin caja

        when(cajaRepository.findIdsDeshabilitadas()).thenReturn(List.of(99L));
        stubReporte(deTurnoDeshabilitado, online);

        ReporteResumenDTO resumen = service.generarResumen(DIA, DIA);

        assertThat(resumen.getRecaudacionTotal()).isEqualByComparingTo("7000");
        assertThat(resumen.getCantidadCompras()).isEqualTo(1);
        assertThat(montoDe(resumen, FormaPago.EFECTIVO_BOLETERIA)).isEqualByComparingTo("0");
        assertThat(montoDe(resumen, FormaPago.MERCADO_PAGO)).isEqualByComparingTo("7000");
    }

    @Test
    void generarResumen_sinCajasDeshabilitadas_todasLasComprasCuentan() {
        Caja cajaHabilitada = new Caja();
        cajaHabilitada.setId(5L);

        Compra puerta = ventaPuerta("10000", FormaPago.EFECTIVO_BOLETERIA, DIA);
        puerta.setCaja(cajaHabilitada);
        Compra online = mpAprobada("7000", DIA, DIA);

        stubReporte(puerta, online);

        ReporteResumenDTO resumen = service.generarResumen(DIA, DIA);

        assertThat(resumen.getRecaudacionTotal()).isEqualByComparingTo("17000");
        assertThat(resumen.getCantidadCompras()).isEqualTo(2);
    }

    @Test
    void generarResumen_preventaMercadoPago_recaudacionEnElMesDeCompraNoEnElDeLaVisita() {
        // Pagó por Mercado Pago en enero para visitar (y validar) en marzo.
        Compra preventa = mpUsada("12000", ENERO, MARZO, MARZO.atTime(11, 0));

        stubReporte(preventa);

        ReporteResumenDTO enero = service.generarResumen(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(enero.getRecaudacionTotal()).isEqualByComparingTo("12000");
        assertThat(enero.getPersonasIngresadas()).isZero();

        stubReporte(preventa);
        ReporteResumenDTO marzo = service.generarResumen(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(marzo.getRecaudacionTotal()).isEqualByComparingTo("0");
        assertThat(marzo.getPersonasIngresadas()).isEqualTo(2);
        assertThat(pasesValidados(marzo, MARZO)).isEqualTo(2);
        assertThat(pasesReservados(marzo, MARZO)).isEqualTo(2);
    }

    @Test
    void generarResumen_reservaEfectivoValidadaOtroDia_plataEIngresoVanAlDiaQueVino_demandaAlQueReservo() {
        LocalDate sabado = LocalDate.of(2026, 3, 7);
        LocalDate domingo = LocalDate.of(2026, 3, 8);
        // Reservó para el sábado, pagó y entró el domingo.
        Compra reserva = efectivoUsada("9000", sabado, domingo.atTime(15, 0));

        stubReporte(reserva);
        ReporteResumenDTO soloSabado = service.generarResumen(sabado, sabado);
        assertThat(soloSabado.getRecaudacionTotal()).isEqualByComparingTo("0");
        assertThat(soloSabado.getPersonasIngresadas()).isZero();
        assertThat(pasesReservados(soloSabado, sabado)).isEqualTo(2);
        assertThat(pasesValidados(soloSabado, sabado)).isZero();

        stubReporte(reserva);
        ReporteResumenDTO soloDomingo = service.generarResumen(domingo, domingo);
        assertThat(soloDomingo.getRecaudacionTotal()).isEqualByComparingTo("9000");
        assertThat(soloDomingo.getPersonasIngresadas()).isEqualTo(2);
        assertThat(pasesReservados(soloDomingo, domingo)).isZero();
        assertThat(pasesValidados(soloDomingo, domingo)).isEqualTo(2);
    }

    @Test
    void generarResumen_reservaEfectivoNoShow_cuentaComoDemandaPeroNoComoIngresoNiRecaudacion() {
        Compra noShow = new Compra();
        noShow.setMontoTotal(new BigDecimal("9000"));
        noShow.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        noShow.setEstado(EstadoCompra.RESERVADO_EFECTIVO);
        noShow.setDescuentoAplicado(BigDecimal.ZERO);
        noShow.setDetalles(new ArrayList<>(List.of(detalleEntrada(2))));
        noShow.setFechaVisita(DIA);
        noShow.setFechaCreacion(DIA.minusDays(3).atTime(10, 0));

        stubReporte(noShow);

        ReporteResumenDTO resumen = service.generarResumen(DIA, DIA);
        assertThat(resumen.getRecaudacionTotal()).isEqualByComparingTo("0");
        assertThat(resumen.getPersonasIngresadas()).isZero();
        assertThat(pasesReservados(resumen, DIA)).isEqualTo(2);
    }

    @Test
    void generarResumen_regalo_recaudacionAlDiaDeCompra_ingresoAlDiaDeCanje() {
        // Regalo: sin fechaVisita nunca. Comprado por MP en enero, canjeado en marzo.
        Compra regalo = mpUsada("8000", ENERO, null, MARZO.atTime(16, 0));

        stubReporte(regalo);
        ReporteResumenDTO enero = service.generarResumen(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(enero.getRecaudacionTotal()).isEqualByComparingTo("8000");
        assertThat(enero.getPersonasIngresadas()).isZero();

        stubReporte(regalo);
        ReporteResumenDTO marzo = service.generarResumen(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(marzo.getRecaudacionTotal()).isEqualByComparingTo("0");
        assertThat(marzo.getPersonasIngresadas()).isEqualTo(2);
        assertThat(pasesValidados(marzo, MARZO)).isEqualTo(2);
        // El regalo no reservó un día: no aporta a la demanda.
        assertThat(pasesReservados(marzo, MARZO)).isZero();
    }

    // ---------- helpers ----------

    private void stubReporte(Compra... compras) {
        when(compraRepository.findParaReporte(any(), any(), any(), any())).thenReturn(List.of(compras));
    }

    private static TipoEntrada tipoEntrada() {
        TipoEntrada t = new TipoEntrada();
        t.setId(1L);
        t.setNombre("General");
        t.setTipo(Tipo.ENTRADA);
        t.setPrecio(new BigDecimal("4500"));
        return t;
    }

    private static CompraDetalle detalleEntrada(int cantidad) {
        CompraDetalle d = new CompraDetalle();
        d.setTipoEntrada(tipoEntrada());
        d.setCantidad(cantidad);
        return d;
    }

    private static Compra base(String monto, FormaPago formaPago, EstadoCompra estado) {
        Compra c = new Compra();
        c.setMontoTotal(new BigDecimal(monto));
        c.setFormaPago(formaPago);
        c.setEstado(estado);
        c.setDescuentoAplicado(BigDecimal.ZERO);
        c.setDetalles(new ArrayList<>(List.of(detalleEntrada(2))));
        return c;
    }

    /** Venta de puerta: compra = visita = validación, todo el mismo día. */
    private static Compra ventaPuerta(String monto, FormaPago formaPago, LocalDate dia) {
        Compra c = base(monto, formaPago, EstadoCompra.VENDIDO_EN_PUERTA);
        c.setFechaVisita(dia);
        c.setFechaCreacion(dia.atTime(13, 0));
        c.setFechaValidacion(dia.atTime(13, 0));
        return c;
    }

    private static Compra mpAprobada(String monto, LocalDate compra, LocalDate visita) {
        Compra c = base(monto, FormaPago.MERCADO_PAGO, EstadoCompra.APROBADO);
        c.setFechaCreacion(compra.atTime(12, 0));
        c.setFechaVisita(visita);
        return c;
    }

    private static Compra mpUsada(String monto, LocalDate compra, LocalDate visita, LocalDateTime validacion) {
        Compra c = base(monto, FormaPago.MERCADO_PAGO, EstadoCompra.USADO);
        c.setFechaCreacion(compra.atTime(12, 0));
        c.setFechaVisita(visita);
        c.setFechaValidacion(validacion);
        return c;
    }

    private static Compra efectivoUsada(String monto, LocalDate visita, LocalDateTime validacion) {
        Compra c = base(monto, FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.USADO);
        c.setFechaCreacion(visita.minusDays(5).atTime(9, 0));
        c.setFechaVisita(visita);
        c.setFechaValidacion(validacion);
        return c;
    }

    private static BigDecimal montoDe(ReporteResumenDTO resumen, FormaPago formaPago) {
        return resumen.getRecaudacionPorFormaPago().stream()
                .filter(r -> r.getFormaPago() == formaPago)
                .map(RecaudacionPorFormaPagoDTO::getMonto)
                .findFirst()
                .orElseThrow();
    }

    private static long pasesValidados(ReporteResumenDTO resumen, LocalDate dia) {
        return afluencia(resumen, dia).getPasesValidadosAnticipada();
    }

    private static long pasesReservados(ReporteResumenDTO resumen, LocalDate dia) {
        return afluencia(resumen, dia).getPasesVendidosAnticipada();
    }

    private static AfluenciaDiariaDTO afluencia(ReporteResumenDTO resumen, LocalDate dia) {
        return resumen.getAfluenciaDiaria().stream()
                .filter(a -> a.getFecha().equals(dia))
                .findFirst()
                .orElseThrow();
    }
}
