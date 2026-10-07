package org.example.laserranitaentradas.monitoreo;

import ch.qos.logback.classic.LoggerContext;
import org.example.laserranitaentradas.model.entity.Incidente;
import org.example.laserranitaentradas.repository.IncidenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Errores del servidor, para Sistema > Errores. Los captura RegistroIncidentesAppender (todo
 * log.error de la app, incluidos los procesos en segundo plano) y se guardan cada pocos segundos
 * desde una cola: así un log nunca espera a la base, y si la base es la que falla no se arma un
 * círculo de errores que generan errores.
 *
 * Los repetidos (misma firma, sin resolver) se agrupan: suma la cantidad y actualiza la última vez.
 */
@Service
public class IncidenteService {

    private static final Logger log = LoggerFactory.getLogger(IncidenteService.class);
    private static final int MAX_DETALLE = 8_000;

    /** Mientras se guardan, los logs de este hilo no se registran (evita el círculo). */
    static final ThreadLocal<Boolean> GUARDANDO = ThreadLocal.withInitial(() -> false);

    record Evento(String area, String nivel, String origen, String mensaje, String excepcion, String detalle,
                  String codigo, String usuario, String request, LocalDateTime momento) {}

    private final BlockingDeque<Evento> cola = new LinkedBlockingDeque<>(1_000);
    /** Errores que no entraron en la cola durante una avalancha; se avisa con un registro aparte. */
    private final AtomicLong descartados = new AtomicLong();
    private volatile LocalDateTime primerDescarte;
    private final IncidenteRepository repository;
    private final TransactionTemplate tx;

    public IncidenteService(IncidenteRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Se engancha al logging recién con la app levantada (antes no hay base para guardar). */
    @EventListener(ApplicationReadyEvent.class)
    public void instalar() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext contexto)) return;
        RegistroIncidentesAppender appender = new RegistroIncidentesAppender(this);
        appender.setContext(contexto);
        appender.setName("incidentes");
        appender.start();
        contexto.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    /**
     * Si la cola está llena (una avalancha de errores, o la base caída un buen rato) se descarta y se
     * cuenta: el log del contenedor lo tiene igual, y al volver se registra cuántos se perdieron.
     */
    void encolar(Evento evento) {
        if (!cola.offer(evento)) contarDescarte(evento.momento());
    }

    private void contarDescarte(LocalDateTime momento) {
        if (descartados.getAndIncrement() == 0) primerDescarte = momento;
    }

    @Scheduled(fixedDelay = 3_000, initialDelay = 3_000)
    public void volcar() {
        if (cola.isEmpty() && descartados.get() == 0) return;
        List<Evento> eventos = new ArrayList<>();
        cola.drainTo(eventos, 200);
        GUARDANDO.set(true);
        try {
            for (int i = 0; i < eventos.size(); i++) {
                Evento e = eventos.get(i);
                try {
                    tx.executeWithoutResult(s -> guardar(e));
                } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException ex) {
                    // La base no responde: cada intento esperaría el timeout de conexión, así que no se
                    // sigue probando. Lo que falta vuelve al frente de la cola para la próxima vuelta.
                    devolver(eventos.subList(i, eventos.size()));
                    log.warn("No se pudieron registrar errores del sistema (se reintenta): {}", ex.getMessage());
                    return;
                } catch (RuntimeException ex) {
                    log.warn("No se pudo registrar un error del sistema: {}", ex.getMessage());
                }
            }
            registrarDescartados();
        } finally {
            GUARDANDO.set(false);
        }
    }

    private void devolver(List<Evento> pendientes) {
        for (int i = pendientes.size() - 1; i >= 0; i--) {
            if (!cola.offerFirst(pendientes.get(i))) contarDescarte(pendientes.get(i).momento());
        }
    }

    private void registrarDescartados() {
        long cantidad = descartados.getAndSet(0);
        if (cantidad == 0) return;
        LocalDateTime ahora = LocalDateTime.now(AuditoriaService.ZONA).withNano(0);
        Evento aviso = new Evento("SISTEMA", "ERROR", IncidenteService.class.getName(),
                "Llegaron más errores de los que se podían registrar: se descartaron " + cantidad
                        + ". Los de esta lista son una muestra; el detalle completo está en el log del servidor.",
                "Descarte", "Errores descartados entre " + primerDescarte + " y " + ahora + ": " + cantidad,
                null, null, null, primerDescarte == null ? ahora : primerDescarte);
        try {
            tx.executeWithoutResult(s -> guardar(aviso));
        } catch (RuntimeException ex) {
            descartados.addAndGet(cantidad);
        }
    }

    /**
     * Un error de JavaScript que mandó el navegador (ver ErroresClienteController). Área FRONT; se
     * agrupa como los del servidor (mismo mensaje en la misma pantalla = el mismo error).
     *
     * @param pantalla ruta de la app donde pasó ("/pos"), sin query string
     */
    public void registrarDelNavegador(String mensaje, String stack, String pantalla, String navegador, String usuario) {
        String detalle = (stack == null || stack.isBlank() ? "(sin stack)" : stack)
                + (navegador != null ? "\n\nNavegador: " + navegador : "");
        encolar(new Evento("FRONT", "ERROR", "navegador", recortar(mensaje, 1000), null, detalle, null, usuario,
                pantalla != null ? "Pantalla " + recortar(pantalla, 200) : null, LocalDateTime.now(AuditoriaService.ZONA).withNano(0)));
    }

    int enCola() {
        return cola.size();
    }

    void guardar(Evento e) {
        String firma = firma(e);
        Incidente i = repository.findFirstByFirmaAndResueltoFalseOrderByIdDesc(firma).orElse(null);
        if (i == null) {
            i = new Incidente();
            i.setFirma(firma);
            i.setArea(e.area());
            i.setNivel(e.nivel());
            i.setOrigen(recortar(e.origen(), 200));
            i.setPrimeraVez(e.momento());
            i.setCantidad(0);
        }
        i.setCantidad(i.getCantidad() + 1);
        i.setUltimaVez(e.momento());
        i.setMensaje(recortar(e.mensaje(), 1000));
        i.setDetalle(recortar(e.detalle(), MAX_DETALLE));
        if (e.codigo() != null) i.setCodigo(e.codigo());
        if (e.usuario() != null) i.setUsuario(recortar(e.usuario(), 100));
        if (e.request() != null) i.setRequest(recortar(e.request(), 300));
        repository.save(i);
    }

    /** "El mismo error": mismo origen, mismo mensaje (sin números, ids ni montos), misma excepción y misma ruta. */
    static String firma(Evento e) {
        String base = e.area() + "|" + e.origen() + "|" + normalizar(e.mensaje()) + "|" + e.excepcion() + "|" + normalizar(e.request());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return Integer.toHexString(base.hashCode());
        }
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String t = texto.replaceAll("[\\w.+-]+@[\\w.-]+", "@")
                .replaceAll("\\d+", "#");
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    /** Área por el logger que lo registró: decide en qué tarjeta de Estado del sistema cuenta. */
    static String area(String logger) {
        String l = logger == null ? "" : logger.toLowerCase();
        if (l.startsWith("alertas.pagos") || l.contains("mercadopago")) return "PAGOS";
        if (l.contains("factura") || l.contains(".afip")) return "FACTURACION";
        if (l.contains("impresion")) return "IMPRESION";
        if (l.contains("email") || l.contains("mail")) return "MAILS";
        return "SISTEMA";
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() > max ? texto.substring(0, max) : texto;
    }

    // ---------- consulta ----------

    public Page<Incidente> listar(String filtro, String area, int pagina, int tamanio) {
        PageRequest p = PageRequest.of(Math.max(0, pagina), Math.min(100, Math.max(1, tamanio)));
        boolean conArea = area != null && !area.isBlank();
        if ("resueltos".equals(filtro)) {
            return conArea ? repository.findByResueltoAndAreaOrderByUltimaVezDesc(true, area, p) : repository.findByResueltoOrderByUltimaVezDesc(true, p);
        }
        if ("todos".equals(filtro)) {
            return conArea ? repository.findByAreaOrderByUltimaVezDesc(area, p) : repository.findAllByOrderByUltimaVezDesc(p);
        }
        return conArea ? repository.findByResueltoAndAreaOrderByUltimaVezDesc(false, area, p) : repository.findByResueltoOrderByUltimaVezDesc(false, p);
    }

    public Incidente resolver(Long id, String usuario) {
        Incidente i = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Error no encontrado ID: " + id));
        if (i.isResuelto()) return i;
        i.setResuelto(true);
        i.setResueltoPor(usuario);
        i.setResueltoEn(LocalDateTime.now(AuditoriaService.ZONA).withNano(0));
        AuditoriaContexto.referencia("\"" + recortar(i.getMensaje(), 80) + "\"");
        return repository.save(i);
    }

    public long pendientes() {
        return repository.countByResueltoFalse();
    }

    public long pendientes(String area) {
        return repository.countByResueltoFalseAndArea(area);
    }

    public List<Incidente> ultimosPendientes(String area) {
        return area == null ? repository.findTop3ByResueltoFalseOrderByUltimaVezDesc()
                : repository.findTop3ByResueltoFalseAndAreaOrderByUltimaVezDesc(area);
    }

    @Scheduled(cron = "0 20 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void purgar() {
        int borrados = repository.borrarResueltosAntesDe(LocalDateTime.now(AuditoriaService.ZONA).minusDays(90));
        if (borrados > 0) log.info("Errores del sistema: {} resueltos de más de 90 días borrados", borrados);
    }
}
