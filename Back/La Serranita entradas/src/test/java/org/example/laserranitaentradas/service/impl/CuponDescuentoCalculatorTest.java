package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.AplicacionDescuento;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CuponDescuentoCalculatorTest {

    private static final long MAYOR = 1L;
    private static final long MENOR = 2L;

    /** 2 mayores a 10.000 c/u (total 20.000) y 2 menores a 6.000 c/u (total 12.000). */
    private static final List<CuponDescuentoCalculator.Linea> LINEAS = List.of(
            new CuponDescuentoCalculator.Linea(MAYOR, 2, new BigDecimal("20000")),
            new CuponDescuentoCalculator.Linea(MENOR, 2, new BigDecimal("12000")));

    private static Cupon.CuponBuilder cupon() {
        return Cupon.builder().codigo("TEST");
    }

    private static void assertMonto(String esperado, CuponDescuentoCalculator.Resultado r) {
        assertNull(r.motivoNoAplica());
        assertEquals(0, new BigDecimal(esperado).compareTo(r.descuento()), "descuento: " + r.descuento());
    }

    @Test
    void porcentajeSobreTodasLasEntradas() {
        assertMonto("6400", CuponDescuentoCalculator.calcular(
                cupon().porcentajeDescuento(new BigDecimal("20")).build(), LINEAS));
    }

    @Test
    void montoPorCompraSeRestaUnaSolaVez() {
        assertMonto("5000", CuponDescuentoCalculator.calcular(
                cupon().montoDescuento(new BigDecimal("5000")).build(), LINEAS));
    }

    @Test
    void montoPorEntradaSeRestaAcadaEntrada() {
        assertMonto("20000", CuponDescuentoCalculator.calcular(
                cupon().montoDescuento(new BigDecimal("5000")).aplicaPor(AplicacionDescuento.ENTRADA).build(), LINEAS));
    }

    @Test
    void montoPorEntradaNoDejaUnaEntradaEnNegativo() {
        // 8.000 por entrada: los mayores (10.000) pierden 8.000, los menores (6.000) quedan en 0.
        assertMonto("28000", CuponDescuentoCalculator.calcular(
                cupon().montoDescuento(new BigDecimal("8000")).aplicaPor(AplicacionDescuento.ENTRADA).build(), LINEAS));
    }

    @Test
    void precioFijoPorEntradaDejaCadaEntradaEnEseValor() {
        // Precio fijo 8.000: los mayores (10.000) bajan 2.000 c/u; los menores (6.000) ya son más baratos y no cambian.
        assertMonto("4000", CuponDescuentoCalculator.calcular(
                cupon().montoDescuento(new BigDecimal("8000")).aplicaPor(AplicacionDescuento.PRECIO_ENTRADA).build(), LINEAS));
    }

    @Test
    void precioFijoPorEntradaConMaximoTomaLasMasCaras() {
        // Precio fijo 4.000 sólo en 3 entradas (2 mayores y 1 menor): 6.000 + 6.000 + 2.000.
        assertMonto("14000", CuponDescuentoCalculator.calcular(
                cupon().montoDescuento(new BigDecimal("4000")).aplicaPor(AplicacionDescuento.PRECIO_ENTRADA)
                        .maxEntradasAfectadas(3).build(), LINEAS));
    }

    @Test
    void elMaximoDeEntradasAfectadasTomaLasMasCaras() {
        // 3 entradas con descuento: los 2 mayores y 1 menor, 5.000 por entrada = 15.000.
        assertMonto("15000", CuponDescuentoCalculator.calcular(
                cupon().montoDescuento(new BigDecimal("5000")).aplicaPor(AplicacionDescuento.ENTRADA)
                        .maxEntradasAfectadas(3).build(), LINEAS));
        // Un porcentaje con máximo 2 alcanza sólo a los 2 mayores: 10% de 20.000.
        assertMonto("2000", CuponDescuentoCalculator.calcular(
                cupon().porcentajeDescuento(new BigDecimal("10")).maxEntradasAfectadas(2).build(), LINEAS));
    }

    @Test
    void elFiltroPorTipoSoloContabilizaEsosTipos() {
        assertMonto("1200", CuponDescuentoCalculator.calcular(
                cupon().porcentajeDescuento(new BigDecimal("10")).tiposEntradaIds(Set.of(MENOR)).build(), LINEAS));
    }

    @Test
    void sinEntradasDeLosTiposDelCuponNoAplica() {
        CuponDescuentoCalculator.Resultado r = CuponDescuentoCalculator.calcular(
                cupon().porcentajeDescuento(new BigDecimal("10")).tiposEntradaIds(Set.of(99L)).build(), LINEAS);
        assertNotNull(r.motivoNoAplica());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.descuento()));
    }

    @Test
    void elMinimoSeCuentaSobreLasEntradasQueElCuponAlcanza() {
        Cupon soloMenores = cupon().porcentajeDescuento(new BigDecimal("10")).tiposEntradaIds(Set.of(MENOR)).minEntradas(3).build();
        assertNotNull(CuponDescuentoCalculator.calcular(soloMenores, LINEAS).motivoNoAplica());

        Cupon todas = cupon().porcentajeDescuento(new BigDecimal("10")).minEntradas(4).build();
        assertNull(CuponDescuentoCalculator.calcular(todas, LINEAS).motivoNoAplica());
    }

    @Test
    void elTopeLimitaElDescuento() {
        assertMonto("3000", CuponDescuentoCalculator.calcular(
                cupon().porcentajeDescuento(new BigDecimal("50")).topeDescuento(new BigDecimal("3000")).build(), LINEAS));
    }

    @Test
    void unaLineaTomadaAMediasProrrateaSuTotal() {
        // 3 entradas a 10.000 con precio de grupo (total 27.000 = 9.000 c/u), máximo 2: base 18.000.
        List<CuponDescuentoCalculator.Linea> grupo = List.of(new CuponDescuentoCalculator.Linea(MAYOR, 3, new BigDecimal("27000")));
        assertMonto("1800", CuponDescuentoCalculator.calcular(
                cupon().porcentajeDescuento(new BigDecimal("10")).maxEntradasAfectadas(2).build(), grupo));
    }

    @Test
    void validarReglasRechazaConfiguracionesIncoherentes() {
        BigDecimal cinco = new BigDecimal("5");
        assertThrows(IllegalArgumentException.class, () -> CuponDescuentoCalculator.validarReglas(
                cinco, cinco, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> CuponDescuentoCalculator.validarReglas(
                cinco, null, AplicacionDescuento.ENTRADA, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> CuponDescuentoCalculator.validarReglas(
                null, cinco, null, 5, 3, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> CuponDescuentoCalculator.validarReglas(
                null, cinco, null, null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> CuponDescuentoCalculator.validarReglas(
                cinco, null, AplicacionDescuento.PRECIO_ENTRADA, null, null, null, null, null));
        assertDoesNotThrow(() -> CuponDescuentoCalculator.validarReglas(
                null, cinco, AplicacionDescuento.ENTRADA, 2, 6, new BigDecimal("30000"), null, null));
        assertDoesNotThrow(() -> CuponDescuentoCalculator.validarReglas(
                null, cinco, AplicacionDescuento.PRECIO_ENTRADA, null, null, null, null, null));
    }
}
