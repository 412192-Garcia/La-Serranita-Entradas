package org.example.laserranitaentradas.service.afip;

/**
 * Falla al hablar con ARCA. `transitorio` decide qué hace el job de emisión: si es transitorio
 * (timeout, ARCA caída, sin internet) la factura sigue PENDIENTE y se reintenta sola; si no
 * (pedido rechazado, certificado vencido o no autorizado), pasa a ERROR y espera a un admin.
 */
public class AfipException extends RuntimeException {

    private final boolean transitorio;

    public AfipException(String mensaje, boolean transitorio) {
        super(mensaje);
        this.transitorio = transitorio;
    }

    public AfipException(String mensaje, boolean transitorio, Throwable causa) {
        super(mensaje, causa);
        this.transitorio = transitorio;
    }

    public boolean esTransitorio() {
        return transitorio;
    }
}
