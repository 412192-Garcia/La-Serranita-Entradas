package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Lo mínimo de SOAP 1.1 para hablar con los web services de ARCA sin generar clientes desde los
 * WSDL: arma el sobre a partir de mapas y lee la respuesta como JSON.
 *
 * La respuesta queda con la misma forma que devolvía AfipSDK: un campo por elemento, y los
 * elementos que se repiten (varios Err, varios impuesto) como array. Uno solo queda como objeto
 * suelto, así que quien lee tiene que aceptar las dos formas.
 */
final class SoapXml {

    static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    private SoapXml() {
    }

    /** Lo que vino en el Body: el contenido del elemento de respuesta, o la falla SOAP si fue eso. */
    record Respuesta(JsonNode contenido, Falla falla) {}

    /** faultcode sin el prefijo de namespace ("cms.cert.untrusted", "Server") y faultstring. */
    record Falla(String codigo, String mensaje) {}

    /**
     * campos("PtoVta", 11, "CbteTipo", 6): un mapa que respeta el orden en que se cargan los campos,
     * porque en los web services de ARCA el orden importa (el de .NET de WSFE ignora sin avisar un
     * campo fuera de lugar y lo toma como 0).
     */
    static Map<String, Object> campos(Object... claveValor) {
        if (claveValor.length % 2 != 0) {
            throw new IllegalArgumentException("Los campos van de a pares nombre, valor");
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put((String) claveValor[i], claveValor[i + 1]);
        }
        return mapa;
    }

    /** Falla si un mapa de más de un campo no garantiza el orden (ej. Map.of). */
    static void exigirOrden(Map<?, ?> mapa) {
        if (mapa.size() > 1 && !(mapa instanceof LinkedHashMap<?, ?>) && !(mapa instanceof SortedMap<?, ?>)) {
            throw new IllegalArgumentException("En SOAP el orden de los campos importa: armar " + mapa.keySet()
                    + " con SoapXml.campos(...) y no con un mapa sin orden");
        }
    }

    /**
     * El sobre SOAP de un método. Con `calificados` los campos van en el namespace del servicio
     * (WSFE, WSAA); sin, van sin namespace (el padrón, elementFormDefault="unqualified").
     *
     * Los valores pueden ser texto, números, mapas (elementos anidados) o listas (el mismo elemento
     * repetido, como cada AlicIva dentro de Iva). Los null no se mandan.
     */
    static String sobre(String namespace, String metodo, Map<String, ?> campos, boolean calificados) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<soap:Envelope xmlns:soap=\"").append(NS_SOAP).append("\"><soap:Body>");
        // Calificados: namespace por defecto en el método, lo heredan todos los campos. Sin
        // calificar: el método con prefijo y los campos sin namespace.
        String etiqueta = calificados ? metodo : "m:" + metodo;
        xml.append('<').append(etiqueta).append(calificados ? " xmlns=\"" : " xmlns:m=\"")
                .append(escapar(namespace)).append("\">");
        escribir(xml, campos);
        xml.append("</").append(etiqueta).append("></soap:Body></soap:Envelope>");
        return xml.toString();
    }

    private static void escribir(StringBuilder xml, Map<?, ?> campos) {
        exigirOrden(campos);
        campos.forEach((nombre, valor) -> {
            if (valor instanceof Collection<?> lista) {
                for (Object item : lista) elemento(xml, (String) nombre, item);
            } else {
                elemento(xml, (String) nombre, valor);
            }
        });
    }

    private static void elemento(StringBuilder xml, String nombre, Object valor) {
        if (valor == null) return;
        xml.append('<').append(nombre).append('>');
        if (valor instanceof Map<?, ?> hijos) {
            escribir(xml, hijos);
        } else {
            xml.append(escapar(valor instanceof BigDecimal d ? d.toPlainString() : String.valueOf(valor)));
        }
        xml.append("</").append(nombre).append('>');
    }

    static String escapar(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '&' -> salida.append("&amp;");
                case '<' -> salida.append("&lt;");
                case '>' -> salida.append("&gt;");
                case '"' -> salida.append("&quot;");
                case '\'' -> salida.append("&apos;");
                default -> salida.append(c);
            }
        }
        return salida.toString();
    }

    /**
     * Lee una respuesta SOAP. El contenido es lo de adentro del elemento de respuesta del Body: para
     * FECAESolicitarResponse, {"FECAESolicitarResult": {...}}.
     *
     * @throws IllegalArgumentException si no es un sobre SOAP (ej. la página de error de un proxy)
     */
    static Respuesta leer(byte[] xml) {
        Element body = hijo(parsear(xml).getDocumentElement(), "Body");
        if (body == null) {
            throw new IllegalArgumentException("La respuesta no es un sobre SOAP");
        }
        Element respuesta = hijo(body, null);
        if (respuesta == null) {
            return new Respuesta(JsonNodeFactory.instance.objectNode(), null);
        }
        if ("Fault".equals(respuesta.getLocalName()) && NS_SOAP.equals(respuesta.getNamespaceURI())) {
            return new Respuesta(null, new Falla(sinPrefijo(texto(respuesta, "faultcode")), texto(respuesta, "faultstring")));
        }
        return new Respuesta(aJson(respuesta), null);
    }

    /** Un XML suelto (como el ticket que WSAA devuelve como texto adentro de loginCmsReturn). */
    static JsonNode leerDocumento(String xml) {
        return aJson(parsear(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)).getDocumentElement());
    }

    private static Document parsear(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // Lo que llega de afuera no puede traer DTD ni entidades externas (XXE).
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            // Sin esto el parser imprime "[Fatal Error]" en la consola además de lanzar la excepción.
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            // Desde bytes: el parser toma la codificación de la declaración XML.
            return builder.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML inválido: " + e.getMessage(), e);
        }
    }

    /**
     * Elemento sin hijos = texto (recortado), o null si viene xsi:nil. Con hijos = objeto con un
     * campo por nombre (sin prefijo); los que se repiten, array.
     */
    static JsonNode aJson(Element e) {
        List<Element> hijos = hijos(e);
        if (hijos.isEmpty()) {
            if ("true".equals(e.getAttributeNS(NS_XSI, "nil"))) return NullNode.instance;
            return TextNode.valueOf(e.getTextContent().trim());
        }
        ObjectNode objeto = JsonNodeFactory.instance.objectNode();
        for (Element hijo : hijos) {
            String nombre = hijo.getLocalName();
            JsonNode valor = aJson(hijo);
            JsonNode previo = objeto.get(nombre);
            if (previo == null) {
                objeto.set(nombre, valor);
            } else if (previo instanceof ArrayNode lista) {
                lista.add(valor);
            } else {
                objeto.set(nombre, JsonNodeFactory.instance.arrayNode().add(previo).add(valor));
            }
        }
        return objeto;
    }

    private static List<Element> hijos(Element e) {
        List<Element> salida = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element hijo) salida.add(hijo);
        }
        return salida;
    }

    /** Primer hijo con ese nombre (sin prefijo), o el primero a secas si nombre es null. */
    private static Element hijo(Element e, String nombre) {
        for (Element hijo : hijos(e)) {
            if (nombre == null || nombre.equals(hijo.getLocalName())) return hijo;
        }
        return null;
    }

    private static String texto(Element e, String nombre) {
        Element hijo = hijo(e, nombre);
        return hijo == null ? "" : hijo.getTextContent().trim();
    }

    private static String sinPrefijo(String codigo) {
        int dosPuntos = codigo.indexOf(':');
        return dosPuntos >= 0 ? codigo.substring(dosPuntos + 1) : codigo;
    }
}
