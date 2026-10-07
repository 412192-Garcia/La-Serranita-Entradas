package org.example.laserranitaentradas.monitoreo;

import org.example.laserranitaentradas.model.dto.EstadoSistemaDTO.Tarjeta;
import org.example.laserranitaentradas.model.entity.ConfirmacionPago;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sistema > Estado > Ventas: fallas que no tiran ningún error pero se notan en los números. Si
 * Mercado Pago deja de confirmar pagos, o el webhook deja de llegar, o el POS de la puerta se queda
 * sin subir ventas, en el log no aparece nada: lo que cambia es cuánto se vende.
 *
 * - Sin ventas online: 0 en las últimas horas cuando, el mismo día de la semana a la misma hora, lo
 *   normal (promedio de las 4 semanas anteriores) es vender varias.
 * - Checkouts que no se pagan: casi nadie termina de pagar los que abre (se rompió algún paso).
 * - Webhook: los pagos se confirman por el respaldo (verificación del navegador, barrido) y no por
 *   el aviso de Mercado Pago.
 * - Puerta: entra gente con anticipadas y el POS no registra ninguna venta (o al revés).
 *
 * Sólo cuenta en la base: no consulta a Mercado Pago.
 */
@Component
public class AnomaliasVentas {

    static final List<EstadoCompra> PAGADAS = List.of(EstadoCompra.APROBADO, EstadoCompra.USADO, EstadoCompra.REEMBOLSADA);
    private static final int SEMANAS_DE_REFERENCIA = 4;

    private final CompraRepository compras;

    @Value("${app.monitoreo.ventas.ventana-horas:3}")
    private int ventanaHoras;
    /** Con lo normal por debajo de esto, 0 ventas online no es raro (horario flojo, temporada baja). */
    @Value("${app.monitoreo.ventas.minimo-esperado:3}")
    private double minimoEsperado;
    @Value("${app.monitoreo.ventas.minimo-checkouts:6}")
    private int minimoCheckouts;
    /** % de checkouts que se pagan por debajo del cual se avisa. */
    @Value("${app.monitoreo.ventas.conversion-minima:20}")
    private int conversionMinima;
    @Value("${app.monitoreo.ventas.minimo-confirmaciones:4}")
    private int minimoConfirmaciones;
    /** Validaciones (o ventas de puerta) en la última hora a partir de las cuales que el otro lado esté en 0 es raro. */
    @Value("${app.monitoreo.ventas.movimiento-puerta:15}")
    private int movimientoPuerta;

    public AnomaliasVentas(CompraRepository compras) {
        this.compras = compras;
    }

    public Tarjeta tarjeta() {
        LocalDateTime ahora = LocalDateTime.now(AuditoriaService.ZONA).withNano(0);
        List<String> problemas = new ArrayList<>();
        List<String> detalles = new ArrayList<>();
        ventasOnline(ahora, problemas, detalles);
        checkouts(ahora, problemas, detalles);
        webhook(ahora, problemas, detalles);
        puerta(ahora, problemas, detalles);
        List<String> todo = new ArrayList<>(problemas);
        todo.addAll(detalles);
        boolean alerta = !problemas.isEmpty();
        return new Tarjeta("ventas", "Ventas", alerta ? "ALERTA" : "OK",
                alerta ? (problemas.size() == 1 ? "Algo raro en las ventas" : problemas.size() + " cosas raras en las ventas") : "Normales",
                todo, null);
    }

    private void ventasOnline(LocalDateTime ahora, List<String> problemas, List<String> detalles) {
        long actuales = pagadasOnline(ahora.minusHours(ventanaHoras), ahora);
        long suma = 0;
        for (int s = 1; s <= SEMANAS_DE_REFERENCIA; s++) {
            LocalDateTime hasta = ahora.minusWeeks(s);
            suma += pagadasOnline(hasta.minusHours(ventanaHoras), hasta);
        }
        double normal = (double) suma / SEMANAS_DE_REFERENCIA;
        String dia = ahora.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "AR"));
        String texto = actuales + " venta(s) online en las últimas " + ventanaHoras + " h (lo normal un " + dia
                + " a esta hora: " + redondear(normal) + ")";
        if (actuales == 0 && normal >= minimoEsperado) {
            problemas.add("Ninguna venta online en las últimas " + ventanaHoras + " h, y un " + dia + " a esta hora lo normal son "
                    + redondear(normal) + ": revisar que la tienda y Mercado Pago anden");
        } else {
            detalles.add(texto);
        }
    }

    /** De los checkouts abiertos hace entre 1 y 6 h (ya tuvieron tiempo de pagarse), cuántos se pagaron. */
    private void checkouts(LocalDateTime ahora, List<String> problemas, List<String> detalles) {
        LocalDateTime desde = ahora.minusHours(6);
        LocalDateTime hasta = ahora.minusHours(1);
        long abiertos = compras.countByFormaPagoAndFechaCreacionBetween(FormaPago.MERCADO_PAGO, desde, hasta);
        if (abiertos == 0) return;
        long pagados = pagadasOnline(desde, hasta);
        long porcentaje = Math.round(pagados * 100.0 / abiertos);
        if (abiertos >= minimoCheckouts && porcentaje < conversionMinima) {
            problemas.add("Se pagó sólo el " + porcentaje + " % de los pagos online iniciados (" + pagados + " de " + abiertos
                    + "): algo puede estar fallando al pagar");
        } else {
            detalles.add(pagados + " de " + abiertos + " pagos online iniciados se completaron (" + porcentaje + " %)");
        }
    }

    private void webhook(LocalDateTime ahora, List<String> problemas, List<String> detalles) {
        long webhook = 0;
        long total = 0;
        for (Object[] fila : compras.confirmacionesPorOrigen(ahora.minusHours(24))) {
            long cantidad = ((Number) fila[1]).longValue();
            total += cantidad;
            if (ConfirmacionPago.WEBHOOK.name().equals(fila[0])) webhook = cantidad;
        }
        if (total == 0) return;
        long otros = total - webhook;
        if (total >= minimoConfirmaciones && webhook == 0) {
            problemas.add("Ninguno de los " + total + " pagos de las últimas 24 h llegó por el aviso de Mercado Pago (webhook): "
                    + "se confirmaron por el respaldo. Revisar MP_NOTIFICATION_URL y el webhook en el panel de Mercado Pago");
        } else if (total >= minimoConfirmaciones && otros * 2 > total) {
            problemas.add(otros + " de " + total + " pagos de las últimas 24 h se confirmaron por el respaldo y no por el webhook: "
                    + "el aviso de Mercado Pago está fallando seguido");
        } else {
            detalles.add(webhook + " de " + total + " pagos de las últimas 24 h confirmados por el webhook de Mercado Pago");
        }
    }

    private void puerta(LocalDateTime ahora, List<String> problemas, List<String> detalles) {
        LocalDateTime haceUnaHora = ahora.minusHours(1);
        long validaciones = compras.countByEstadoAndFechaValidacionBetween(EstadoCompra.USADO, haceUnaHora, ahora);
        long ventasPuerta = compras.countByEstadoAndFechaCreacionBetween(EstadoCompra.VENDIDO_EN_PUERTA, haceUnaHora, ahora);
        if (validaciones == 0 && ventasPuerta == 0) return;
        if (validaciones >= movimientoPuerta && ventasPuerta == 0) {
            problemas.add(validaciones + " anticipadas validadas en la última hora y ninguna venta en la boletería: "
                    + "¿el POS está sin conexión o sin subir las ventas? (ver Terminales del POS)");
        } else if (ventasPuerta >= movimientoPuerta && validaciones == 0) {
            problemas.add(ventasPuerta + " ventas en la boletería en la última hora y ninguna anticipada validada: "
                    + "¿falla la validación en la puerta?");
        } else {
            detalles.add("Última hora en la puerta: " + ventasPuerta + " venta(s) en boletería, " + validaciones + " anticipada(s) validada(s)");
        }
    }

    private long pagadasOnline(LocalDateTime desde, LocalDateTime hasta) {
        return compras.countByFormaPagoAndEstadoInAndFechaCreacionBetween(FormaPago.MERCADO_PAGO, PAGADAS, desde, hasta);
    }

    private static String redondear(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.format(new Locale("es", "AR"), "%.1f", valor);
    }
}
