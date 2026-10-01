package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce entre el dominio y los métodos de WSFEv1 de ARCA (manual del desarrollador
 * COMPG v4). Sólo lo que usa el parque: Factura B a Consumidor Final con IVA 21%.
 */
@Component
public class WsfeService {

    public static final int CBTE_TIPO_FACTURA_B = 6;
    /** DocTipo 99 + DocNro 0 = consumidor final sin identificar. */
    private static final int DOC_TIPO_SIN_IDENTIFICAR = 99;
    private static final int CONDICION_IVA_CONSUMIDOR_FINAL = 5;
    private static final int ALICUOTA_IVA_21 = 5;
    /** "No existen datos en nuestros registros": el comprobante consultado no existe. */
    private static final int ERROR_COMPROBANTE_INEXISTENTE = 602;
    /** "El número o fecha del comprobante no se corresponde con el próximo a autorizar". */
    public static final int ERROR_NUMERO_NO_ES_EL_PROXIMO = 10016;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.BASIC_ISO_DATE;

    private final AfipSdkClient client;

    public WsfeService(AfipSdkClient client) {
        this.client = client;
    }

    public long ultimoAutorizado(int puntoVenta, int tipoComprobante) {
        JsonNode r = client.llamarWsfe("FECompUltimoAutorizado", Map.of(
                "PtoVta", puntoVenta,
                "CbteTipo", tipoComprobante));
        lanzarSiHayErrores(r, true);
        return r.path("CbteNro").asLong();
    }

    /** Vacío si ARCA no tiene ese comprobante. */
    public Optional<ComprobanteAutorizado> consultar(int puntoVenta, int tipoComprobante, long numero) {
        JsonNode r = client.llamarWsfe("FECompConsultar", Map.of(
                "FeCompConsReq", Map.of(
                        "CbteNro", numero,
                        "PtoVta", puntoVenta,
                        "CbteTipo", tipoComprobante)));
        List<String> errores = mensajes(r.path("Errors").path("Err"));
        if (!errores.isEmpty()) {
            if (tieneCodigo(r.path("Errors").path("Err"), ERROR_COMPROBANTE_INEXISTENTE)) {
                return Optional.empty();
            }
            throw new AfipException("ARCA: " + String.join(" | ", errores), true);
        }
        JsonNode c = r.path("ResultGet");
        if (c.isMissingNode() || c.path("CodAutorizacion").asText("").isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ComprobanteAutorizado(
                c.path("CodAutorizacion").asText(),
                LocalDate.parse(c.path("FchVto").asText(), FORMATO_FECHA),
                LocalDate.parse(c.path("CbteFch").asText(), FORMATO_FECHA),
                new BigDecimal(c.path("ImpTotal").asText("0"))));
    }

    public ResultadoCae solicitarCae(SolicitudCae s) {
        Map<String, Object> det = new LinkedHashMap<>();
        det.put("Concepto", s.concepto());
        det.put("DocTipo", DOC_TIPO_SIN_IDENTIFICAR);
        det.put("DocNro", 0);
        det.put("CbteDesde", s.numero());
        det.put("CbteHasta", s.numero());
        det.put("CbteFch", s.fecha().format(FORMATO_FECHA));
        det.put("ImpTotal", s.total());
        det.put("ImpTotConc", 0);
        det.put("ImpNeto", s.neto());
        det.put("ImpOpEx", 0);
        det.put("ImpIVA", s.iva());
        det.put("ImpTrib", 0);
        // Concepto 2 (servicios) y 3 (productos y servicios) exigen el período del servicio y
        // el vencimiento del pago. Es una entrada de un día cobrada en el acto: el período es
        // ese día y el pago "vence" en la fecha del comprobante (contado).
        if (s.concepto() != 1) {
            det.put("FchServDesde", s.fechaServicio().format(FORMATO_FECHA));
            det.put("FchServHasta", s.fechaServicio().format(FORMATO_FECHA));
            det.put("FchVtoPago", s.fecha().format(FORMATO_FECHA));
        }
        det.put("MonId", "PES");
        det.put("MonCotiz", 1);
        det.put("CondicionIVAReceptorId", CONDICION_IVA_CONSUMIDOR_FINAL);
        det.put("Iva", Map.of("AlicIva", List.of(Map.of(
                "Id", ALICUOTA_IVA_21,
                "BaseImp", s.neto(),
                "Importe", s.iva()))));

        JsonNode r = client.llamarWsfe("FECAESolicitar", Map.of(
                "FeCAEReq", Map.of(
                        "FeCabReq", Map.of(
                                "CantReg", 1,
                                "PtoVta", s.puntoVenta(),
                                "CbteTipo", s.tipoComprobante()),
                        "FeDetReq", Map.of("FECAEDetRequest", det))));

        JsonNode detResp = primero(r.path("FeDetResp").path("FECAEDetResponse"));
        List<String> mensajes = new ArrayList<>(mensajes(r.path("Errors").path("Err")));
        mensajes.addAll(mensajes(detResp.path("Observaciones").path("Obs")));
        List<Integer> codigos = new ArrayList<>(codigos(r.path("Errors").path("Err")));
        codigos.addAll(codigos(detResp.path("Observaciones").path("Obs")));

        String resultado = detResp.path("Resultado").asText(r.path("FeCabResp").path("Resultado").asText(""));
        String cae = detResp.path("CAE").asText("");
        if ("A".equals(resultado) && !cae.isBlank()) {
            return new ResultadoCae(true, cae,
                    LocalDate.parse(detResp.path("CAEFchVto").asText(), FORMATO_FECHA), mensajes, codigos);
        }
        if (resultado.isBlank() && !mensajes.isEmpty()) {
            // Sin Resultado pero con Errors: ARCA ni siquiera procesó el pedido (ej. error
            // interno o de autenticación). No se sabe si es pasajero, así que se reintenta.
            throw new AfipException("ARCA: " + String.join(" | ", mensajes), true);
        }
        return new ResultadoCae(false, null, null, mensajes, codigos);
    }

    private void lanzarSiHayErrores(JsonNode r, boolean transitorio) {
        List<String> errores = mensajes(r.path("Errors").path("Err"));
        if (!errores.isEmpty()) {
            throw new AfipException("ARCA: " + String.join(" | ", errores), transitorio);
        }
    }

    /** ARCA devuelve listas de un solo elemento como objeto suelto en vez de array. */
    private static JsonNode primero(JsonNode nodo) {
        return nodo.isArray() ? nodo.path(0) : nodo;
    }

    private static List<String> mensajes(JsonNode nodo) {
        List<String> salida = new ArrayList<>();
        if (nodo.isMissingNode() || nodo.isNull()) return salida;
        Iterable<JsonNode> items = nodo.isArray() ? nodo : List.of(nodo);
        for (JsonNode item : items) {
            salida.add(item.path("Code").asText() + " - " + item.path("Msg").asText());
        }
        return salida;
    }

    private static List<Integer> codigos(JsonNode nodo) {
        List<Integer> salida = new ArrayList<>();
        if (nodo.isMissingNode() || nodo.isNull()) return salida;
        Iterable<JsonNode> items = nodo.isArray() ? nodo : List.of(nodo);
        for (JsonNode item : items) {
            salida.add(item.path("Code").asInt());
        }
        return salida;
    }

    private static boolean tieneCodigo(JsonNode nodo, int codigo) {
        Iterable<JsonNode> items = nodo.isArray() ? nodo : List.of(nodo);
        for (JsonNode item : items) {
            if (item.path("Code").asInt() == codigo) return true;
        }
        return false;
    }

    public record SolicitudCae(int puntoVenta, int tipoComprobante, int concepto, long numero,
                               LocalDate fecha, LocalDate fechaServicio,
                               BigDecimal total, BigDecimal neto, BigDecimal iva) {}

    public record ResultadoCae(boolean aprobado, String cae, LocalDate caeVencimiento,
                               List<String> mensajes, List<Integer> codigos) {}

    public record ComprobanteAutorizado(String cae, LocalDate caeVencimiento, LocalDate fecha, BigDecimal total) {}
}
