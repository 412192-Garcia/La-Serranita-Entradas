package org.example.laserranitaentradas.service.afip;

/** Falla con ARCA. Si es `transitorio` la factura sigue PENDIENTE y se reintenta; si no, pasa a ERROR. */
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
