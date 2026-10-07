package org.example.laserranitaentradas.monitoreo;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Último envío de mail que salió bien y último que falló (con el motivo), para la tarjeta de Mails
 * de Estado del sistema. En memoria: se pierde al reiniciar, y está bien (es "cómo viene ahora").
 */
@Component
public class EstadoMails {

    public record Fallo(LocalDateTime momento, String motivo) {}

    private final AtomicReference<LocalDateTime> ultimoOk = new AtomicReference<>();
    private final AtomicReference<Fallo> ultimoFallo = new AtomicReference<>();

    public void ok() {
        ultimoOk.set(LocalDateTime.now(AuditoriaService.ZONA).withNano(0));
    }

    public void fallo(String motivo) {
        ultimoFallo.set(new Fallo(LocalDateTime.now(AuditoriaService.ZONA).withNano(0), resumir(motivo)));
    }

    /**
     * El mensaje de JavaMail trae la excepción entera, en varias líneas ("Mail server connection
     * failed. Failed messages: org...MailConnectException: Couldn't connect to host..."): para la
     * tarjeta alcanza con lo que dice la última excepción de la primera línea.
     */
    static String resumir(String motivo) {
        if (motivo == null || motivo.isBlank()) return "sin detalle";
        String linea = motivo.strip().lines().findFirst().orElse("").strip();
        int i = linea.lastIndexOf("Exception: ");
        if (i >= 0) linea = linea.substring(i + "Exception: ".length());
        linea = linea.replaceAll("[;,\\s]+$", "");
        return linea.length() > 160 ? linea.substring(0, 160) + "…" : linea;
    }

    public LocalDateTime ultimoOk() {
        return ultimoOk.get();
    }

    public Fallo ultimoFallo() {
        return ultimoFallo.get();
    }
}
