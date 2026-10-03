package org.example.serranita.agente;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 *   informa "aceptando trabajos" incluso con la impresora marcada Offline. Y con una USB tampoco
 *   alcanza el estado de Windows: desenchufada sigue diciendo "Normal". Por eso además se mira si
 *   el dispositivo USB detrás de su puerto (USB001...) está presente.
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
        if ("False".equalsIgnoreCase(info[2])) {
            // El puerto USB no tiene el dispositivo: cable desenchufado o impresora apagada.
            return new Estado(false, "apagada o desconectada");
        }
        if (fueraDeLinea) {
            return new Estado(false, "marcada \"Usar impresora sin conexión\" en Windows");
        }
        if (ESTADOS_WINDOWS_OK.contains(estado)) {
            return new Estado(true, null);
        }
        return new Estado(false, DETALLES_WINDOWS.getOrDefault(estado, "con estado '" + estado + "' en Windows"));
    }

    /**
     * Nombre (en minúsculas) → [PrinterStatus, WorkOffline, Presente] de todas las impresoras de
     * Windows, en una sola llamada a PowerShell. Presente es "True"/"False" para las de puerto USB
     * (si el dispositivo está enchufado y prendido) y vacío para el resto. Vacío si falla (sin
     * PowerShell, etc.): en ese caso todas las USB se informan como no disponibles, que es lo prudente.
     */
    static Map<String, String[]> estadosWindows() {
        Map<String, String[]> salida = new HashMap<>();
        try {
            // Cada puerto USBnnn se registra en DeviceClasses (interfaz de impresora USB) con su
            // número y el dispositivo que lo usa; Get-PnpDevice dice si ese dispositivo está presente.
            // Un mismo puerto puede tener entradas viejas de otros aparatos: alcanza con que uno esté.
            String script = "[Console]::OutputEncoding = [Text.Encoding]::UTF8; "
                    + "$puertos = @{}; "
                    + "$clase = 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\DeviceClasses\\{28d78fad-5a12-11d1-ae5b-0000f803a8c2}'; "
                    + "Get-ChildItem -LiteralPath $clase -ErrorAction SilentlyContinue | ForEach-Object { "
                    + "  $inst = (Get-ItemProperty -LiteralPath $_.PSPath -ErrorAction SilentlyContinue).DeviceInstance; "
                    + "  $dp = Get-ItemProperty -LiteralPath ($_.PSPath + '\\#\\Device Parameters') -ErrorAction SilentlyContinue; "
                    + "  if ($inst -and $dp) { $puerto = [string]$dp.'Base Name' + ('{0:D3}' -f [int]$dp.'Port Number'); "
                    + "    if (-not $puertos.ContainsKey($puerto)) { $puertos[$puerto] = @() }; $puertos[$puerto] += $inst } }; "
                    + "$presentes = @{}; "
                    + "$ids = @($puertos.Values | ForEach-Object { $_ }); "
                    + "if ($ids.Count -gt 0) { Get-PnpDevice -InstanceId $ids -ErrorAction SilentlyContinue | "
                    + "  ForEach-Object { $presentes[$_.InstanceId] = $_.Present } }; "
                    + "Get-Printer | ForEach-Object { "
                    + "  $presente = ''; "
                    + "  if ($puertos.ContainsKey($_.PortName)) { "
                    + "    $presente = [string][bool]($puertos[$_.PortName] | Where-Object { $presentes[$_] -eq $true }) }; "
                    + "  $_.Name + [char]9 + $_.PrinterStatus + [char]9 + $_.WorkOffline + [char]9 + $presente }";
            for (String linea : powershell(script, 10)) {
                // -1: los campos del final suelen venir vacíos; sin el -1, split los descarta.
                String[] partes = linea.split("\t", -1);
                if (partes.length >= 2) {
                    salida.put(partes[0].trim().toLowerCase(), new String[]{
                            partes[1].trim(),
                            partes.length >= 3 ? partes[2].trim() : "",
                            partes.length >= 4 ? partes[3].trim() : ""});
                }
            }
            if (salida.isEmpty()) {
                log.warning("Windows no devolvió ninguna impresora (Get-Printer)");
            }
        } catch (Exception e) {
            log.warning("No se pudo consultar el estado de las impresoras de Windows: " + e.getMessage());
        }
        return salida;
    }

    /**
     * Espera a que un trabajo salga de la cola de Windows (= se le pasó a la impresora). Si a los
     * `segundos` sigue ahí, lo borra de la cola y devuelve false: Windows acepta el trabajo aunque la
     * impresora esté desenchufada, y sin esto el agente lo daba por impreso y el ticket salía horas
     * después, cuando alguien la volvía a enchufar.
     *
     * @throws Exception si no se pudo consultar la cola (en ese caso no se sabe qué pasó)
     */
    static boolean esperarQueSalgaDeLaCola(String impresora, String documento, int segundos) throws Exception {
        String script = "$imp = '" + comillas(impresora) + "'; $doc = '" + comillas(documento) + "'; "
                + "$fin = (Get-Date).AddSeconds(" + segundos + "); "
                + "do { $t = @(Get-PrintJob -PrinterName $imp -ErrorAction Stop | Where-Object { $_.DocumentName -eq $doc }); "
                + "  if ($t.Count -eq 0) { 'SALIO'; exit } ; Start-Sleep -Milliseconds 500 } while ((Get-Date) -lt $fin); "
                + "$t | Remove-PrintJob -ErrorAction SilentlyContinue; 'TRABADO'";
        List<String> salida = powershell(script, segundos + 10).stream().map(String::trim).toList();
        if (salida.contains("SALIO")) return true;
        if (salida.contains("TRABADO")) return false;
        throw new IllegalStateException("respuesta inesperada de Windows: " + salida);
    }

    private static String comillas(String texto) {
        return texto.replace("'", "''");
    }

    /**
     * Corre un script de PowerShell y devuelve las líneas que escribió.
     *
     * -EncodedCommand (UTF-16LE en base64) y no -Command: Java en Windows no escapa las comillas
     * internas de un argumento, y con -Command el script le llegaba roto a PowerShell.
     */
    private static List<String> powershell(String script, int segundos) throws Exception {
        // Sin barras de progreso: con la salida redirigida, PowerShell las escribe en CLIXML
        // mezcladas con lo que imprime el script (y cortaba las palabras que se leen acá).
        String completo = "$ProgressPreference = 'SilentlyContinue'; " + script;
        String codificado = Base64.getEncoder().encodeToString(completo.getBytes(StandardCharsets.UTF_16LE));
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", codificado)
                .redirectErrorStream(true)
                .start();
        // Plazo independiente de la lectura: si PowerShell se cuelga con la salida abierta, readLine
        // no vuelve nunca. Esto comparte hilo con la impresión, así que un cuelgue frenaría todos los
        // tickets. Al matar el proceso se cierra la salida y la lectura termina.
        p.onExit().orTimeout(segundos, TimeUnit.SECONDS).exceptionally(e -> {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            return p;
        });
        List<String> lineas = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = r.readLine()) != null) {
                lineas.add(linea);
            }
        }
        p.waitFor(2, TimeUnit.SECONDS);
        return lineas;
    }
}
