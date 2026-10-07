package org.example.laserranitaentradas.monitoreo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Engancha el logging de la app al registro de errores: cada log.error (y cada warn de los loggers
 * "alertas.*", que son avisos explícitos aunque no sean una excepción) va a la cola de
 * IncidenteService. Nunca toca la base desde acá: sólo encola.
 */
class RegistroIncidentesAppender extends AppenderBase<ILoggingEvent> {

    /** Loggers de la propia base / pool: si la base falla, registrarlo en la base no tiene sentido. */
    private static final String[] IGNORADOS = {"org.hibernate", "com.zaxxer", "org.postgresql", "org.springframework.orm",
            "org.springframework.jdbc", RegistroIncidentesAppender.class.getName(), IncidenteService.class.getName()};

    private final IncidenteService servicio;

    RegistroIncidentesAppender(IncidenteService servicio) {
        this.servicio = servicio;
    }

    @Override
    protected void append(ILoggingEvent e) {
        if (Boolean.TRUE.equals(IncidenteService.GUARDANDO.get())) return;
        String logger = e.getLoggerName();
        boolean esAlerta = logger != null && logger.startsWith("alertas.");
        if (!e.getLevel().isGreaterOrEqual(Level.ERROR) && !(esAlerta && e.getLevel().isGreaterOrEqual(Level.WARN))) return;
        if (logger != null) {
            for (String ignorado : IGNORADOS) {
                if (logger.startsWith(ignorado)) return;
            }
        }
        IThrowableProxy error = e.getThrowableProxy();
        IThrowableProxy causa = causaRaiz(error);
        Map<String, String> mdc = e.getMDCPropertyMap();
        servicio.encolar(new IncidenteService.Evento(
                IncidenteService.area(logger),
                e.getLevel().isGreaterOrEqual(Level.ERROR) ? "ERROR" : "WARN",
                logger,
                conCausa(e.getFormattedMessage(), causa),
                causa != null ? causa.getClassName() : null,
                error != null ? ThrowableProxyUtil.asString(error) : null,
                mdc.get("codigoError"),
                mdc.get("usuario"),
                mdc.get("request"),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(e.getTimeStamp()), AuditoriaService.ZONA).withNano(0)));
    }

    /** La excepción de más abajo: la que dice qué pasó de verdad (ej. "Connection refused"). */
    static IThrowableProxy causaRaiz(IThrowableProxy error) {
        IThrowableProxy actual = error;
        for (int i = 0; actual != null && actual.getCause() != null && i < 20; i++) actual = actual.getCause();
        return actual;
    }

    /**
     * "Error no controlado atendiendo la request" no dice nada en la lista: se le suma la causa, así
     * con muchos errores juntos se ve de un vistazo cuáles son por lo mismo (ej. la base caída).
     */
    static String conCausa(String mensaje, IThrowableProxy causa) {
        if (causa == null) return mensaje;
        String clase = causa.getClassName().substring(causa.getClassName().lastIndexOf('.') + 1);
        String detalle = causa.getMessage() == null ? clase : clase + ": " + causa.getMessage();
        if (detalle.length() > 200) detalle = detalle.substring(0, 200) + "…";
        if (mensaje != null && mensaje.contains(detalle)) return mensaje;
        return (mensaje == null || mensaje.isBlank() ? "" : mensaje + " — ") + "causa: " + detalle;
    }
}
