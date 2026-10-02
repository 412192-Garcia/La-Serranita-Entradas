package org.example.serranita.agente;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Lo que hay en agente.properties (ver agente.properties.example). */
final class Configuracion {

    /** Dónde está físicamente una ticketera. */
    sealed interface Destino permits Red, Windows {}

    /** Ticketera de red: se le habla directo por TCP (puerto 9100, impresión RAW). */
    record Red(String host, int puerto) implements Destino {
        @Override
        public String toString() {
            return host + ":" + puerto;
        }
    }

    /** Ticketera instalada en Windows (USB, o compartida desde otra PC): el ticket va a la cola
     * de impresión de Windows con ese nombre, como bytes crudos. */
    record Windows(String impresora) implements Destino {
        @Override
        public String toString() {
            return "Windows '" + impresora + "'";
        }
    }

    final String backendUrl;
    final String token;
    final String nombre;
    /** Nombre de la impresora (el que ve la tablet) → dónde está (red o Windows). */
    final Map<String, Destino> impresoras;

    private Configuracion(String backendUrl, String token, String nombre, Map<String, Destino> impresoras) {
        this.backendUrl = backendUrl;
        this.token = token;
        this.nombre = nombre;
        this.impresoras = impresoras;
    }

    static Configuracion leer(Path archivo) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        String backendUrl = obligatorio(p, "backend.url").replaceAll("/+$", "");
        String token = obligatorio(p, "agente.token");
        String nombre = p.getProperty("agente.nombre", "PC Entrada").trim();

        Map<String, Destino> impresoras = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            String nombreImpresora = p.getProperty("impresora." + i + ".nombre");
            if (nombreImpresora == null || nombreImpresora.isBlank()) continue;
            nombreImpresora = nombreImpresora.trim();
            if (nombreImpresora.contains(";") || nombreImpresora.contains(",")) {
                throw new IllegalArgumentException("El nombre de impresora '" + nombreImpresora + "' no puede tener ';' ni ','");
            }
            // tipo=red (por defecto, así las configuraciones viejas siguen andando) o windows.
            String tipo = p.getProperty("impresora." + i + ".tipo", "red").trim().toLowerCase();
            Destino destino = switch (tipo) {
                case "red" -> new Red(obligatorio(p, "impresora." + i + ".host"),
                        Integer.parseInt(p.getProperty("impresora." + i + ".puerto", "9100").trim()));
                case "windows" -> new Windows(obligatorio(p, "impresora." + i + ".windows"));
                default -> throw new IllegalArgumentException(
                        "impresora." + i + ".tipo tiene que ser 'red' o 'windows' (es '" + tipo + "')");
            };
            impresoras.put(nombreImpresora, destino);
        }
        if (impresoras.isEmpty()) {
            throw new IllegalArgumentException("agente.properties no tiene ninguna impresora (impresora.1.nombre y su host o windows)");
        }
        return new Configuracion(backendUrl, token, nombre, impresoras);
    }

    private static String obligatorio(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta '" + clave + "' en agente.properties");
        }
        return valor.trim();
    }
}
