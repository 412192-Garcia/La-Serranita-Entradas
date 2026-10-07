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
 */
public record EstadoSistemaDTO(LocalDateTime generado, List<Tarjeta> tarjetas) {

    public record Tarjeta(String clave, String titulo, String estado, String resumen, List<String> detalles, String enlace,
                          String accion) {

        /** La mayoría de las tarjetas no tienen botón propio. */
        public Tarjeta(String clave, String titulo, String estado, String resumen, List<String> detalles, String enlace) {
            this(clave, titulo, estado, resumen, detalles, enlace, null);
        }
    }
}
