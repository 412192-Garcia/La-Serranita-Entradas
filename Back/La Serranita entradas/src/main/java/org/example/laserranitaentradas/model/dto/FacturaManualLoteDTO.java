package org.example.laserranitaentradas.model.dto;

import java.util.List;

/** Lote de facturas manuales, una por venta. Sin entrega: quedan los PDF. */
public record FacturaManualLoteDTO(List<FacturaManualDTO> facturas, String claveIdempotencia) {

    public FacturaManualLoteDTO(List<FacturaManualDTO> facturas) {
        this(facturas, null);
    }
}
