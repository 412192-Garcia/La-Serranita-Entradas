package org.example.serranita.agente;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Revisa si cada ticketera responde, para que la tablet pueda distinguir "la PC de la entrada está
 * apagada" (el agente no está conectado) de "la ticketera está apagada o desconectada" (el agente
 * está, pero la impresora no) y avisarle al cajero antes de cobrar, no después.
 *
 * - Red: se abre y cierra una conexión al puerto RAW. Si atiende, está prendida y en la red.
 * - Windows (USB): se le pregunta a Windows el estado de la impresora. Java no sirve para esto:
 *   informa "aceptando trabajos" incluso con la impresora marcada Offline.
 */
final class MonitorImpresoras {

    private static final Logger log = Logger.getLogger("agente");

    record Estado(boolean disponible, String detalle) {}

    /** Estados de Windows que significan "lista para imprimir" (o casi: ocupada, calentando...). */
    private static final Set<String> ESTADOS_WINDOWS_OK = Set.of(
            "Normal", "Printing", "Busy", "IOActive", "Processing", "Waiting", "WarmingUp",
            "PowerSave", "Initialization", "TonerLow");

    private static final Map<String, String> DETALLES_WINDOWS = Map.of(
            "Offline", "apagada o desconectada",
            "PaperOut", "sin papel",
            "PaperJam", "con el papel trabado",
            "PaperProblem", "con un problema de papel",
            "DoorOpen", "con la tapa abierta",
            "Paused", "pausada en Windows",
            "Error", "con un error",
            "NotAvailable", "apagada o desconectada",
            "UserIntervention", "esperando que alguien la revise");

    private MonitorImpresoras() {}

    static Map<String, Estado> revisar(Configuracion config) {
        boolean hayWindows = config.impresoras.values().stream().anyMatch(d -> d instanceof Configuracion.Windows);
        Map<String, String[]> windows = hayWindows ? estadosWindows() : Map.of();

        Map<String, Estado> estados = new LinkedHashMap<>();
        config.impresoras.forEach((nombre, destino) -> {
            if (destino instanceof Configuracion.Red red) {
                estados.put(nombre, revisarRed(red));
            } else if (destino instanceof Configuracion.Windows w) {
                estados.put(nombre, revisarWindows(w, windows));
            }
        });
        return estados;
    }

    private static Estado revisarRed(Configuracion.Red red) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(red.host(), red.puerto()), 2_000);
            return new Estado(true, null);
        } catch (Exception e) {
            // Sin la IP: lo lee el cajero en la tablet. La IP queda en el log del agente.
            return new Estado(false, "apagada o desconectada");
        }
    }

    private static Estado revisarWindows(Configuracion.Windows w, Map<String, String[]> windows) {
        String[] info = windows.get(w.impresora().toLowerCase());
        if (info == null) {
            return new Estado(false, "no está instalada en Windows con el nombre '" + w.impresora() + "'");
        }
        String estado = info[0];
        boolean fueraDeLinea = "True".equalsIgnoreCase(info[1]);
        if (fueraDeLinea) {
            return new Estado(false, "marcada \"Usar impresora sin conexión\" en Windows");
        }
        if (ESTADOS_WINDOWS_OK.contains(estado)) {
            return new Estado(true, null);
        }
        return new Estado(false, DETALLES_WINDOWS.getOrDefault(estado, "con estado '" + estado + "' en Windows"));
    }

    /**
     * Nombre (en minúsculas) → [PrinterStatus, WorkOffline] de todas las impresoras de Windows, en
     * una sola llamada a PowerShell. Vacío si falla (sin PowerShell, etc.): en ese caso todas las
     * USB se informan como no disponibles, que es lo prudente.
     */
    private static Map<String, String[]> estadosWindows() {
        Map<String, String[]> salida = new HashMap<>();
        try {
            // -EncodedCommand (UTF-16LE en base64) y no -Command: Java en Windows no escapa las
            // comillas internas de un argumento, y con -Command el script le llegaba roto a
            // PowerShell (no devolvía ninguna impresora).
            String script = "[Console]::OutputEncoding = [Text.Encoding]::UTF8; "
                    + "Get-Printer | ForEach-Object { $_.Name + [char]9 + $_.PrinterStatus + [char]9 + $_.WorkOffline }";
            String codificado = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", codificado)
                    .redirectErrorStream(true)
                    .start();
            // Plazo independiente de la lectura: si Get-Printer se cuelga con la salida abierta,
            // readLine no vuelve nunca y el waitFor de abajo no llegaría a correr. Esta revisión
            // comparte hilo con la impresión, así que un cuelgue frenaría todos los tickets. Al matar
            // el proceso se cierra la salida y la lectura termina.
            p.onExit().orTimeout(10, TimeUnit.SECONDS).exceptionally(e -> {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                return p;
            });
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = r.readLine()) != null) {
                    // -1: WorkOffline suele venir vacío y la línea termina en tab; sin el -1, split
                    // descarta ese campo vacío del final y la línea parecería incompleta.
                    String[] partes = linea.split("\t", -1);
                    if (partes.length >= 2) {
                        salida.put(partes[0].trim().toLowerCase(),
                                new String[]{partes[1].trim(), partes.length >= 3 ? partes[2].trim() : ""});
                    }
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
            if (salida.isEmpty()) {
                log.warning("Windows no devolvió ninguna impresora (Get-Printer)");
            }
        } catch (Exception e) {
            log.warning("No se pudo consultar el estado de las impresoras de Windows: " + e.getMessage());
        }
        return salida;
    }
}
