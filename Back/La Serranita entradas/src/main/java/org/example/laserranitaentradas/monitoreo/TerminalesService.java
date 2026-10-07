package org.example.laserranitaentradas.monitoreo;

import org.example.laserranitaentradas.model.entity.TerminalPos;
import org.example.laserranitaentradas.repository.TerminalPosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Latidos de las terminales del POS (ver TerminalPos) y qué se considera un problema:
 * - una terminal que se quedó sin conexión con operaciones sin subir (ventas que el servidor todavía
 *   no tiene: si esa PC se borra o se rompe, se pierden);
 * - una cola que no se vacía aunque la terminal sí tiene conexión (algo la traba).
 */
@Service
public class TerminalesService {

    private static final Logger log = LoggerFactory.getLogger(TerminalesService.class);

    /** Lo que manda el POS. pendienteDesde: hora local de la terminal, sin zona. */
    public record Latido(String terminalId, int pendientes, int conError, LocalDateTime pendienteDesde, String navegador) {}

    public record Problema(TerminalPos terminal, String descripcion) {}

    private final TerminalPosRepository repository;

    /** Sin noticias de una terminal con pendientes en este tiempo = se quedó sin conexión. */
    @Value("${app.monitoreo.minutos-terminal-sin-latido:10}")
    private long minutosSinLatido;

    /** Una operación pendiente más vieja que esto, con la terminal conectada = la cola está trabada. */
    @Value("${app.monitoreo.minutos-cola-trabada:15}")
    private long minutosColaTrabada;

    public TerminalesService(TerminalPosRepository repository) {
        this.repository = repository;
    }

    public void registrar(Latido l, String usuario) {
        if (l.terminalId() == null || l.terminalId().isBlank() || l.terminalId().length() > 64) {
            throw new IllegalArgumentException("Terminal inválida");
        }
        TerminalPos t = repository.findById(l.terminalId()).orElseGet(() -> {
            TerminalPos nueva = new TerminalPos();
            nueva.setId(l.terminalId());
            return nueva;
        });
        t.setUsuario(usuario);
        t.setUltimaVez(ahora());
        t.setPendientes(Math.max(0, l.pendientes()));
        t.setConError(Math.max(0, l.conError()));
        t.setPendienteDesde(l.pendientes() > 0 ? l.pendienteDesde() : null);
        if (l.navegador() != null) t.setNavegador(l.navegador().length() > 300 ? l.navegador().substring(0, 300) : l.navegador());
        repository.save(t);
    }

    /** Las que se vieron en las últimas 24 h, más las que quedaron con algo sin subir. */
    public List<TerminalPos> recientes() {
        return repository.recientesOConPendientes(ahora().minusHours(24));
    }

    public Problema problema(TerminalPos t) {
        LocalDateTime ahora = ahora();
        if (t.getPendientes() > 0 && t.getUltimaVez().isBefore(ahora.minusMinutes(minutosSinLatido))) {
            return new Problema(t, "sin conexión hace " + duracion(Duration.between(t.getUltimaVez(), ahora)) + " con "
                    + t.getPendientes() + " operación(es) sin subir");
        }
        if (t.getPendientes() > 0 && t.getPendienteDesde() != null && t.getPendienteDesde().isBefore(ahora.minusMinutes(minutosColaTrabada))) {
            return new Problema(t, t.getPendientes() + " operación(es) sin subir desde hace "
                    + duracion(Duration.between(t.getPendienteDesde(), ahora)) + " aunque tiene conexión");
        }
        return null;
    }

    public static String nombre(TerminalPos t) {
        return "Terminal " + t.getId().substring(0, Math.min(6, t.getId().length())) + (t.getUsuario() != null ? " (" + t.getUsuario() + ")" : "");
    }

    static String duracion(Duration d) {
        if (d.toDays() > 0) return d.toDays() + " d " + d.toHoursPart() + " h";
        if (d.toHours() > 0) return d.toHours() + " h " + d.toMinutesPart() + " min";
        return Math.max(1, d.toMinutes()) + " min";
    }

    /** Terminales que no se usan más: sin pendientes a los 30 días; con pendientes, a los 90 (ya no se van a subir). */
    @Scheduled(cron = "0 25 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void purgar() {
        int borradas = repository.borrarSinPendientesAntesDe(ahora().minusDays(30)) + repository.borrarAntesDe(ahora().minusDays(90));
        if (borradas > 0) log.info("Terminales del POS: {} sin uso borradas", borradas);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(AuditoriaService.ZONA).withNano(0);
    }
}
