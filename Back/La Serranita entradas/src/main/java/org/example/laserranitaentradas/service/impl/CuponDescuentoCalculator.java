package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.AplicacionDescuento;
import org.example.laserranitaentradas.model.entity.Cupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Calcula cuánto descuenta un cupón sobre las entradas de una compra. Un solo lugar para la
 * compra online y para la cotización, así el total que ve el cliente es el que se le cobra.
 *
 * Trabaja sobre líneas (tipo, cantidad, total ya con precio de grupo si correspondía): el
 * precio unitario de una línea es total / cantidad.
 */
final class CuponDescuentoCalculator {

    private CuponDescuentoCalculator() {}

    record Linea(Long tipoEntradaId, int cantidad, BigDecimal total) {
        BigDecimal unitario() {
            return cantidad == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(cantidad), 4, RoundingMode.HALF_UP);
        }
    }

    /** descuento siempre >= 0; motivoNoAplica != null significa que el cupón no se puede usar con esas entradas. */
    record Resultado(BigDecimal descuento, String motivoNoAplica) {
        static Resultado noAplica(String motivo) {
            return new Resultado(BigDecimal.ZERO, motivo);
        }
    }

    static Resultado calcular(Cupon cupon, List<Linea> lineas) {
        Set<Long> tipos = cupon.getTiposEntradaIds();
        boolean filtraPorTipo = tipos != null && !tipos.isEmpty();

        List<Linea> elegibles = lineas.stream()
                .filter(l -> l.cantidad() > 0)
                .filter(l -> !filtraPorTipo || tipos.contains(l.tipoEntradaId()))
                .sorted(Comparator.comparing(Linea::unitario).reversed())
                .toList();
        int cantidadElegible = elegibles.stream().mapToInt(Linea::cantidad).sum();

        if (cantidadElegible == 0) {
            return Resultado.noAplica("El cupón " + cupon.getCodigo() + " no aplica a ninguna de las entradas elegidas.");
        }
        if (cupon.getMinEntradas() != null && cantidadElegible < cupon.getMinEntradas()) {
            return Resultado.noAplica("El cupón " + cupon.getCodigo() + " requiere al menos "
                    + cupon.getMinEntradas() + " entradas.");
        }

        boolean porPorcentaje = cupon.getPorcentajeDescuento() != null;
        AplicacionDescuento aplicaPor = cupon.getAplicaPor() == null ? AplicacionDescuento.COMPRA : cupon.getAplicaPor();
        boolean restaPorEntrada = !porPorcentaje && aplicaPor == AplicacionDescuento.ENTRADA;
        boolean precioFijoPorEntrada = !porPorcentaje && aplicaPor == AplicacionDescuento.PRECIO_ENTRADA;
        boolean porEntrada = restaPorEntrada || precioFijoPorEntrada;
        BigDecimal monto = cupon.getMontoDescuento();

        int restantes = cupon.getMaxEntradasAfectadas() == null ? cantidadElegible : cupon.getMaxEntradasAfectadas();
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal descuentoPorEntrada = BigDecimal.ZERO;
        for (Linea l : elegibles) {
            int tomadas = Math.min(restantes, l.cantidad());
            if (tomadas <= 0) break;
            restantes -= tomadas;
            // Una línea tomada entera suma su total tal cual (sin redondeos de dividir y volver a multiplicar).
            BigDecimal parte = tomadas == l.cantidad()
                    ? l.total()
                    : l.total().multiply(BigDecimal.valueOf(tomadas)).divide(BigDecimal.valueOf(l.cantidad()), 2, RoundingMode.HALF_UP);
            base = base.add(parte);
            if (porEntrada && monto != null) {
                BigDecimal porUnidad = parte.divide(BigDecimal.valueOf(tomadas), 2, RoundingMode.HALF_UP);
                // Restar: cada entrada pierde el monto (sin pasar de su precio). Precio fijo: cada
                // entrada pasa a costar el monto, o queda como está si ya era más barata.
                BigDecimal porEntradaDescontada = restaPorEntrada
                        ? monto.min(porUnidad)
                        : porUnidad.subtract(monto).max(BigDecimal.ZERO);
                descuentoPorEntrada = descuentoPorEntrada.add(porEntradaDescontada.multiply(BigDecimal.valueOf(tomadas)));
            }
        }

        BigDecimal descuento;
        if (porPorcentaje) {
            descuento = base.multiply(cupon.getPorcentajeDescuento()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (porEntrada) {
            descuento = descuentoPorEntrada;
        } else if (monto != null) {
            descuento = monto.min(base);
        } else {
            descuento = BigDecimal.ZERO;
        }

        if (cupon.getTopeDescuento() != null) {
            descuento = descuento.min(cupon.getTopeDescuento());
        }
        return new Resultado(descuento, null);
    }

    /** Valida las reglas de un cupón nuevo; lanza IllegalArgumentException con qué está mal. */
    static void validarReglas(BigDecimal porcentaje, BigDecimal monto, AplicacionDescuento aplicaPor,
                              Integer minEntradas, Integer maxEntradas, BigDecimal tope,
                              LocalDate desde, LocalDate hasta) {
        boolean tienePorcentaje = porcentaje != null && porcentaje.compareTo(BigDecimal.ZERO) > 0;
        boolean tieneMonto = monto != null && monto.compareTo(BigDecimal.ZERO) > 0;
        // Exactamente uno: con los dos, el monto quedaría de adorno, callado.
        if (tienePorcentaje == tieneMonto) {
            throw new IllegalArgumentException("El cupón necesita un porcentaje o un monto de descuento mayor a cero (uno solo, no los dos).");
        }
        if (aplicaPor != null && aplicaPor != AplicacionDescuento.COMPRA && !tieneMonto) {
            throw new IllegalArgumentException("\"Por entrada\" sólo tiene sentido con un monto, no con un porcentaje.");
        }
        if (minEntradas != null && minEntradas < 1) {
            throw new IllegalArgumentException("La cantidad mínima de entradas tiene que ser 1 o más.");
        }
        if (maxEntradas != null && maxEntradas < 1) {
            throw new IllegalArgumentException("El máximo de entradas con descuento tiene que ser 1 o más.");
        }
        if (minEntradas != null && maxEntradas != null && minEntradas > maxEntradas) {
            throw new IllegalArgumentException("El mínimo de entradas no puede ser mayor que el máximo con descuento.");
        }
        if (tope != null && tope.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El tope de descuento tiene que ser mayor a cero.");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la de vencimiento.");
        }
    }
}
