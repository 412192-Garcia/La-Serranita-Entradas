package org.example.laserranitaentradas.monitoreo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * "Hacer un backup ahora" desde Sistema > Estado. El backup lo hace el contenedor "backup" (pg_dump
 * y la copia a Drive), no el backend: darle al backend acceso a Docker para dispararlo sería un
 * riesgo. Entonces se comunican por una carpeta compartida (app.backups.pedidos-dir):
 *
 * - el backend deja el archivo "backup" (quién y cuándo lo pidió);
 * - el contenedor backup lo mira cada minuto: lo renombra a "backup.en-curso", corre backup.sh y
 *   deja el resultado en "ultimo" ("OK fecha archivo" o "ERROR fecha motivo").
 */
@Component
public class PedidoBackup {

    public enum Situacion { NO_DISPONIBLE, LIBRE, PEDIDO, EN_CURSO }

    /** ultimoResultado: "OK", "ERROR" o null si nunca se pidió uno. */
    public record Estado(Situacion situacion, LocalDateTime pedidoEn, String ultimoResultado, LocalDateTime ultimoEn, String ultimoDetalle) {}

    @Value("${app.backups.pedidos-dir:/pedidos}")
    private String carpeta;

    public Estado estado() {
        Path dir = Path.of(carpeta);
        if (!Files.isDirectory(dir)) return new Estado(Situacion.NO_DISPONIBLE, null, null, null, null);
        String resultado = null;
        LocalDateTime ultimoEn = null;
        String detalle = null;
        String ultimo = leer(dir.resolve("ultimo"));
        if (ultimo != null) {
            String[] partes = ultimo.trim().split(" ", 3);
            resultado = partes[0];
            ultimoEn = fecha(partes.length > 1 ? partes[1] : null);
            detalle = partes.length > 2 ? partes[2] : null;
        }
        Situacion situacion = Files.exists(dir.resolve("backup.en-curso")) ? Situacion.EN_CURSO
                : Files.exists(dir.resolve("backup")) ? Situacion.PEDIDO : Situacion.LIBRE;
        LocalDateTime pedidoEn = situacion == Situacion.PEDIDO ? modificado(dir.resolve("backup"))
                : situacion == Situacion.EN_CURSO ? modificado(dir.resolve("backup.en-curso")) : null;
        return new Estado(situacion, pedidoEn, resultado, ultimoEn, detalle);
    }

    public Estado pedir(String usuario) {
        Estado actual = estado();
        switch (actual.situacion()) {
            case NO_DISPONIBLE -> throw new IllegalStateException(
                    "El servidor no tiene la carpeta de pedidos de backup (" + carpeta + "): falta montarla en docker-compose.");
            case PEDIDO, EN_CURSO -> throw new IllegalStateException("Ya hay un backup pedido: se hace en menos de un minuto.");
            default -> { }
        }
        LocalDateTime ahora = LocalDateTime.now(AuditoriaService.ZONA).withNano(0);
        try {
            Files.writeString(Path.of(carpeta, "backup"), (usuario != null ? usuario : "?") + " " + ahora + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo dejar el pedido de backup (" + e.getMessage()
                    + "): revisá que el contenedor backup esté corriendo con el docker-compose actual.");
        }
        AuditoriaContexto.detalle("Backup pedido a mano desde Sistema");
        return estado();
    }

    private static String leer(Path archivo) {
        try {
            return Files.isRegularFile(archivo) ? Files.readString(archivo, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static LocalDateTime modificado(Path archivo) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(archivo).toInstant(), AuditoriaService.ZONA).withNano(0);
        } catch (IOException e) {
            return null;
        }
    }

    private static LocalDateTime fecha(String texto) {
        try {
            return texto == null ? null : LocalDateTime.parse(texto);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
