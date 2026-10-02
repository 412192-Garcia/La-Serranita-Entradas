package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Cliente HTTP de la API REST de AfipSDK (https://docs.afipsdk.com/integracion/api).
 *
 * Se usa la API REST directo con RestClient en vez del SDK de Java: son sólo dos endpoints
 * (auth y requests) y así no se suma una dependencia más. AfipSDK se ocupa de lo engorroso de
 * ARCA: firmar el ticket de acceso de WSAA con el certificado y traducir SOAP a JSON.
 *
 * En modo dev sin certificado AfipSDK deja usar su CUIT de prueba (20409378472): con el
 * access token alcanza para probar contra homologación.
 */
@Component
public class AfipSdkClient {

    private static final Logger log = LoggerFactory.getLogger(AfipSdkClient.class);
    private static final String WSID_FACTURACION = "wsfe";
    /** Se pide un ticket nuevo un rato antes de que venza el actual, no justo en el límite. */
    private static final Duration MARGEN_VENCIMIENTO_TA = Duration.ofMinutes(10);
    private static final String USER_AGENT = "LaSerranitaEntradas/1.0";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${afip.access-token:}")
    private String accessToken;

    /** "dev" (homologación) o "prod". */
    @Value("${afip.environment:dev}")
    private String environment;

    @Value("${afip.cuit:20409378472}")
    private String cuit;

    /** Certificado y clave privada (PEM). Aceptan el contenido o la ruta a un archivo. Vacíos
     * en dev con el CUIT de prueba de AfipSDK; obligatorios en prod. */
    @Value("${afip.cert:}")
    private String cert;

    @Value("${afip.key:}")
    private String key;

    private String token;
    private String sign;
    private Instant vencimientoTa = Instant.EPOCH;

    public AfipSdkClient(ObjectMapper objectMapper,
                         @Value("${afip.base-url:https://app.afipsdk.com/api/v1/afip}") String baseUrl) {
        this.objectMapper = objectMapper;
        // Timeouts explícitos: sin esto una ARCA colgada deja el hilo de emisión esperando para
        // siempre, y con él la cola entera (se emite de a una por punto de venta).
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Sin access token no hay facturación: las ventas se registran igual, sin factura. */
    public boolean estaConfigurado() {
        if (accessToken == null || accessToken.isBlank()) return false;
        // En producción ARCA exige el certificado y la clave del CUIT: sin ellos cada factura
        // fallaría al autenticar y se acumularía en errores. Mejor apagada, como sin token.
        return !esProduccion() || (cert != null && !cert.isBlank() && key != null && !key.isBlank());
    }

    public String getCuit() {
        return cuit;
    }

    public boolean esProduccion() {
        return "prod".equalsIgnoreCase(environment);
    }

    /**
     * Llama a un método del web service de facturación (FECAESolicitar, FECompUltimoAutorizado,
     * etc.). `params` no lleva el bloque Auth: se agrega acá con el ticket vigente.
     *
     * Devuelve el resultado ya desenvuelto: ARCA contesta {"<Metodo>Result": {...}} y acá se
     * devuelve directamente lo de adentro.
     */
    public JsonNode llamarWsfe(String metodo, Map<String, Object> params) {
        ObjectNode cuerpo = objectMapper.createObjectNode();
        cuerpo.put("environment", environment);
        cuerpo.put("method", metodo);
        cuerpo.put("wsid", WSID_FACTURACION);
        ObjectNode paramsNode = objectMapper.valueToTree(params);
        paramsNode.set("Auth", auth());
        cuerpo.set("params", paramsNode);

        JsonNode respuesta = post("/requests", cuerpo);
        JsonNode resultado = respuesta.get(metodo + "Result");
        return resultado != null ? resultado : respuesta;
    }

    private synchronized ObjectNode auth() {
        if (token == null || Instant.now().isAfter(vencimientoTa.minus(MARGEN_VENCIMIENTO_TA))) {
            ObjectNode cuerpo = objectMapper.createObjectNode();
            cuerpo.put("environment", environment);
            cuerpo.put("tax_id", cuit);
            cuerpo.put("wsid", WSID_FACTURACION);
            if (!cert.isBlank() && !key.isBlank()) {
                cuerpo.put("cert", leerPem(cert));
                cuerpo.put("key", leerPem(key));
            }
            JsonNode respuesta = post("/auth", cuerpo);
            token = respuesta.path("token").asText(null);
            sign = respuesta.path("sign").asText(null);
            if (token == null || sign == null) {
                throw new AfipException("AfipSDK no devolvió token/sign al autenticar", true);
            }
            String expiration = respuesta.path("expiration").asText(null);
            vencimientoTa = expiration != null ? Instant.parse(expiration) : Instant.now().plus(Duration.ofHours(1));
            log.info("Ticket de acceso de ARCA renovado (vence {})", vencimientoTa);
        }
        ObjectNode authNode = objectMapper.createObjectNode();
        authNode.put("Token", token);
        authNode.put("Sign", sign);
        authNode.put("Cuit", cuit);
        return authNode;
    }

    private JsonNode post(String ruta, JsonNode cuerpo) {
        if (!estaConfigurado()) {
            throw new AfipException("Falta configurar AFIPSDK_ACCESS_TOKEN", false);
        }
        try {
            JsonNode respuesta = restClient.post()
                    .uri(ruta)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    // AfipSDK está detrás de Cloudflare, que corta con 403 (error 1010) a los
                    // User-Agent genéricos de librerías HTTP.
                    .header("User-Agent", USER_AGENT)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null) {
                throw new AfipException("AfipSDK devolvió una respuesta vacía", true);
            }
            return respuesta;
        } catch (RestClientResponseException e) {
            // 4xx de AfipSDK = pedido mal armado o credenciales: reintentar no lo arregla. 5xx sí
            // puede ser algo pasajero (AfipSDK o ARCA caídos).
            boolean transitorio = e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429;
            throw new AfipException("AfipSDK respondió " + e.getStatusCode().value() + ": "
                    + recortar(e.getResponseBodyAsString()), transitorio, e);
        } catch (AfipException e) {
            throw e;
        } catch (Exception e) {
            // Timeout, DNS, sin internet: siempre transitorio.
            throw new AfipException("No se pudo contactar a AfipSDK: " + e.getMessage(), true, e);
        }
    }

    private static String leerPem(String valor) {
        if (valor.trim().startsWith("-----BEGIN")) {
            return valor;
        }
        try {
            return Files.readString(Path.of(valor.trim()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el certificado/clave de ARCA en " + valor, e);
        }
    }

    private static String recortar(String texto) {
        if (texto == null) return "";
        return texto.length() > 500 ? texto.substring(0, 500) + "..." : texto;
    }
}
