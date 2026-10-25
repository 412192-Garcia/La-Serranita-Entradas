package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DestinoFactura;

/**
 * Factura que arranca marcada en el POS para cada forma de pago: IMPRIMIR o MAIL (mail con el
 * email vacío = no facturar).
 */
public record FacturaPredeterminadaDTO(DestinoFactura efectivo, DestinoFactura tarjeta, DestinoFactura qr) {

    public static FacturaPredeterminadaDTO de(ConfiguracionParque config) {
        return new FacturaPredeterminadaDTO(config.getFacturaEfectivo(), config.getFacturaTarjeta(), config.getFacturaQr());
    }
}
