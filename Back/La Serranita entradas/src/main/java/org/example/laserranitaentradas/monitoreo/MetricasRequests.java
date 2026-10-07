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
 * Cuánto tarda cada endpoint de la API (Sistema > Rendimiento): cantidad, promedio, máximo, lentas
 * y errores 5xx por endpoint desde que arrancó el servidor, más las últimas requests lentas. En
 * memoria a propósito: es "cómo viene ahora", se pierde al reiniciar y no le suma carga a la base.
 */
@Component
public class MetricasRequests {

    private static final int MAX_LENTAS_RECIENTES = 200;

    public record Endpoint(String endpoint, long cantidad, long promedioMs, long maxMs, long lentas, long errores) {}

    public record RequestLenta(LocalDateTime momento, String endpoint, long ms, int estado, String usuario, String requestId) {}

    public record Resumen(LocalDateTime desde, long umbralMs, List<Endpoint> endpoints, List<RequestLenta> lentasRecientes) {}

    private static final class Acumulado {
        final LongAdder cantidad = new LongAdder();
        final LongAdder totalMs = new LongAdder();
        final AtomicLong maxMs = new AtomicLong();
        final LongAdder lentas = new LongAdder();
        final LongAdder errores = new LongAdder();
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
            endpoints.add(new Endpoint(nombre, cantidad, cantidad == 0 ? 0 : a.totalMs.sum() / cantidad, a.maxMs.get(),
                    a.lentas.sum(), a.errores.sum()));
        });
        endpoints.sort(Comparator.comparingLong(Endpoint::promedioMs).reversed());
        List<RequestLenta> recientes;
        synchronized (lentas) {
            recientes = List.copyOf(lentas);
        }
        return new Resumen(desde, umbralMs, endpoints, recientes);
    }
}
