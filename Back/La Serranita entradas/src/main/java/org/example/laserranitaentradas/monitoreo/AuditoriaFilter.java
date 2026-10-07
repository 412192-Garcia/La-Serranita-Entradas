package org.example.laserranitaentradas.monitoreo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Corre en cada request de la API, después de la seguridad (ya se sabe quién es):
 * - deja usuario y request en el MDC, así un error que se registre atendiéndola dice de quién y
 *   de qué era (ver IncidenteService);
 * - si la request modifica algo y la hizo un usuario logueado (o es un inicio de sesión), la
 *   anota en el historial de acciones al terminar, con el resultado.
 */
@Component
public class AuditoriaFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_QUE_MODIFICAN = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String LOGIN = "/api/usuarios/login";
    private static final int MAX_CUERPO = 16_384;
    /** Quién hizo la request, para MetricasRequestsFilter (que corre antes de la seguridad y no lo sabe). */
    static final String ATRIBUTO_USUARIO = AuditoriaFilter.class.getName() + ".usuario";

    private final AuditoriaService auditoriaService;

    public AuditoriaFilter(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UsuarioAutenticado usuario = usuarioActual();
        String uri = request.getRequestURI();
        boolean auditable = METODOS_QUE_MODIFICAN.contains(request.getMethod())
                && (usuario != null || LOGIN.equals(uri));
        HttpServletRequest pedido = auditable ? new ContentCachingRequestWrapper(request, MAX_CUERPO) : request;

        if (usuario != null) {
            MDC.put("usuario", usuario.username());
            request.setAttribute(ATRIBUTO_USUARIO, usuario.username());
        }
        MDC.put("request", request.getMethod() + " " + uri);
        if (auditable) AuditoriaContexto.iniciar();
        try {
            chain.doFilter(pedido, response);
        } finally {
            try {
                if (auditable) auditar(pedido, response, usuario);
            } finally {
                AuditoriaContexto.tomar();
                MDC.remove("usuario");
                MDC.remove("request");
            }
        }
    }

    private void auditar(HttpServletRequest pedido, HttpServletResponse response, UsuarioAutenticado usuario) {
        String patron = (String) pedido.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String clave = pedido.getMethod() + " " + (patron != null ? patron : pedido.getRequestURI());
        AuditoriaContexto.Datos contexto = AuditoriaContexto.tomar();
        if (CatalogoAcciones.EXCLUIDAS.contains(clave)) return;
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) pedido.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        byte[] cuerpo = pedido instanceof ContentCachingRequestWrapper w ? w.getContentAsByteArray() : null;
        auditoriaService.registrar(new AuditoriaService.Pedido(pedido.getMethod(), patron, pedido.getRequestURI(), variables,
                response.getStatus(), usuario != null ? usuario.username() : null,
                usuario != null && usuario.rol() != null ? usuario.rol().name() : null, ip(pedido), cuerpo), contexto);
    }

    private static UsuarioAutenticado usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UsuarioAutenticado u ? u : null;
    }

    /** Detrás de Caddy/nginx la IP real viene en X-Forwarded-For (la primera). */
    private static String ip(HttpServletRequest r) {
        String reenviada = r.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) return reenviada.split(",")[0].trim();
        return r.getRemoteAddr();
    }
}
