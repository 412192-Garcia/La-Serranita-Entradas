package org.example.laserranitaentradas.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sistema > Estado: una tarjeta por área, todas con la misma forma.
 *
 * @param estado OK (verde), ALERTA (rojo, prende la campanita) o INFO (gris)
 * @param enlace ruta para ir a resolverlo, o null
 * @param accion botón de la tarjeta ("forzar-backup"), o null
 * @param filas tabla chica para las tarjetas que son una lista; null en las demás
 */
public record EstadoSistemaDTO(LocalDateTime generado, List<Tarjeta> tarjetas) {

    public record Tarjeta(String clave, String titulo, String estado, String resumen, List<String> detalles, String enlace,
                          String accion, List<Fila> filas) {

        /** La mayoría de las tarjetas no tienen botón propio. */
        public Tarjeta(String clave, String titulo, String estado, String resumen, List<String> detalles, String enlace) {
            this(clave, titulo, estado, resumen, detalles, enlace, null, null);
        }

        public Tarjeta(String clave, String titulo, String estado, String resumen, List<String> detalles, String enlace,
                       String accion) {
            this(clave, titulo, estado, resumen, detalles, enlace, accion, null);
        }
    }

    /**
     * Una fila de la tabla chica.
     *
     * @param valor lo principal ("hace 2 min")
     * @param secundario debajo ("próxima en 1 min"), o null
     * @param estado OK / ALERTA / INFO
     */
    public record Fila(String nombre, String valor, String secundario, String estado) {}
}
