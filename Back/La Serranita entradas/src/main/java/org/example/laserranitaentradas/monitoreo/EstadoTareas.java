package org.example.laserranitaentradas.monitoreo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.TaskExecutionOutcome;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Las tareas programadas (@Scheduled) de la app: cuándo corrió cada una por última vez, si terminó
 * bien y cuándo le toca. Spring ya lleva esa cuenta (ScheduledTask#getLastExecutionOutcome); acá
 * sólo se lee y se decide si alguna dejó de andar. Así una tarea nueva aparece sola, sin anotarla.
 *
 * Una tarea "trabada" no se nota de otra forma: si la emisión de facturas se cuelga esperando a
 * ARCA, no tira error, simplemente deja de correr — y con ella todos los reintentos.
 */
@Component
public class EstadoTareas {

    /** Si una tarea no arrancó a la hora que le tocaba pasado este margen, está atrasada (pool lleno o colgado). */
    private static final Duration MARGEN_ATRASO = Duration.ofMinutes(3);

    /** Nombre legible por "Clase.metodo"; las que no están se muestran así, crudas. */
    private static final Map<String, String> NOMBRES = Map.ofEntries(
            Map.entry("EmisionFacturasScheduler.emitirPendientes", "Reintento de facturas pendientes"),
            Map.entry("EmisionFacturasScheduler.controlarNumeracion", "Control nocturno de numeración con ARCA"),
            Map.entry("ExpiracionCheckoutsScheduler.expirarCheckoutsAbandonados", "Vencimiento de pagos online abandonados"),
            Map.entry("CierreAutomaticoCajasEspecialesScheduler.cerrarCajasSinControlAtrasadas", "Cierre automático de cajas sin control"),
            Map.entry("ImpresionService.latido", "Latido a las ticketeras"),
            Map.entry("IncidenteService.volcar", "Registro de errores"),
            Map.entry("IncidenteService.purgar", "Limpieza de errores resueltos"),
            Map.entry("AuditoriaService.purgar", "Limpieza del historial de acciones"),
            Map.entry("AlertasService.revisar", "Alertas por mail"),
            Map.entry("AlertasService.latidoExterno", "Latido al monitor externo"),
            Map.entry("TerminalesService.purgar", "Limpieza de terminales viejas"),
            Map.entry("ErroresClienteController.limpiar", "Límite de errores del navegador"),
            Map.entry("MetricasNegocio.actualizar", "Métricas para Grafana"));

    public enum Situacion { OK, NUNCA, CORRIENDO, TRABADA, ATRASADA, FALLO }

    public record Tarea(String clave, String nombre, Situacion situacion, LocalDateTime ultima, LocalDateTime proxima, String error) {
        public boolean enProblemas() {
            return situacion == Situacion.TRABADA || situacion == Situacion.ATRASADA || situacion == Situacion.FALLO;
        }
    }

    private final List<ScheduledTaskHolder> holders;

    /** Más que esto corriendo sin terminar = trabada (la pasada más larga normal es la de facturas: segundos). */
    @Value("${app.monitoreo.minutos-tarea-trabada:15}")
    private long minutosTrabada;

    public EstadoTareas(List<ScheduledTaskHolder> holders) {
        this.holders = holders;
    }

    public List<Tarea> tareas() {
        Instant ahora = Instant.now();
        List<Tarea> tareas = new ArrayList<>();
        for (ScheduledTaskHolder h : holders) {
            for (ScheduledTask t : h.getScheduledTasks()) tareas.add(tarea(t, ahora));
        }
        tareas.sort(Comparator.comparing(Tarea::nombre));
        return tareas;
    }

    private Tarea tarea(ScheduledTask t, Instant ahora) {
        String clave = clave(t.getTask().toString());
        TaskExecutionOutcome r = t.getTask().getLastExecutionOutcome();
        Instant proxima = t.nextExecution();
        Instant ultima = r.executionTime();
        Situacion situacion;
        String error = null;
        switch (r.status()) {
            case STARTED -> situacion = ultima != null && ultima.plus(Duration.ofMinutes(minutosTrabada)).isBefore(ahora)
                    ? Situacion.TRABADA : Situacion.CORRIENDO;
            case ERROR -> {
                situacion = Situacion.FALLO;
                error = r.throwable() != null ? String.valueOf(r.throwable().getMessage()) : null;
            }
            case SUCCESS -> situacion = Situacion.OK;
            default -> situacion = Situacion.NUNCA;
        }
        // Le tocaba correr hace rato y no arrancó (y no es que está corriendo): el pool de hilos está
        // tomado por otra tarea colgada.
        if (situacion != Situacion.CORRIENDO && situacion != Situacion.TRABADA
                && proxima != null && proxima.plus(MARGEN_ATRASO).isBefore(ahora)) {
            situacion = Situacion.ATRASADA;
        }
        return new Tarea(clave, NOMBRES.getOrDefault(clave, clave), situacion, local(ultima), local(proxima), error);
    }

    /** "org.example...EmisionFacturasScheduler.emitirPendientes" (o con el sufijo de un proxy) → "EmisionFacturasScheduler.emitirPendientes". */
    static String clave(String metodo) {
        String sinProxy = metodo.replaceAll("\\$\\$SpringCGLIB\\$\\$\\d+", "");
        int punto = sinProxy.lastIndexOf('.');
        if (punto <= 0) return sinProxy;
        int anterior = sinProxy.lastIndexOf('.', punto - 1);
        return sinProxy.substring(anterior + 1);
    }

    /** Al segundo más cercano: la próxima corrida de un cron llega como 00:04:59.998 y se mostraba "00:04". */
    private static LocalDateTime local(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i.plusMillis(500), AuditoriaService.ZONA).withNano(0);
    }
}
