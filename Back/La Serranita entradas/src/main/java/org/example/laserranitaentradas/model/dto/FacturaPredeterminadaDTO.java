package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DestinoFactura;

/** Factura que arranca marcada en el POS por forma de pago: IMPRIMIR o MAIL. */
public record FacturaPredeterminadaDTO(DestinoFactura efectivo, DestinoFactura tarjeta, DestinoFactura qr) {

    public static FacturaPredeterminadaDTO de(ConfiguracionParque config) {
        return new FacturaPredeterminadaDTO(config.getFacturaEfectivo(), config.getFacturaTarjeta(), config.getFacturaQr());
    }
}
