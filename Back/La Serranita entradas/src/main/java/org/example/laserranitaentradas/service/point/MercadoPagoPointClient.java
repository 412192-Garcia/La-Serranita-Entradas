package org.example.laserranitaentradas.service.point;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API de órdenes de Point de Mercado Pago (POST /v1/orders con type "point"), que reemplazó a la
 * vieja "Point Integration API" de payment-intents. Va por HTTP directo y no por el SDK: el SDK de
 * Java no cubre las órdenes de Point ni el listado/configuración de terminales.
 *
 * El flujo: se crea una orden para un terminal_id, el Point la recibe por su propia conexión, el
 * cliente pasa la tarjeta y la orden queda processed (o failed/expired/canceled). El resultado
 * llega por webhook (tópico "order") y además se puede consultar con GET /v1/orders/{id}.
 */
@Component
public class MercadoPagoPointClient {

    private static final String BASE = "https://api.mercadopago.com";

    private final RestClient http;
    private final String accessToken;
    private final String expiracion;

    @org.springframework.beans.factory.annotation.Autowired
    public MercadoPagoPointClient(
            // El Point puede estar en otra cuenta que la del checkout online: si no, usa la misma.
            @Value("${mercadopago.point.access-token:}") String tokenPoint,
            @Value("${mercadopago.accessToken:}") String tokenCheckout,
            // Lo que tiene el cliente para pasar la tarjeta: el default de MP (15 min) es demasiado
            // para una fila en la boletería, y una orden vencida se vuelve a mandar en un toque.
            @Value("${mercadopago.point.expiracion:PT5M}") String expiracion) {
        this(tokenPoint != null && !tokenPoint.isBlank() ? tokenPoint : tokenCheckout, expiracion, restClientConTimeouts());
    }

    MercadoPagoPointClient(String accessToken, String expiracion, RestClient http) {
        this.accessToken = accessToken;
        this.expiracion = expiracion;
        this.http = http;
    }

    private static RestClient restClientConTimeouts() {
        // El boletero está esperando con el cliente adelante: mejor un error rápido que una
        // pantalla colgada.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(8));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl(BASE).requestFactory(requestFactory).build();
    }

    public boolean estaConfigurado() {
        return accessToken != null && !accessToken.isBlank();
    }

    /** Un Point de la cuenta. id = "MODELO__SERIE" (ej. NEWLAND_N950__N950NCB801293324). */
    public record Terminal(String id, String modo, String posId, String storeId, String externalPosId) {}

    /** Estado de una orden con lo que importa de su (único) pago. */
    public record Orden(String id, String status, String statusDetail, String pagoId, String pagoStatus,
                        String pagoStatusDetail, String medioPago, String tipoMedio, Integer cuotas) {}

    /** Error de Mercado Pago con su código HTTP y el mensaje que devolvió, para mostrarle al boletero. */
    public static class PointException extends RuntimeException {
        private final int status;

        public PointException(int status, String mensaje) {
            super(mensaje);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    public List<Terminal> listarTerminales() {
        JsonNode r = ejecutar(() -> http.get().uri("/terminals/v1/list?limit=50").headers(h -> h.setBearerAuth(accessToken))
                .retrieve().body(JsonNode.class));
        List<Terminal> terminales = new ArrayList<>();
        for (JsonNode t : r.path("data").path("terminals")) {
            terminales.add(new Terminal(t.path("id").asText(), texto(t, "operating_mode"), texto(t, "pos_id"),
                    texto(t, "store_id"), texto(t, "external_pos_id")));
        }
        return terminales;
    }

    /** PDV = la maneja el sistema (sólo cobra lo que le mandan); STANDALONE = se cobra a mano en el propio Point. */
    public void cambiarModo(String terminalId, String modo) {
        Map<String, Object> body = Map.of("terminals", List.of(Map.of("id", terminalId, "operating_mode", modo)));
        ejecutar(() -> http.patch().uri("/terminals/v1/setup").headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity());
    }

    /** qr = el Point muestra el QR para pagar con la app; si no, espera la tarjeta. */
    public Orden crearOrden(String terminalId, BigDecimal monto, String externalReference, String claveIdempotencia, boolean qr) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "point");
        body.put("external_reference", externalReference);
        body.put("expiration_time", expiracion);
        body.put("transactions", Map.of("payments", List.of(Map.of("amount", monto.setScale(2, RoundingMode.HALF_UP).toPlainString()))));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("point", Map.of("terminal_id", terminalId));
        if (qr) {
            config.put("payment_method", Map.of("default_type", "qr"));
        }
        body.put("config", config);
        JsonNode r = ejecutar(() -> http.post().uri("/v1/orders").headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.set("X-Idempotency-Key", claveIdempotencia);
                }).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        return orden(r);
    }

    public Orden obtenerOrden(String ordenId) {
        return orden(ejecutar(() -> http.get().uri("/v1/orders/{id}", ordenId).headers(h -> h.setBearerAuth(accessToken))
                .retrieve().body(JsonNode.class)));
    }

    /**
     * Cancela la orden. Si el Point ya la tomó (at_terminal) la cancelación es asincrónica: MP
     * responde 202, la orden sigue at_terminal con el pago en cancellation_requested y la
     * confirmación llega después (puede que no, si el cliente ya estaba pagando).
     */
    public Orden cancelarOrden(String ordenId, String claveIdempotencia) {
        return orden(ejecutar(() -> http.post().uri("/v1/orders/{id}/cancel", ordenId).headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.set("X-Idempotency-Key", claveIdempotencia);
                    h.set("x-allow-cancelable-status", "at_terminal");
                }).retrieve().body(JsonNode.class)));
    }

    /** Devolución total del pago (hasta 90 días). */
    public Orden devolverOrden(String ordenId, String claveIdempotencia) {
        return orden(ejecutar(() -> http.post().uri("/v1/orders/{id}/refund", ordenId).headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.set("X-Idempotency-Key", claveIdempotencia);
                }).retrieve().body(JsonNode.class)));
    }

    static Orden orden(JsonNode r) {
        if (r == null) {
            throw new PointException(502, "Mercado Pago no devolvió la orden");
        }
        JsonNode pago = r.path("transactions").path("payments").path(0);
        JsonNode medio = pago.path("payment_method");
        return new Orden(texto(r, "id"), texto(r, "status"), texto(r, "status_detail"), texto(pago, "id"),
                texto(pago, "status"), texto(pago, "status_detail"), texto(medio, "id"), texto(medio, "type"),
                medio.hasNonNull("installments") ? medio.get("installments").asInt() : null);
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() || v.isMissingNode() ? null : v.asText();
    }

    private <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        if (!estaConfigurado()) {
            throw new PointException(503, "Falta el access token de Mercado Pago en el servidor");
        }
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || (status == 403 && e.getResponseBodyAsString().contains("UNAUTHORIZED"))) {
                // Típico de un token de prueba o de una cuenta sin Point: MP sólo dice "UNAUTHORIZED".
                throw new PointException(status, "Mercado Pago no autorizó el token para usar Point: hacen falta las credenciales "
                        + "de producción de la cuenta dueña de los Points (MP_POINT_ACCESS_TOKEN o MP_ACCESS_TOKEN).");
            }
            throw new PointException(status, mensajeDe(e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new PointException(504, "No se pudo contactar a Mercado Pago: " + e.getMessage());
        }
    }

    /** MP devuelve {"errors":[{"code":"...","message":"..."}]} o {"message":"..."}: se queda con lo legible. */
    private static String mensajeDe(HttpStatusCode status, String cuerpo) {
        try {
            JsonNode r = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cuerpo);
            JsonNode error = r.path("errors").path(0);
            String codigo = texto(error, "code");
            String mensaje = texto(error, "message");
            if (mensaje == null) mensaje = texto(r, "message");
            if (codigo == null) codigo = texto(r, "error");
            if (mensaje != null) return codigo != null ? mensaje + " (" + codigo + ")" : mensaje;
        } catch (Exception ignorada) {
            // Cuerpo que no es JSON: se usa tal cual, recortado.
        }
        String crudo = cuerpo == null ? "" : cuerpo.strip();
        return "Mercado Pago respondió " + status.value() + (crudo.isEmpty() ? "" : ": " + crudo.substring(0, Math.min(200, crudo.length())));
    }
}
