package org.example.laserranitaentradas.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traduce las excepciones de los servicios a respuestas HTTP en un solo lugar.
 *
 * Antes cada endpoint repetía el mismo try/catch, con dos problemas: los
 * controladores nuevos se olvidaban de ponerlo y devolvían el error 500 genérico
 * de Spring, y el cuerpo de la respuesta variaba de endpoint en endpoint.
 *
 * El cuerpo se devuelve como texto plano porque es lo que el frontend ya espera
 * para mostrarle el motivo al usuario.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Reglas de negocio violadas: datos que no existen, estados inválidos, duplicados. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> solicitudInvalida(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    /** Un controller que ya decidió el código HTTP (ej. 401 del agente de impresión con un token
     * inválido): se respeta ese código en vez de convertirlo en un 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> conCodigo(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    /** Cualquier otra cosa: se registra completa y al cliente se le da un mensaje neutro. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> errorInesperado(Exception ex) {
        log.error("Error no controlado atendiendo la request", ex);
        return ResponseEntity.internalServerError()
                .body("Ocurrió un error inesperado. Reintentá en unos minutos.");
    }
}
