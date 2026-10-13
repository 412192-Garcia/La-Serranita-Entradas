package org.example.laserranitaentradas.monitoreo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cuánto tarda cada endpoint de la API (Sistema > Rendimiento): cantidad, promedio, p95, máximo,
 * tiempo total, lentas y errores 4xx/5xx por endpoint desde que arrancó el servidor, más las
 * últimas requests lentas. En
 * memoria a propósito: es "cómo viene ahora", se pierde al reiniciar y no le suma carga a la base.
 */
@Component
public class MetricasRequests {

    private static final int MAX_LENTAS_RECIENTES = 200;

    /**
     * @param p95Ms     el 95 % de las requests tardó esto o menos. Aproximado: sale de un histograma
     *                  por tramos (ver TRAMOS_MS), así no hay que guardar cada duración.
     * @param totalMs   tiempo sumado de todas: lo que ese endpoint le ocupa al servidor
     * @param errores4xx pedidos rechazados (sesión vencida, datos inválidos, 404)
     */
    public record Endpoint(String endpoint, long cantidad, long promedioMs, long p95Ms, long maxMs, long totalMs,
                           long lentas, long errores4xx, long errores) {}

    /** Límites superiores de los tramos del histograma, en ms. El último tramo es "más que eso". */
    static final long[] TRAMOS_MS = {5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000};

    public record RequestLenta(LocalDateTime momento, String endpoint, long ms, int estado, String usuario, String requestId) {}

    public record Resumen(LocalDateTime desde, long umbralMs, List<Endpoint> endpoints, List<RequestLenta> lentasRecientes) {}

    private static final class Acumulado {
        final LongAdder cantidad = new LongAdder();
        final LongAdder totalMs = new LongAdder();
        final AtomicLong maxMs = new AtomicLong();
        final LongAdder lentas = new LongAdder();
        final LongAdder errores4xx = new LongAdder();
        final LongAdder errores = new LongAdder();
        final LongAdder[] tramos = new LongAdder[TRAMOS_MS.length + 1];

        Acumulado() {
            for (int i = 0; i < tramos.length; i++) tramos[i] = new LongAdder();
        }

        /** El límite del tramo donde se llega al 95 %; en el último tramo, el máximo real. */
        long p95(long cantidad) {
            if (cantidad == 0) return 0;
            long objetivo = (long) Math.ceil(cantidad * 0.95);
            long acumulado = 0;
            for (int i = 0; i < TRAMOS_MS.length; i++) {
                acumulado += tramos[i].sum();
                if (acumulado >= objetivo) return Math.min(TRAMOS_MS[i], maxMs.get());
            }
            return maxMs.get();
        }
    }

    private final Map<String, Acumulado> porEndpoint = new ConcurrentHashMap<>();
    private final Deque<RequestLenta> lentas = new ArrayDeque<>();
    private final LocalDateTime desde = LocalDateTime.now(AuditoriaService.ZONA).withNano(0);

    @Value("${app.monitoreo.request-lenta-ms:3000}")
    private long umbralMs;

    public long umbralMs() {
        return umbralMs;
    }

    /** @return true si fue lenta (para que el filtro la deje en el log). */
    boolean registrar(String endpoint, long ms, int estado, String usuario, String requestId) {
        Acumulado a = porEndpoint.computeIfAbsent(endpoint, k -> new Acumulado());
        a.cantidad.increment();
        a.totalMs.add(ms);
        a.maxMs.accumulateAndGet(ms, Math::max);
        if (estado >= 500) a.errores.increment();
        else if (estado >= 400) a.errores4xx.increment();
        int tramo = 0;
        while (tramo < TRAMOS_MS.length && ms > TRAMOS_MS[tramo]) tramo++;
        a.tramos[tramo].increment();
        boolean lenta = ms >= umbralMs;
        if (lenta) {
            a.lentas.increment();
            synchronized (lentas) {
                lentas.addFirst(new RequestLenta(LocalDateTime.now(AuditoriaService.ZONA).withNano(0), endpoint, ms, estado, usuario, requestId));
                while (lentas.size() > MAX_LENTAS_RECIENTES) lentas.removeLast();
            }
        }
        return lenta;
    }

    /** Requests lentas en la última hora (de las últimas que se guardan). */
    public long lentasUltimaHora() {
        LocalDateTime limite = LocalDateTime.now(AuditoriaService.ZONA).minusHours(1);
        synchronized (lentas) {
            return lentas.stream().filter(l -> l.momento().isAfter(limite)).count();
        }
    }

    public Resumen resumen() {
        List<Endpoint> endpoints = new ArrayList<>();
        porEndpoint.forEach((nombre, a) -> {
            long cantidad = a.cantidad.sum();
            long total = a.totalMs.sum();
            endpoints.add(new Endpoint(nombre, cantidad, cantidad == 0 ? 0 : total / cantidad, a.p95(cantidad), a.maxMs.get(),
                    total, a.lentas.sum(), a.errores4xx.sum(), a.errores.sum()));
        });
        endpoints.sort(Comparator.comparingLong(Endpoint::promedioMs).reversed());
        List<RequestLenta> recientes;
        synchronized (lentas) {
            recientes = List.copyOf(lentas);
        }
        return new Resumen(desde, umbralMs, endpoints, recientes);
    }
}
