package org.example.laserranitaentradas.service.afip;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/** Certificados autofirmados con el formato de los de ARCA (serialNumber "CUIT ..."), sólo para tests. */
final class CertificadosDePrueba {

    private static final AtomicLong SERIE = new AtomicLong(1000);

    private CertificadosDePrueba() {
    }

    record Par(X509Certificate certificado, PrivateKey clave, String certPem, String clavePkcs8, String clavePkcs1) {}

    static Par generar(String cuit, Instant vence) {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            KeyPair par = generador.generateKeyPair();
            X500NameBuilder nombre = new X500NameBuilder(BCStyle.INSTANCE)
                    .addRDN(BCStyle.C, "AR")
                    .addRDN(BCStyle.O, "PRUEBA")
                    .addRDN(BCStyle.CN, "serranita-prueba");
            if (cuit != null) nombre.addRDN(BCStyle.SERIALNUMBER, "CUIT " + cuit);
            X500Name dn = nombre.build();
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(
                    dn, BigInteger.valueOf(SERIE.incrementAndGet()), Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                    Date.from(vence), dn, par.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate())));
            String pkcs8 = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(par.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----";
            return new Par(cert, par.getPrivate(), pem(cert), pkcs8, pem(par.getPrivate()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Certificado o clave en PEM; una clave RSA sale en PKCS#1 ("RSA PRIVATE KEY"), como OpenSSL 1.x. */
    private static String pem(Object objeto) throws Exception {
        StringWriter salida = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(salida)) {
            writer.writeObject(objeto);
        }
        return salida.toString().trim();
    }
}
