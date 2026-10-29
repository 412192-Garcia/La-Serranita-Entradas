package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autenticación WSAA: el ticket de acceso (token + sign) se pide con un TRA firmado en CMS con el
 * certificado. Acá está lo que no depende de la red.
 */
final class Wsaa {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Empieza un rato antes por si el reloj del servidor adelanta: WSAA rechaza un generationTime futuro. */
    private static final Duration VENTANA_PEDIDO = Duration.ofMinutes(10);
    private static final Pattern CUIT = Pattern.compile("CUIT\\s*(\\d{11})");

    private Wsaa() {
    }

    record TicketAcceso(String token, String sign, LocalDateTime vence) {}

    /** loginTicketRequest para un web service ("wsfe", "ws_sr_constancia_inscripcion"). */
    static String pedidoDeTicket(String servicio, ZonedDateTime ahora) {
        ZonedDateTime enSegundos = ahora.withZoneSameInstant(ZONA).truncatedTo(ChronoUnit.SECONDS);
        DateTimeFormatter formato = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<loginTicketRequest version=\"1.0\"><header>"
                // Sólo tiene que ser distinto entre pedidos (entero de 32 bits sin signo).
                + "<uniqueId>" + enSegundos.toEpochSecond() + "</uniqueId>"
                + "<generationTime>" + enSegundos.minus(VENTANA_PEDIDO).format(formato) + "</generationTime>"
                + "<expirationTime>" + enSegundos.plus(VENTANA_PEDIDO).format(formato) + "</expirationTime>"
                + "</header><service>" + SoapXml.escapar(servicio) + "</service></loginTicketRequest>";
    }

    /** El pedido firmado: CMS (PKCS#7 SignedData) con el contenido y el certificado adentro, en base64. */
    static String firmar(String pedido, X509Certificate certificado, PrivateKey clave) {
        try {
            CMSSignedDataGenerator generador = new CMSSignedDataGenerator();
            generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(clave), certificado));
            generador.addCertificates(new JcaCertStore(List.of(certificado)));
            byte[] cms = generador.generate(new CMSProcessableByteArray(pedido.getBytes(StandardCharsets.UTF_8)), true).getEncoded();
            return Base64.getEncoder().encodeToString(cms);
        } catch (Exception e) {
            // No es algo que se arregle reintentando: la clave no sirve para firmar.
            throw new AfipException("No se pudo firmar el pedido de acceso a ARCA: " + e.getMessage(), false, e);
        }
    }

    /** El loginTicketResponse que WSAA devuelve como texto adentro de loginCmsReturn. */
    static TicketAcceso leerTicket(String loginTicketResponse) {
        JsonNode ticket;
        try {
            ticket = SoapXml.leerDocumento(loginTicketResponse);
        } catch (IllegalArgumentException e) {
            throw new AfipException("WSAA devolvió un ticket de acceso ilegible", true, e);
        }
        String token = ticket.path("credentials").path("token").asText("");
        String sign = ticket.path("credentials").path("sign").asText("");
        String vence = ticket.path("header").path("expirationTime").asText("");
        if (token.isBlank() || sign.isBlank() || vence.isBlank()) {
            throw new AfipException("WSAA devolvió un ticket de acceso incompleto", true);
        }
        return new TicketAcceso(token, sign,
                OffsetDateTime.parse(vence).atZoneSameInstant(ZONA).toLocalDateTime());
    }

    static X509Certificate certificado(String pem) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Clave RSA en PEM, PKCS#1 o PKCS#8. */
    static PrivateKey clave(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object leido = parser.readObject();
            JcaPEMKeyConverter conversor = new JcaPEMKeyConverter();
            if (leido instanceof PEMKeyPair par) return conversor.getPrivateKey(par.getPrivateKeyInfo());
            if (leido instanceof PrivateKeyInfo info) return conversor.getPrivateKey(info);
            if (leido instanceof PEMEncryptedKeyPair || leido instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new IllegalArgumentException("la clave tiene contraseña; generarla sin (openssl genrsa sin -aes256)");
            }
            throw new IllegalArgumentException("no es una clave privada");
        }
    }

    /** Si la clave es la del certificado (firma algo con una y lo verifica con el otro). */
    static boolean sonPareja(X509Certificate certificado, PrivateKey clave) {
        try {
            byte[] prueba = "la-serranita".getBytes(StandardCharsets.US_ASCII);
            Signature firma = Signature.getInstance("SHA256withRSA");
            firma.initSign(clave);
            firma.update(prueba);
            byte[] firmado = firma.sign();
            firma.initVerify(certificado.getPublicKey());
            firma.update(prueba);
            return firma.verify(firmado);
        } catch (Exception e) {
            return false;
        }
    }

    /** El CUIT del certificado de ARCA (va en el subject como serialNumber "CUIT 30XXXXXXXXX"). */
    static Optional<String> cuitDelCertificado(X509Certificate certificado) {
        try {
            for (RDN rdn : new JcaX509CertificateHolder(certificado).getSubject().getRDNs(BCStyle.SERIALNUMBER)) {
                Matcher m = CUIT.matcher(IETFUtils.valueToString(rdn.getFirst().getValue()));
                if (m.find()) return Optional.of(m.group(1));
            }
        } catch (Exception e) {
            // Sin CUIT en el certificado: tiene que venir en AFIP_CUIT.
        }
        return Optional.empty();
    }
}
