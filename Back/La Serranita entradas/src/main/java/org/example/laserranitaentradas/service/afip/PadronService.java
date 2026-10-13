package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.laserranitaentradas.model.dto.PadronContribuyenteDTO;
import org.example.laserranitaentradas.model.dto.PadronContribuyenteDTO.Estado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Consulta un CUIT en el padrón de constancia de inscripción de ARCA y lo traduce a lo que lleva
 * una Factura A: razón social, domicilio y condición frente al IVA.
 *
 * Es una ayuda para no tipear: si ARCA no contesta o no da la constancia, el admin carga los datos
 * a mano igual. Por eso los problemas vuelven como estado en la respuesta y no como error HTTP.
 */
@Service
public class PadronService {

    private static final Logger log = LoggerFactory.getLogger(PadronService.class);

    private static final int IMPUESTO_IVA = 30;
    private static final int IMPUESTO_IVA_EXENTO = 32;
    private static final int IMPUESTO_MONOTRIBUTO = 20;
    private static final int CONDICION_RESPONSABLE_INSCRIPTO = 1;
    private static final int CONDICION_EXENTO = 4;
    private static final int CONDICION_MONOTRIBUTO = 6;
    private static final int CONDICION_MONOTRIBUTO_SOCIAL = 13;
    private static final int CONDICION_MONOTRIBUTO_PROMOVIDO = 16;

    private final AfipSdkClient client;

    public PadronService(AfipSdkClient client) {
        this.client = client;
    }

    public PadronContribuyenteDTO consultar(String cuitIngresado) {
        String cuit = cuitIngresado == null ? "" : cuitIngresado.replaceAll("[\\s.-]", "");
        if (!WsfeService.cuitValido(cuit)) {
            throw new IllegalArgumentException("El CUIT no es válido");
        }
        if (!client.estaConfigurado()) {
            return PadronContribuyenteDTO.sinDatos(cuit, Estado.NO_DISPONIBLE, "La facturación no está configurada en el servidor.");
        }
        JsonNode respuesta;
        try {
            respuesta = client.consultarPadron(Long.parseLong(cuit));
        } catch (AfipException e) {
            String detalle = e.getMessage() == null ? "" : e.getMessage();
            if (detalle.contains("No existe persona")) {
                return PadronContribuyenteDTO.sinDatos(cuit, Estado.NO_EXISTE, "Ese CUIT no figura en el padrón de ARCA.");
            }
            // Ej. en producción, el certificado todavía no autorizado para ws_sr_constancia_inscripcion.
            log.warn("No se pudo consultar el CUIT {} en el padrón de ARCA: {}", cuit, detalle);
            return PadronContribuyenteDTO.sinDatos(cuit, Estado.NO_DISPONIBLE,
                    "No se pudo consultar el padrón de ARCA ahora. Cargá los datos a mano.");
        }
        return interpretar(cuit, respuesta);
    }

    /** Separado de la llamada para poder probarlo con respuestas reales guardadas. */
    PadronContribuyenteDTO interpretar(String cuit, JsonNode respuesta) {
        JsonNode persona = respuesta.has("personaReturn") ? respuesta.get("personaReturn") : respuesta;
        if (persona == null || persona.isNull() || persona.isMissingNode() || persona.isEmpty()) {
            return PadronContribuyenteDTO.sinDatos(cuit, Estado.NO_EXISTE, "Ese CUIT no figura en el padrón de ARCA.");
        }

        JsonNode generales = persona.path("datosGenerales");
        JsonNode errorConstancia = persona.path("errorConstancia");
        if (generales.isMissingNode() && !errorConstancia.isMissingNode()) {
            // ARCA no da la constancia (CUIT cancelada, bloqueada...): se devuelve el motivo y, si
            // vino, el nombre, pero no se arma nada para facturar.
            String motivo = textos(errorConstancia.path("error")).stream().collect(Collectors.joining(" "));
            return new PadronContribuyenteDTO(cuit, Estado.CON_ERROR, nombre(errorConstancia), null, null, null, false,
                    "ARCA no da la constancia de este CUIT" + (motivo.isBlank() ? "." : ": " + motivo));
        }

        Integer condicion = condicionIva(persona);
        String condicionTexto = condicion == null ? "No inscripto en IVA ni en Monotributo"
                : condicion == CONDICION_EXENTO ? "IVA Sujeto Exento"
                : WsfeService.CONDICIONES_IVA_FACTURA_A.get(condicion);
        boolean admiteA = condicion != null && WsfeService.CONDICIONES_IVA_FACTURA_A.containsKey(condicion);

        List<String> avisos = new ArrayList<>();
        String estadoClave = generales.path("estadoClave").asText("");
        if (!estadoClave.isBlank() && !"ACTIVO".equalsIgnoreCase(estadoClave)) {
            avisos.add("La CUIT figura " + estadoClave.toLowerCase(Locale.ROOT) + " en ARCA.");
        }
        if (!admiteA) {
            avisos.add(condicionTexto + ": no corresponde Factura A, va con Factura B.");
        }
        if (!errorConstancia.isMissingNode()) {
            avisos.addAll(textos(errorConstancia.path("error")));
        }

        return new PadronContribuyenteDTO(cuit, Estado.ENCONTRADO, nombre(generales), domicilio(generales.path("domicilioFiscal")),
                condicion, condicionTexto, admiteA, avisos.isEmpty() ? null : String.join(" ", avisos));
    }

    /**
     * Monotributo activo (con la variante según la categoría), si no IVA activo = responsable
     * inscripto, si no IVA exento. Monotributo primero: en el padrón de homologación hay CUITs con
     * los dos activos y, si es monotributista, una A como responsable inscripto la rechazaría ARCA.
     */
    private static Integer condicionIva(JsonNode persona) {
        JsonNode mono = persona.path("datosMonotributo");
        if (impuestoActivo(mono, IMPUESTO_MONOTRIBUTO)) {
            String categoria = mono.path("categoriaMonotributo").path("descripcionCategoria").asText("").toUpperCase(Locale.ROOT);
            if (categoria.contains("PROMOVIDO")) return CONDICION_MONOTRIBUTO_PROMOVIDO;
            if (categoria.contains("SOCIAL")) return CONDICION_MONOTRIBUTO_SOCIAL;
            return CONDICION_MONOTRIBUTO;
        }
        JsonNode general = persona.path("datosRegimenGeneral");
        if (impuestoActivo(general, IMPUESTO_IVA)) return CONDICION_RESPONSABLE_INSCRIPTO;
        if (impuestoActivo(general, IMPUESTO_IVA_EXENTO)) return CONDICION_EXENTO;
        return null;
    }

    private static boolean impuestoActivo(JsonNode datos, int idImpuesto) {
        for (JsonNode imp : lista(datos.path("impuesto"))) {
            if (imp.path("idImpuesto").asInt() == idImpuesto && "AC".equalsIgnoreCase(imp.path("estadoImpuesto").asText("AC"))) {
                return true;
            }
        }
        return false;
    }

    /** Razón social de una persona jurídica, o "APELLIDO NOMBRE" de una física. */
    private static String nombre(JsonNode datos) {
        String razonSocial = datos.path("razonSocial").asText("").trim();
        if (!razonSocial.isEmpty()) return razonSocial;
        String completo = (datos.path("apellido").asText("") + " " + datos.path("nombre").asText("")).trim();
        return completo.isEmpty() ? null : completo;
    }

    /** "PERALTA RAMOS 1428, BARRIO EL DORADO, BUENOS AIRES" (sin repetir localidad y provincia). */
    private static String domicilio(JsonNode d) {
        if (d.isMissingNode() || d.isNull()) return null;
        List<String> partes = new ArrayList<>();
        for (String campo : List.of("direccion", "localidad", "descripcionProvincia")) {
            String valor = d.path(campo).asText("").trim();
            if (!valor.isEmpty() && partes.stream().noneMatch(p -> p.equalsIgnoreCase(valor))) partes.add(valor);
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    /** ARCA devuelve las listas de un solo elemento como objeto suelto en vez de array. */
    private static Iterable<JsonNode> lista(JsonNode nodo) {
        if (nodo.isMissingNode() || nodo.isNull()) return List.of();
        return nodo.isArray() ? nodo : List.of(nodo);
    }

    private static List<String> textos(JsonNode nodo) {
        List<String> salida = new ArrayList<>();
        for (JsonNode n : lista(nodo)) {
            if (!n.asText("").isBlank()) salida.add(n.asText().trim());
        }
        return salida;
    }
}
