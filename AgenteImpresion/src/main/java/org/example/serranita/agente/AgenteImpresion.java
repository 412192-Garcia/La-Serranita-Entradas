package org.example.serranita.agente;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;

/**
 * Agente de impresión de la PC de la entrada.
 *
 * El parque usa Starlink (sin IP pública), así que el servidor no puede llegar a la ticketera. Al
 * revés sí: este agente abre una conexión SSE hacia el backend, se anuncia con sus impresoras y
 * queda escuchando. Cada ticket que llega (ya armado en ESC/POS) lo manda a la impresora por la
 * red local (puerto 9100) y le confirma al backend si salió.
 *
 * Además revisa cada 10 s si cada ticketera responde y se lo informa al backend: así la tablet
 * distingue "PC de la entrada apagada" de "ticketera apagada" y bloquea "Imprimir" antes de cobrar.
 *
 * Si la conexión se corta (Starlink, reinicio del servidor), reintenta solo con espera creciente.
 * Los tickets ya impresos se recuerdan en impresos.txt: si el backend reenvía uno que no llegó a
 * confirmarse, no sale dos veces.
 */
public class AgenteImpresion {

    private static final Logger log = Logger.getLogger("agente");
    /** El backend manda un latido cada 20 s: 65 s sin nada = conexión muerta, reconectar. */
    private static final int TIMEOUT_LECTURA_MS = 65_000;
    private static final int MAX_IMPRESOS_RECORDADOS = 500;
    private static final long SEGUNDOS_ENTRE_REVISIONES = 10;
    /** Aunque no cambie nada, el estado se reenvía cada 15 s: es la señal de vida del agente. El
     * backend lo da por desconectado ("PC de la entrada apagada") a los 35 s sin noticias. */
    private static final Duration REENVIO_ESTADO = Duration.ofSeconds(15);

    private final Configuracion config;
    private final Path archivoImpresos;
    private final LinkedHashSet<String> impresos = new LinkedHashSet<>();
    /** Un solo hilo para imprimir y para revisar las ticketeras: los tickets salen en el orden en
     * que llegan, la lectura de la conexión no se traba mientras la impresora está ocupada, y una
     * revisión nunca choca con un ticket que se está mandando (hay ticketeras que atienden de a
     * una conexión, y lo darían por apagada). */
    private final ScheduledExecutorService impresora = Executors.newSingleThreadScheduledExecutor();
    private volatile Map<String, MonitorImpresoras.Estado> ultimoEstado = Map.of();
    private volatile Instant ultimoEnvioEstado = Instant.EPOCH;

    public static void main(String[] args) throws Exception {
        Path archivoConfig = args.length > 0 ? Path.of(args[0]).toAbsolutePath() : carpetaDelAgente().resolve("agente.properties");
        Path carpeta = archivoConfig.getParent();
        configurarLog(carpeta);
        Configuracion config = Configuracion.leer(archivoConfig);
        log.info("Agente '" + config.nombre + "' arrancando. Backend: " + config.backendUrl
                + " - Impresoras: " + config.impresoras.keySet());
        new AgenteImpresion(config, carpeta.resolve("impresos.txt")).correr();
    }

    AgenteImpresion(Configuracion config, Path archivoImpresos) throws IOException {
        this.config = config;
        this.archivoImpresos = archivoImpresos;
        if (Files.exists(archivoImpresos)) {
            impresos.addAll(Files.readAllLines(archivoImpresos, StandardCharsets.UTF_8));
        }
        impresora.scheduleWithFixedDelay(() -> revisarImpresoras(false),
                SEGUNDOS_ENTRE_REVISIONES, SEGUNDOS_ENTRE_REVISIONES, TimeUnit.SECONDS);
    }

    /** Revisa las ticketeras y, si cambió algo (o pasó un rato, o recién conectó), le avisa al
     * backend. Corre en el hilo de impresión. */
    private void revisarImpresoras(boolean forzar) {
        try {
            Map<String, MonitorImpresoras.Estado> estados = MonitorImpresoras.revisar(config);
            boolean cambio = !estados.equals(ultimoEstado);
            if (cambio) {
                estados.forEach((nombre, e) -> {
                    MonitorImpresoras.Estado antes = ultimoEstado.get(nombre);
                    if (antes == null || antes.disponible() != e.disponible()) {
                        log.info("Ticketera '" + nombre + "' (" + config.impresoras.get(nombre) + "): "
                                + (e.disponible() ? "lista" : e.detalle()));
                    }
                });
            }
            if (forzar || cambio || Duration.between(ultimoEnvioEstado, Instant.now()).compareTo(REENVIO_ESTADO) > 0) {
                if (enviarEstado(estados)) {
                    ultimoEnvioEstado = Instant.now();
                }
            }
            ultimoEstado = estados;
        } catch (Exception e) {
            log.warning("No se pudieron revisar las ticketeras: " + e.getMessage());
        }
    }

    /** Una línea por ticketera: "nombre<TAB>1|0<TAB>detalle". */
    private boolean enviarEstado(Map<String, MonitorImpresoras.Estado> estados) {
        StringBuilder cuerpo = new StringBuilder();
        estados.forEach((nombre, e) -> cuerpo.append(nombre).append('\t').append(e.disponible() ? '1' : '0')
                .append('\t').append(e.detalle() == null ? "" : e.detalle()).append('\n'));
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(config.backendUrl + "/impresion/agente/estado?agente="
                    + codificar(config.nombre)).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("X-Agente-Token", config.token);
            c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) {
                out.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
            }
            int codigo = c.getResponseCode();
            c.disconnect();
            return codigo == 200;
        } catch (IOException e) {
            // Sin conexión con el backend: no pasa nada, al reconectar se reenvía.
            return false;
        }
    }

    void correr() throws InterruptedException {
        long esperaSeg = 2;
        while (true) {
            Instant inicio = Instant.now();
            try {
                conectarYEscuchar();
            } catch (TokenInvalidoException e) {
                log.severe("El backend rechazó el token del agente. Revisar agente.token en agente.properties.");
                esperaSeg = 300;
            } catch (Exception e) {
                log.warning("Conexión con el backend perdida: " + e.getMessage());
            }
            // Si la conexión duró un rato, el corte fue pasajero: reconectar rápido. Si se cae
            // enseguida (backend caído, sin internet), espaciar los intentos hasta 1 minuto.
            if (Duration.between(inicio, Instant.now()).toSeconds() > 60) esperaSeg = 2;
            log.info("Reintentando en " + esperaSeg + " s");
            Thread.sleep(esperaSeg * 1000);
            esperaSeg = Math.min(esperaSeg * 2, 60);
        }
    }

    private void conectarYEscuchar() throws IOException, TokenInvalidoException {
        String url = config.backendUrl + "/impresion/agente/conectar"
                + "?agente=" + codificar(config.nombre)
                + "&impresoras=" + codificar(String.join(",", config.impresoras.keySet()));
        HttpURLConnection conexion = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conexion.setRequestProperty("X-Agente-Token", config.token);
        conexion.setRequestProperty("Accept", "text/event-stream");
        conexion.setConnectTimeout(10_000);
        conexion.setReadTimeout(TIMEOUT_LECTURA_MS);

        int codigo = conexion.getResponseCode();
        if (codigo == 401) throw new TokenInvalidoException();
        if (codigo != 200) throw new IOException("El backend respondió " + codigo);
        log.info("Conectado al backend");

        try (BufferedReader lector = new BufferedReader(new InputStreamReader(conexion.getInputStream(), StandardCharsets.UTF_8))) {
            String evento = null;
            StringBuilder datos = new StringBuilder();
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isEmpty()) {
                    if (evento != null || datos.length() > 0) procesarEvento(evento, datos.toString());
                    evento = null;
                    datos.setLength(0);
                } else if (linea.startsWith(":")) {
                    // Latido del backend: sólo mantiene viva la conexión.
                } else if (linea.startsWith("event:")) {
                    evento = linea.substring(6).trim();
                } else if (linea.startsWith("data:")) {
                    if (datos.length() > 0) datos.append('\n');
                    String valor = linea.substring(5);
                    datos.append(valor.startsWith(" ") ? valor.substring(1) : valor);
                }
            }
        } finally {
            conexion.disconnect();
        }
        throw new IOException("el backend cerró la conexión");
    }

    private void procesarEvento(String evento, String datos) {
        if ("conectado".equals(evento)) {
            // Recién conectado (o reconectado): el backend todavía no sabe cómo están las ticketeras.
            impresora.submit(() -> revisarImpresoras(true));
            return;
        }
        if (!"trabajo".equals(evento)) return;
        // Formato: "id;impresora;ticket en base64".
        String[] partes = datos.split(";", 3);
        if (partes.length != 3) {
            log.warning("Trabajo con formato inválido, se ignora");
            return;
        }
        String id = partes[0];
        String nombreImpresora = partes[1];
        byte[] ticket = Base64.getDecoder().decode(partes[2]);
        impresora.submit(() -> imprimir(id, nombreImpresora, ticket));
    }

    private void imprimir(String id, String nombreImpresora, byte[] ticket) {
        if (impresos.contains(id)) {
            // Ya salió, pero el backend no se enteró (se cortó antes de la confirmación).
            log.info("Trabajo " + id + " ya impreso antes: sólo se reconfirma");
            confirmar(id, true, null);
            return;
        }
        Configuracion.Destino destino = config.impresoras.get(nombreImpresora);
        if (destino == null) {
            confirmar(id, false, "El agente no conoce la impresora '" + nombreImpresora + "'");
            return;
        }
        try {
            if (destino instanceof Configuracion.Red red) {
                imprimirPorRed(red, ticket);
            } else if (destino instanceof Configuracion.Windows windows) {
                imprimirPorWindows(windows, ticket);
            }
            recordarImpreso(id);
            log.info("Trabajo " + id + " impreso en '" + nombreImpresora + "' (" + destino + ", " + ticket.length + " bytes)");
            confirmar(id, true, null);
        } catch (Exception e) {
            log.warning("No se pudo imprimir el trabajo " + id + " en " + destino + ": " + e.getMessage());
            // Lo lee el cajero en la tablet: algo que entienda, no "Connection refused".
            String motivo = destino instanceof Configuracion.Red
                    ? "la ticketera está apagada o desconectada"
                    : e.getMessage();
            confirmar(id, false, motivo);
        }
    }

    /** Ticketera de red: los bytes ESC/POS directo al puerto RAW (9100). */
    private static void imprimirPorRed(Configuracion.Red red, byte[] ticket) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(red.host(), red.puerto()), 5_000);
            socket.setSoTimeout(15_000);
            OutputStream salida = socket.getOutputStream();
            salida.write(ticket);
            salida.flush();
        }
    }

    /**
     * Ticketera instalada en Windows (USB o compartida): el ticket va a la cola de impresión de
     * Windows. Con el tipo AUTOSENSE de bytes, Java se los pasa al spooler tal cual (RAW), sin
     * convertirlos: los comandos ESC/POS (negrita, QR, corte) llegan intactos a la impresora,
     * igual que por red. Funciona con el driver de la marca o con "Generic / Text Only".
     */
    private static void imprimirPorWindows(Configuracion.Windows windows, byte[] ticket) throws Exception {
        PrintService servicio = null;
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (s.getName().equalsIgnoreCase(windows.impresora())) {
                servicio = s;
                break;
            }
        }
        if (servicio == null) {
            throw new IOException("Windows no tiene una impresora llamada '" + windows.impresora() + "'");
        }
        DocPrintJob trabajo = servicio.createPrintJob();
        trabajo.print(new SimpleDoc(ticket, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), new HashPrintRequestAttributeSet());
    }

    private void confirmar(String id, boolean ok, String error) {
        String url = config.backendUrl + "/impresion/agente/trabajos/" + codificar(id) + "/resultado?ok=" + ok
                + (error != null ? "&error=" + codificar(error) : "");
        for (int intento = 1; intento <= 3; intento++) {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("X-Agente-Token", config.token);
                c.setConnectTimeout(10_000);
                c.setReadTimeout(15_000);
                c.setDoOutput(true);
                c.getOutputStream().close();
                int codigo = c.getResponseCode();
                c.disconnect();
                if (codigo == 200) return;
                log.warning("El backend respondió " + codigo + " al confirmar el trabajo " + id);
            } catch (IOException e) {
                log.warning("No se pudo confirmar el trabajo " + id + " (intento " + intento + "): " + e.getMessage());
            }
            try {
                Thread.sleep(2_000L * intento);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        // Si no se pudo confirmar, el backend lo reenvía al reconectar y impresos.txt evita que
        // salga dos veces.
    }

    private synchronized void recordarImpreso(String id) {
        impresos.add(id);
        while (impresos.size() > MAX_IMPRESOS_RECORDADOS) {
            impresos.remove(impresos.iterator().next());
        }
        try {
            Files.write(archivoImpresos, impresos, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warning("No se pudo guardar impresos.txt: " + e.getMessage());
        }
    }

    /** Empaquetado con jpackage, la carpeta del .exe (ahí va agente.properties); si no, la carpeta
     * desde donde se lo corre. Así funciona igual arrancado por la tarea programada de Windows,
     * que no siempre arranca en la carpeta del programa. */
    private static Path carpetaDelAgente() {
        String exe = System.getProperty("jpackage.app-path");
        return exe != null ? Path.of(exe).toAbsolutePath().getParent() : Path.of("").toAbsolutePath();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static void configurarLog(Path carpeta) throws IOException {
        Logger raiz = Logger.getLogger("");
        for (Handler h : raiz.getHandlers()) raiz.removeHandler(h);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");
        SimpleFormatter formato = new SimpleFormatter();
        // agente0.log (el actual), agente1.log y agente2.log, de 1 MB cada uno: no llena nunca el disco.
        FileHandler archivo = new FileHandler(carpeta.toString().replace("%", "%%") + File.separator + "agente%g.log", 1_000_000, 3, true);
        archivo.setEncoding("UTF-8");
        archivo.setFormatter(formato);
        raiz.addHandler(archivo);
        ConsoleHandler consola = new ConsoleHandler();
        consola.setFormatter(formato);
        raiz.addHandler(consola);
    }

    private static class TokenInvalidoException extends Exception {}
}
