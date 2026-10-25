package org.example.laserranitaentradas.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Traduce las excepciones a respuestas HTTP. El cuerpo va en texto plano: el front lo muestra tal cual. */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Reglas de negocio violadas: datos que no existen, estados inválidos, duplicados. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> solicitudInvalida(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    /** Pedido mal armado: 400, y no se registra en Sistema > Errores. */
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

    /** Respeta el código que ya eligió el controller. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> conCodigo(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    /** Lo demás se registra completo y al cliente se le da un mensaje con un código corto para reportarlo. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> errorInesperado(Exception ex) {
        if (esDesconexionDelCliente(ex)) {
            // El cliente cortó antes de la respuesta: no es error del servidor.
            log.debug("El cliente cortó la conexión antes de la respuesta: {}", ex.toString());
            return null;
        }
        // "E" + id de la request: con ese código se encuentran todas sus líneas en el log.
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

    /**
     * Se reconoce por el tipo (ClientAbortException, AsyncRequestNotUsableException) y no por el texto: un
     * "Connection reset" de la base o de MP es un error real y tiene que dar 500, o el POS daría la venta por
     * guardada.
     */
    public static boolean esDesconexionDelCliente(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String clase = t.getClass().getSimpleName();
            if (clase.equals("ClientAbortException") || clase.equals("AsyncRequestNotUsableException")) return true;
        }
        return false;
    }
}
