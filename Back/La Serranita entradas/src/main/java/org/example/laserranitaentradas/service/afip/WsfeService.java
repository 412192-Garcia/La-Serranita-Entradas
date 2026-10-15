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
 * COMPG v4). Sólo lo que usa el parque: Factura B a Consumidor Final y, en la facturación manual,
 * Factura A a un responsable inscripto o monotributista identificado por CUIT. Todo con IVA 21%.
 *
 * Los campos se arman en el orden del WSDL (SoapXml.campos): WSFE es un servicio .NET que toma un
 * campo fuera de lugar como si no hubiera venido.
 */
@Component
public class WsfeService {

    public static final int CBTE_TIPO_FACTURA_A = 1;
    public static final int CBTE_TIPO_NOTA_CREDITO_A = 3;
    public static final int CBTE_TIPO_FACTURA_B = 6;
    public static final int CBTE_TIPO_NOTA_CREDITO_B = 8;
    /** DocTipo 99 + DocNro 0 = consumidor final sin identificar. */
    private static final int DOC_TIPO_SIN_IDENTIFICAR = 99;
    private static final int DOC_TIPO_CUIT = 80;
    private static final int CONDICION_IVA_CONSUMIDOR_FINAL = 5;

    /**
     * Condiciones frente al IVA del receptor que admiten Factura A (tabla FEParamGetCondicionIvaReceptor):
     * responsable inscripto y las tres variantes de monotributo (RG 5003). Exento, no alcanzado y
     * consumidor final van con Factura B.
     */
    public static final Map<Integer, String> CONDICIONES_IVA_FACTURA_A = Map.of(
            1, "IVA Responsable Inscripto",
            6, "Responsable Monotributo",
            13, "Monotributista Social",
            16, "Monotributo Trabajador Independiente Promovido");
    private static final int ALICUOTA_IVA_21 = 5;
    /** "No existen datos en nuestros registros": el comprobante consultado no existe. */
    private static final int ERROR_COMPROBANTE_INEXISTENTE = 602;
    /** "El número o fecha del comprobante no se corresponde con el próximo a autorizar". */
    public static final int ERROR_NUMERO_NO_ES_EL_PROXIMO = 10016;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.BASIC_ISO_DATE;

    private final ArcaClient client;

    public WsfeService(ArcaClient client) {
        this.client = client;
    }

    public long ultimoAutorizado(int puntoVenta, int tipoComprobante) {
        JsonNode r = client.llamarWsfe("FECompUltimoAutorizado", SoapXml.campos(
                "PtoVta", puntoVenta,
                "CbteTipo", tipoComprobante));
        lanzarSiHayErrores(r, true);
        return r.path("CbteNro").asLong();
    }

    /** Vacío si ARCA no tiene ese comprobante. */
    public Optional<ComprobanteAutorizado> consultar(int puntoVenta, int tipoComprobante, long numero) {
        JsonNode r = client.llamarWsfe("FECompConsultar", SoapXml.campos(
                "FeCompConsReq", SoapXml.campos(
                        "CbteTipo", tipoComprobante,
                        "CbteNro", numero,
                        "PtoVta", puntoVenta)));
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

    public static boolean esNotaDeCredito(int tipoComprobante) {
        return tipoComprobante == CBTE_TIPO_NOTA_CREDITO_A || tipoComprobante == CBTE_TIPO_NOTA_CREDITO_B;
    }

    public static boolean esFactura(int tipoComprobante) {
        return tipoComprobante == CBTE_TIPO_FACTURA_A || tipoComprobante == CBTE_TIPO_FACTURA_B;
    }

    /** "A" o "B". */
    public static String letra(int tipoComprobante) {
        return tipoComprobante == CBTE_TIPO_FACTURA_A || tipoComprobante == CBTE_TIPO_NOTA_CREDITO_A ? "A" : "B";
    }

    /** "Factura A" / "Nota de Crédito B". */
    public static String nombre(int tipoComprobante) {
        return (esNotaDeCredito(tipoComprobante) ? "Nota de Crédito " : "Factura ") + letra(tipoComprobante);
    }

    /** La nota de crédito que anula una factura de este tipo (misma letra). */
    public static int notaDeCreditoDe(int tipoFactura) {
        return tipoFactura == CBTE_TIPO_FACTURA_A ? CBTE_TIPO_NOTA_CREDITO_A : CBTE_TIPO_NOTA_CREDITO_B;
    }

    /** CUIT con dígito verificador válido (11 dígitos, sin guiones). */
    public static boolean cuitValido(String cuit) {
        if (cuit == null || !cuit.matches("\\d{11}")) return false;
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < 10; i++) suma += (cuit.charAt(i) - '0') * pesos[i];
        int verificador = 11 - (suma % 11);
        if (verificador == 11) verificador = 0;
        if (verificador == 10) return false;
        return verificador == cuit.charAt(10) - '0';
    }

    public ResultadoCae solicitarCae(SolicitudCae s) {
        Map<String, Object> det = new LinkedHashMap<>();
        det.put("Concepto", s.concepto());
        if (s.receptor() != null) {
            det.put("DocTipo", DOC_TIPO_CUIT);
            det.put("DocNro", Long.parseLong(s.receptor().cuit()));
        } else {
            det.put("DocTipo", DOC_TIPO_SIN_IDENTIFICAR);
            det.put("DocNro", 0);
        }
        det.put("CbteDesde", s.numero());
        det.put("CbteHasta", s.numero());
        det.put("CbteFch", s.fecha().format(FORMATO_FECHA));
        det.put("ImpTotal", s.total());
        det.put("ImpTotConc", 0);
        det.put("ImpNeto", s.neto());
        det.put("ImpOpEx", 0);
        det.put("ImpTrib", 0);
        det.put("ImpIVA", s.iva());
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
        det.put("CondicionIVAReceptorId", s.receptor() != null ? s.receptor().condicionIva() : CONDICION_IVA_CONSUMIDOR_FINAL);
        // Nota de crédito: la factura que anula. Va antes de Iva, como en el WSDL.
        if (s.asociado() != null) {
            det.put("CbtesAsoc", SoapXml.campos("CbteAsoc", List.of(SoapXml.campos(
                    "Tipo", s.asociado().tipo(),
                    "PtoVta", s.asociado().puntoVenta(),
                    "Nro", s.asociado().numero(),
                    "Cuit", client.getCuit(),
                    "CbteFch", s.asociado().fecha().format(FORMATO_FECHA)))));
        }
        det.put("Iva", SoapXml.campos("AlicIva", List.of(SoapXml.campos(
                "Id", ALICUOTA_IVA_21,
                "BaseImp", s.neto(),
                "Importe", s.iva()))));

        JsonNode r = client.llamarWsfe("FECAESolicitar", SoapXml.campos(
                "FeCAEReq", SoapXml.campos(
                        "FeCabReq", SoapXml.campos(
                                "CantReg", 1,
                                "PtoVta", s.puntoVenta(),
                                "CbteTipo", s.tipoComprobante()),
                        "FeDetReq", SoapXml.campos("FECAEDetRequest", det))));

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
                               BigDecimal total, BigDecimal neto, BigDecimal iva, Asociado asociado,
                               Receptor receptor) {}

    /** Receptor identificado de una Factura A (o su nota de crédito). Null = consumidor final. */
    public record Receptor(String cuit, int condicionIva) {}

    /** Comprobante que anula una nota de crédito. */
    public record Asociado(int tipo, int puntoVenta, long numero, LocalDate fecha) {}

    public record ResultadoCae(boolean aprobado, String cae, LocalDate caeVencimiento,
                               List<String> mensajes, List<Integer> codigos) {}

    public record ComprobanteAutorizado(String cae, LocalDate caeVencimiento, LocalDate fecha, BigDecimal total) {}
}
