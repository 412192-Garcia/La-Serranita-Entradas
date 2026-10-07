package org.example.laserranitaentradas.monitoreo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Primero de todos en cada request de la API (antes de la seguridad):
 * - le pone un id corto (MDC "requestId", sale en cada línea del log y en el header X-Request-Id),
 *   así todas las líneas de una misma request se encuentran juntas. El código EXXXXXX que ve el
 *   usuario en un error 500 es "E" + este id (ver ManejadorGlobalErrores);
 * - mide cuánto tardó y lo suma a MetricasRequests. Las que pasan el umbral quedan además en el log.
 *
 * Las conexiones que quedan abiertas (el stream SSE de los agentes de impresión) no se miden: su
 * "duración" es todo el tiempo que la PC estuvo prendida.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class MetricasRequestsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MetricasRequestsFilter.class);
    public static final String HEADER = "X-Request-Id";

    private final MetricasRequests metricas;

    public MetricasRequestsFilter(MetricasRequests metricas) {
        this.metricas = metricas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    /** Los preflight de CORS (OPTIONS) los contesta el filtro de CORS sin llegar a ningún endpoint: no dicen nada del rendimiento. */
    private static boolean esPreflight(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0x1000000)).toUpperCase();
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                if (!request.isAsyncStarted() && !esPreflight(request)) registrar(request, response, requestId, (System.nanoTime() - inicio) / 1_000_000);
            } finally {
                MDC.remove("requestId");
            }
        }
    }

    private void registrar(HttpServletRequest request, HttpServletResponse response, String requestId, long ms) {
        String patron = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        // Sin patrón = no llegó a ningún endpoint (404, o lo cortó la seguridad): todo junto, así
        // las rutas inventadas de los bots no llenan la lista.
        String endpoint = request.getMethod() + " " + (patron != null ? patron : "(sin endpoint)");
        String usuario = (String) request.getAttribute(AuditoriaFilter.ATRIBUTO_USUARIO);
        if (metricas.registrar(endpoint, ms, response.getStatus(), usuario, requestId)) {
            log.warn("Request lenta: {} tardó {} ms (estado {}{})", endpoint, ms, response.getStatus(),
                    usuario != null ? ", usuario " + usuario : "");
        }
    }
}
