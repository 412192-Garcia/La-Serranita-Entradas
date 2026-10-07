package org.example.laserranitaentradas.monitoreo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.entity.RegistroAuditoria;
import org.example.laserranitaentradas.repository.RegistroAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Arma y guarda el historial de acciones (ver AuditoriaFilter), y lo busca para Sistema >
 * Historial de acciones.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int MAX_DETALLE = 2000;
    /** Datos que nunca se guardan en el historial, aunque vengan en el pedido. */
    private static final Pattern SENSIBLE = Pattern.compile("(?i).*(pass|contrase|token|secret|clave|foto|cert|key).*");

    private final RegistroAuditoriaRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${app.auditoria.retencion-dias:730}")
    private int retencionDias;

    public AuditoriaService(RegistroAuditoriaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Lo que vio el filtro de una request. */
    record Pedido(String metodo, String patron, String uri, Map<String, String> variables, int estado,
                  String usuario, String rol, String ip, byte[] cuerpo) {}

    void registrar(Pedido p, AuditoriaContexto.Datos contexto) {
        RegistroAuditoria r = armar(p, contexto);
        try {
            repository.save(r);
        } catch (RuntimeException e) {
            // El historial nunca puede romper la operación (que ya terminó): queda en el log.
            log.warn("No se pudo guardar en el historial: {} {} ({})", r.getAccion(), r.getDescripcion(), e.getMessage());
        }
    }

    RegistroAuditoria armar(Pedido p, AuditoriaContexto.Datos contexto) {
        String clave = p.metodo() + " " + (p.patron() != null ? p.patron() : p.uri());
        CatalogoAcciones.Accion accion = CatalogoAcciones.ACCIONES.get(clave);
        boolean ok = p.estado() < 400;
        boolean esLogin = accion != null && "LOGIN".equals(accion.codigo());

        String usuario = p.usuario();
        String codigo;
        String descripcion;
        if (esLogin) {
            usuario = campo(p.cuerpo(), "username");
            codigo = ok ? "LOGIN_OK" : "LOGIN_FALLIDO";
            descripcion = ok ? "Inició sesión" : "Intento de inicio de sesión fallido";
        } else if (accion != null) {
            codigo = accion.codigo();
            descripcion = accion.descripcion();
        } else {
            codigo = "OTRA";
            descripcion = p.metodo() + " " + p.uri();
        }

        String entidadId = primerVariable(p.variables());
        String referencia = contexto != null ? contexto.referencia : null;
        if (!esLogin && accion != null) {
            if (referencia != null) descripcion += " " + referencia;
            else if (entidadId != null && accion.entidad() != null) descripcion += " (ID " + entidadId + ")";
        }
        if (!ok && !esLogin) descripcion = "Intentó: " + descripcion.substring(0, 1).toLowerCase() + descripcion.substring(1);

        RegistroAuditoria r = new RegistroAuditoria();
        r.setFecha(LocalDateTime.now(ZONA).withNano(0));
        r.setUsuario(recortar(usuario, 100));
        r.setRol(p.rol());
        r.setAccion(codigo);
        r.setDescripcion(recortar(descripcion, 300));
        r.setEntidad(accion != null ? accion.entidad() : null);
        r.setEntidadId(recortar(referencia != null ? referencia : entidadId, 60));
        r.setDetalle(detalle(contexto, p.cuerpo(), esLogin));
        r.setResultado(p.estado());
        r.setRuta(recortar(p.metodo() + " " + p.uri(), 250));
        r.setIp(recortar(p.ip(), 60));
        return r;
    }

    /** Antes → después si el servicio lo anotó; si no, un resumen de lo enviado (sin datos sensibles). */
    private String detalle(AuditoriaContexto.Datos contexto, byte[] cuerpo, boolean esLogin) {
        StringBuilder sb = new StringBuilder();
        if (contexto != null) contexto.lineas.forEach(l -> sb.append(l).append('\n'));
        if (sb.isEmpty() && !esLogin) {
            String resumen = resumenCuerpo(cuerpo);
            if (resumen != null) sb.append(resumen);
        }
        String texto = sb.toString().strip();
        return texto.isEmpty() ? null : recortar(texto, MAX_DETALLE);
    }

    String resumenCuerpo(byte[] cuerpo) {
        if (cuerpo == null || cuerpo.length == 0) return null;
        try {
            JsonNode nodo = objectMapper.readTree(new String(cuerpo, StandardCharsets.UTF_8));
            if (nodo == null) return null;
            if (nodo.isArray()) return "[" + nodo.size() + " elementos]";
            if (!nodo.isObject()) return valor(nodo);
            StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> campos = nodo.fields();
            while (campos.hasNext()) {
                Map.Entry<String, JsonNode> c = campos.next();
                if (c.getValue().isNull()) continue;
                sb.append(c.getKey()).append(": ")
                        .append(SENSIBLE.matcher(c.getKey()).matches() ? "(oculto)" : valor(c.getValue()))
                        .append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String valor(JsonNode v) {
        if (v.isArray()) return "[" + v.size() + " elementos]";
        if (v.isObject()) return "{" + v.size() + " datos}";
        return recortar(v.asText(), 120);
    }

    private String campo(byte[] cuerpo, String nombre) {
        if (cuerpo == null || cuerpo.length == 0) return null;
        try {
            JsonNode n = objectMapper.readTree(new String(cuerpo, StandardCharsets.UTF_8)).get(nombre);
            return n == null || n.isNull() ? null : n.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String primerVariable(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return null;
        return variables.values().iterator().next();
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() > max ? texto.substring(0, max - 1) + "…" : texto;
    }

    // ---------- búsqueda ----------

    public Page<RegistroAuditoria> buscar(LocalDate desde, LocalDate hasta, String usuario, String accion, String texto,
                                          int pagina, int tamanio) {
        LocalDate d = desde != null ? desde : LocalDate.now(ZONA).minusDays(7);
        LocalDate h = hasta != null ? hasta : LocalDate.now(ZONA);
        return repository.buscar(d.atStartOfDay(), h.plusDays(1).atStartOfDay(),
                vacioANull(usuario) == null ? null : usuario.trim().toLowerCase(),
                vacioANull(accion),
                vacioANull(texto) == null ? null : "%" + texto.trim().toLowerCase() + "%",
                PageRequest.of(Math.max(0, pagina), Math.min(100, Math.max(1, tamanio))));
    }

    public List<String> acciones() {
        return repository.accionesRegistradas();
    }

    public List<String> usuarios() {
        return repository.usuariosRegistrados();
    }

    /** Logins fallidos de las últimas `horas`, por usuario intentado: [usuario, cantidad]. */
    public List<Object[]> loginsFallidos(int horas) {
        return repository.loginsFallidosPorUsuario(LocalDateTime.now(ZONA).minusHours(horas));
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void purgar() {
        int borrados = repository.borrarAntesDe(LocalDateTime.now(ZONA).minusDays(retencionDias));
        if (borrados > 0) log.info("Historial de acciones: {} registros de más de {} días borrados", borrados, retencionDias);
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
