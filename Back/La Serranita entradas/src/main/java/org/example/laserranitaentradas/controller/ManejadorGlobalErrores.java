package org.example.laserranitaentradas.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    /** Pedido mal armado (JSON roto, un tipo que no corresponde): es error del que pide, no del
     * servidor, así que va como 400 y no se registra en Sistema > Errores. */
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<String> pedidoMalArmado(Exception ex) {
        return ResponseEntity.badRequest().body("El pedido no tiene el formato esperado.");
    }

    /** Una ruta que no existe (un bot probando URLs, un link viejo): 404, no un error del servidor. */
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<String> noEncontrado(Exception ex) {
        HttpStatus estado = ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException
                ? HttpStatus.METHOD_NOT_ALLOWED : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(estado).body("No existe.");
    }

    /** Un controller que ya decidió el código HTTP (ej. 401 del agente de impresión con un token
     * inválido): se respeta ese código en vez de convertirlo en un 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> conCodigo(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    /**
     * Cualquier otra cosa: se registra completa (queda en Sistema > Errores) y al cliente se le da un
     * mensaje neutro con un código corto, para que lo pueda reportar y se encuentre el error exacto.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> errorInesperado(Exception ex) {
        // "E" + el id de la request (ver MetricasRequestsFilter): con el código que reporta el usuario
        // se encuentran en el log todas las líneas de esa request, no sólo la del error.
        String requestId = org.slf4j.MDC.get("requestId");
        String codigo = "E" + (requestId != null ? requestId
                : Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt(0x100000, 0x1000000)).toUpperCase());
        org.slf4j.MDC.put("codigoError", codigo);
        try {
            log.error("Error no controlado atendiendo la request", ex);
        } finally {
            org.slf4j.MDC.remove("codigoError");
        }
        return ResponseEntity.internalServerError()
                .body("Ocurrió un error inesperado (código " + codigo + "). Reintentá en unos minutos; si sigue pasando, avisale al administrador con ese código.");
    }
}
