package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.laserranitaentradas.model.entity.TicketAccesoArca;
import org.example.laserranitaentradas.repository.TicketAccesoArcaRepository;
import org.example.laserranitaentradas.service.afip.Wsaa.TicketAcceso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente SOAP de ARCA: WSAA, WSFEv1 y padrón.
 *
 * El ticket de WSAA dura 12 h y ARCA no da otro mientras siga vigente, por eso se guarda en la base
 * (TicketAccesoArca). Sin certificado y clave la facturación queda apagada, también en homologación.
 */
@Component
public class ArcaClient {

    private static final Logger log = LoggerFactory.getLogger(ArcaClient.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    static final String SERVICIO_WSFE = "wsfe";
    /** Padrón de constancia de inscripción (datos fiscales de un CUIT), para la Factura A. */
    static final String SERVICIO_PADRON = "ws_sr_constancia_inscripcion";

    static final String NS_WSAA = "http://wsaa.view.sua.dvadac.desein.afip.gov";
    static final String NS_WSFE = "http://ar.gov.afip.dif.FEV1/";
    static final String NS_PADRON = "http://a5.soap.ws.server.puc.sr/";

    private record Urls(String wsaa, String wsfe, String padron) {}

    private static final Urls HOMOLOGACION = new Urls(
            "https://wsaahomo.afip.gov.ar/ws/services/LoginCms",
            "https://wswhomo.afip.gov.ar/wsfev1/service.asmx",
            "https://awshomo.afip.gov.ar/sr-padron/webservices/personaServiceA5");
    private static final Urls PRODUCCION = new Urls(
            "https://wsaa.afip.gov.ar/ws/services/LoginCms",
            "https://servicios1.afip.gov.ar/wsfev1/service.asmx",
            "https://aws.afip.gov.ar/sr-padron/webservices/personaServiceA5");

    /**
     * Corto a propósito: pedir otro mientras ARCA da por vigente el anterior falla
     * (coe.alreadyAuthenticated).
     */
    private static final Duration MARGEN_VENCIMIENTO_TA = Duration.ofSeconds(5);
    private static final MediaType TEXT_XML = new MediaType("text", "xml", StandardCharsets.UTF_8);

    private final RestClient restClient;
    private final TicketAccesoArcaRepository ticketRepository;
    private final TransactionTemplate txNueva;
    private final boolean produccion;
    private final Urls urls;

    /** Null si no hay o no sirven (el motivo queda en el log del arranque). */
    private final X509Certificate certificado;
    private final PrivateKey clave;
    /** Vencimiento del certificado (notAfter) en hora de Argentina; null si no hay certificado. */
    private final LocalDate certificadoVence;
    /** AFIP_CUIT o, si no está, el del certificado. */
    private final String cuit;
    private final boolean configurado;

    /** Ticket de acceso vigente por web service: cada uno se autoriza por separado. */
    private final Map<String, TicketAcceso> tickets = new ConcurrentHashMap<>();

    /**
     * @param environment "dev" (homologación) o "prod"
     * @param cuit el CUIT que factura; vacío = el del certificado
     * @param cert certificado PEM: ruta a un archivo o el contenido (también en una línea con "\n" escritos)
     * @param key la clave privada, igual que cert
     */
    @Autowired
    public ArcaClient(TicketAccesoArcaRepository ticketRepository, PlatformTransactionManager transactionManager,
                      @Value("${afip.environment:dev}") String environment,
                      @Value("${afip.cuit:}") String cuit,
                      @Value("${afip.cert:}") String cert,
                      @Value("${afip.key:}") String key) {
        this(restClientConTimeouts(), ticketRepository, transactionManager, environment, cuit, cert, key);
    }

    ArcaClient(RestClient restClient, TicketAccesoArcaRepository ticketRepository, PlatformTransactionManager transactionManager,
               String environment, String cuit, String cert, String key) {
        this.restClient = restClient;
        this.ticketRepository = ticketRepository;
        // El ticket se guarda en su propia transacción para no perderlo si la de quien llama se deshace.
        this.txNueva = new TransactionTemplate(transactionManager);
        this.txNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.produccion = "prod".equalsIgnoreCase(environment);
        this.urls = produccion ? PRODUCCION : HOMOLOGACION;

        // Se validan al arrancar y el motivo va al log (nunca el contenido).
        String certPem = leerCredencial("AFIP_CERT", cert, "CERTIFICATE");
        String keyPem = leerCredencial("AFIP_KEY", key, "PRIVATE KEY");
        X509Certificate x509 = null;
        PrivateKey privada = null;
        if (certPem != null) {
            try {
                x509 = Wsaa.certificado(certPem);
            } catch (Exception e) {
                log.error("AFIP_CERT: no se pudo leer el certificado: {}", e.getMessage());
            }
        }
        if (keyPem != null) {
            try {
                privada = Wsaa.clave(keyPem);
            } catch (Exception e) {
                log.error("AFIP_KEY: no se pudo leer la clave: {}", e.getMessage());
            }
        }
        if (x509 != null && privada != null && !Wsaa.sonPareja(x509, privada)) {
            log.error("AFIP_KEY no es la clave del certificado AFIP_CERT (¿un certificado viejo o de otro pedido?)");
            privada = null;
        }
        this.certificado = x509;
        this.clave = privada;
        this.certificadoVence = x509 == null ? null : x509.getNotAfter().toInstant().atZone(ZONA).toLocalDate();
        if (certificadoVence != null) {
            long dias = ChronoUnit.DAYS.between(LocalDate.now(ZONA), certificadoVence);
            if (dias < 0) log.error("El certificado de ARCA VENCIÓ el {}: hay que generar uno nuevo", certificadoVence);
            else if (dias <= 30) log.warn("El certificado de ARCA vence en {} días ({}): renovarlo", dias, certificadoVence);
            else log.info("Certificado de ARCA vigente hasta el {}", certificadoVence);
        }

        String cuitConfigurado = cuit == null ? "" : cuit.trim();
        this.cuit = !cuitConfigurado.isEmpty() ? cuitConfigurado
                : x509 == null ? null : Wsaa.cuitDelCertificado(x509).orElse(null);

        boolean hayCredenciales = certificado != null && clave != null;
        boolean cuitValido = this.cuit != null && WsfeService.cuitValido(this.cuit);
        this.configurado = hayCredenciales && cuitValido;
        if ((cert == null || cert.isBlank()) && (key == null || key.isBlank())) {
            log.info("Facturación apagada: falta configurar AFIP_CERT y AFIP_KEY (certificado de ARCA)");
        } else if (!hayCredenciales) {
            log.error("Facturación APAGADA: hacen falta AFIP_CERT y AFIP_KEY válidos (ver el motivo arriba)");
        } else if (!cuitValido) {
            log.error("Facturación APAGADA: {}", this.cuit == null
                    ? "falta AFIP_CUIT y el certificado no dice de qué CUIT es"
                    : "AFIP_CUIT (" + this.cuit + ") no es un CUIT válido");
        } else {
            log.info("Facturación con ARCA en {}: CUIT {}", produccion ? "producción" : "homologación", this.cuit);
        }
    }

    private static RestClient restClientConTimeouts() {
        // Sin timeouts una ARCA colgada frena la cola de emisión entera.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Sin certificado no hay facturación: las ventas se registran igual, sin factura. */
    public boolean estaConfigurado() {
        return configurado;
    }

    /** Vencimiento del certificado de ARCA cargado; vacío si no hay. */
    public Optional<LocalDate> vencimientoCertificado() {
        return Optional.ofNullable(certificadoVence);
    }

    /** El CUIT que factura; null si la facturación no está configurada. */
    public String getCuit() {
        return cuit;
    }

    public boolean esProduccion() {
        return produccion;
    }

    /**
     * Llama a un método de WSFE. El bloque Auth se agrega acá. Con más de un campo, `params` respeta el orden
     * del WSDL (SoapXml.campos). Devuelve el contenido de "<Metodo>Result".
     */
    public JsonNode llamarWsfe(String metodo, Map<String, Object> params) {
        SoapXml.exigirOrden(params);
        TicketAcceso ta = ticket(SERVICIO_WSFE);
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("Auth", SoapXml.campos("Token", ta.token(), "Sign", ta.sign(), "Cuit", cuit));
        campos.putAll(params);
        JsonNode respuesta = llamar(SERVICIO_WSFE, urls.wsfe(), NS_WSFE + metodo,
                SoapXml.sobre(NS_WSFE, metodo, campos, true));
        JsonNode resultado = respuesta.get(metodo + "Result");
        return resultado != null ? resultado : respuesta;
    }

    /**
     * getPersona_v2 del padrón. El ticket va suelto en los parámetros. En producción el certificado tiene que
     * estar autorizado para este servicio.
     */
    public JsonNode consultarPadron(long cuitConsultado) {
        TicketAcceso ta = ticket(SERVICIO_PADRON);
        return llamar(SERVICIO_PADRON, urls.padron(), "", SoapXml.sobre(NS_PADRON, "getPersona_v2",
                SoapXml.campos("token", ta.token(), "sign", ta.sign(), "cuitRepresentada", cuit, "idPersona", cuitConsultado),
                false));
    }

    private synchronized TicketAcceso ticket(String servicio) {
        TicketAcceso ta = tickets.get(servicio);
        if (vigente(ta)) return ta;
        ta = guardado(servicio);
        if (!vigente(ta)) {
            ta = pedirTicket(servicio);
            guardar(servicio, ta);
            log.info("Ticket de acceso de ARCA para {} renovado (vence {})", servicio, ta.vence());
        }
        tickets.put(servicio, ta);
        return ta;
    }

    private static boolean vigente(TicketAcceso ta) {
        return ta != null && LocalDateTime.now(ZONA).isBefore(ta.vence().minus(MARGEN_VENCIMIENTO_TA));
    }

    private TicketAcceso pedirTicket(String servicio) {
        String pedido = Wsaa.firmar(Wsaa.pedidoDeTicket(servicio, ZonedDateTime.now(ZONA)), certificado, clave);
        JsonNode r = llamar(servicio, urls.wsaa(), "", SoapXml.sobre(NS_WSAA, "loginCms", SoapXml.campos("in0", pedido), true));
        return Wsaa.leerTicket(r.path("loginCmsReturn").asText(""));
    }

    private String idTicket(String servicio) {
        return (produccion ? "prod" : "dev") + ":" + servicio;
    }

    private String numeroSerieCertificado() {
        return certificado.getSerialNumber().toString(16);
    }

    /** El ticket guardado en la base, si es del certificado actual. */
    private TicketAcceso guardado(String servicio) {
        try {
            return ticketRepository.findById(idTicket(servicio))
                    .filter(t -> t.getCertificado().equals(numeroSerieCertificado()))
                    .map(t -> new TicketAcceso(t.getToken(), t.getSign(), t.getVence()))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("No se pudo leer el ticket de acceso de ARCA guardado para {}: {}", servicio, e.getMessage());
            return null;
        }
    }

    private void guardar(String servicio, TicketAcceso ta) {
        try {
            txNueva.executeWithoutResult(s -> {
                TicketAccesoArca t = new TicketAccesoArca();
                t.setServicio(idTicket(servicio));
                t.setCertificado(numeroSerieCertificado());
                t.setToken(ta.token());
                t.setSign(ta.sign());
                t.setVence(ta.vence());
                ticketRepository.save(t);
            });
        } catch (Exception e) {
            // Sigue sirviendo desde la memoria; lo que se pierde es que sobreviva a un reinicio.
            log.warn("No se pudo guardar el ticket de acceso de ARCA para {}: {}", servicio, e.getMessage());
        }
    }

    private JsonNode llamar(String servicio, String url, String accionSoap, String sobre) {
        if (!configurado) {
            throw new AfipException("La facturación con ARCA no está configurada (falta el certificado)", false);
        }
        byte[] cuerpo;
        try {
            cuerpo = restClient.post()
                    .uri(url)
                    .contentType(TEXT_XML)
                    .header("SOAPAction", "\"" + accionSoap + "\"")
                    .body(sobre)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            // SOAP manda las fallas con HTTP 500 y el detalle en el cuerpo.
            SoapXml.Falla falla = falla(e.getResponseBodyAsByteArray());
            if (falla != null) throw excepcion(servicio, falla, e);
            boolean transitorio = e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429;
            throw new AfipException("ARCA (" + servicio + ") respondió " + e.getStatusCode().value() + ": "
                    + recortar(e.getResponseBodyAsString()), transitorio, e);
        } catch (Exception e) {
            // Timeout, DNS, sin internet: siempre transitorio.
            throw new AfipException("No se pudo contactar a ARCA (" + servicio + "): " + e.getMessage(), true, e);
        }
        SoapXml.Respuesta respuesta;
        try {
            respuesta = SoapXml.leer(cuerpo == null ? new byte[0] : cuerpo);
        } catch (IllegalArgumentException e) {
            throw new AfipException("ARCA (" + servicio + ") devolvió una respuesta que no se entiende: " + e.getMessage(), true, e);
        }
        if (respuesta.falla() != null) throw excepcion(servicio, respuesta.falla(), null);
        return respuesta.contenido();
    }

    private static SoapXml.Falla falla(byte[] cuerpo) {
        try {
            return cuerpo == null || cuerpo.length == 0 ? null : SoapXml.leer(cuerpo).falla();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Las fallas de certificado, firma, autorización o pedido mal armado no se arreglan reintentando; las
     * demás sí.
     */
    static AfipException excepcion(String servicio, SoapXml.Falla falla, Throwable causa) {
        String codigo = falla.codigo();
        if (codigo.endsWith("alreadyAuthenticated")) {
            return new AfipException("ARCA ya entregó un ticket de acceso para " + servicio + " que este sistema no tiene"
                    + " guardado (¿otro sistema con el mismo certificado, o se borró la base?). Se destraba solo cuando"
                    + " ese ticket vence, hasta 12 h después de pedido, o usando un certificado nuevo", true, causa);
        }
        String mensaje = "ARCA (" + servicio + "): " + falla.mensaje() + " [" + codigo + "]";
        if (codigo.equals("coe.notAuthorized")) {
            mensaje += ". Hay que autorizar el certificado para " + servicio
                    + " (producción: Administrador de Relaciones de Clave Fiscal; homologación: WSASS)";
        }
        boolean permanente = codigo.startsWith("cms.") || codigo.startsWith("xml.") || codigo.startsWith("ta.")
                || codigo.equals("coe.notAuthorized") || codigo.equals("wsn.notFound") || codigo.equalsIgnoreCase("Client");
        return new AfipException(mensaje, !permanente, causa);
    }

    /**
     * El PEM de una credencial, como ruta o contenido. Null si no sirve (el motivo va al log).
     *
     * @param marca la cabecera esperada ("CERTIFICATE", "PRIVATE KEY")
     */
    private static String leerCredencial(String variable, String valor, String marca) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        // Pegado entre comillas en el panel o en el .env.
        if (limpio.length() > 1 && (limpio.startsWith("\"") && limpio.endsWith("\"")
                || limpio.startsWith("'") && limpio.endsWith("'"))) {
            limpio = limpio.substring(1, limpio.length() - 1).trim();
        }
        String pem;
        if (limpio.startsWith("-----BEGIN")) {
            // Contenido directo; si vino en una sola línea con \n escritos, se rearma.
            pem = limpio.replace("\\r\\n", "\n").replace("\\n", "\n");
        } else {
            Path archivo = Path.of(limpio);
            if (!Files.isRegularFile(archivo)) {
                log.error("{}: no existe el archivo {} (¿está montada la carpeta afip/ en el backend?)", variable, limpio);
                return null;
            }
            try {
                pem = Files.readString(archivo).trim();
            } catch (IOException e) {
                log.error("{}: no se pudo leer {}: {}", variable, limpio, e.getMessage());
                return null;
            }
        }
        if (!pem.contains("-----BEGIN") || !pem.contains(marca)) {
            log.error("{}: no es un PEM de tipo \"{}\" (revisar que no estén cruzados el certificado y la clave)", variable, marca);
            return null;
        }
        return pem;
    }

    private static String recortar(String texto) {
        if (texto == null) return "";
        return texto.length() > 500 ? texto.substring(0, 500) + "..." : texto;
    }
}
