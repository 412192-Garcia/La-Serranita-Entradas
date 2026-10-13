package org.example.laserranitaentradas.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sistema > Estado: una tarjeta por área, todas con la misma forma (así sumar una es fácil y la
 * pantalla las dibuja igual).
 *
 * @param estado OK (verde), ALERTA (rojo: hay algo para resolver, prende la campanita) o INFO (gris:
 *               no aplica ahora o no se puede saber)
 * @param enlace a dónde ir a resolverlo (ruta de la app), o null
 * @param accion botón que la pantalla dibuja en la tarjeta ("forzar-backup"), o null
 * @param filas  para las tarjetas que son una lista de cosas con su estado (las tareas programadas):
 *               la pantalla las dibuja como tabla chica en vez de los detalles en texto. Los
 *               detalles quedan igual para el mail de alertas. Null en las demás.
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
     * Una fila de la tabla chica de una tarjeta.
     *
     * @param valor      lo principal a la derecha ("hace 2 min")
     * @param secundario debajo, más chico ("próxima en 1 min"), o null
     * @param estado     OK / ALERTA / INFO, como las tarjetas
     */
    public record Fila(String nombre, String valor, String secundario, String estado) {}
}
