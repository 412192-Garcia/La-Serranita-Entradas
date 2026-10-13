package org.example.laserranitaentradas.model.dto;

import java.util.List;

/**
 * Lote de facturas manuales (Acciones > Facturación manual > En lote): al final del día, las
 * ventas que quedaron sin facturar, una factura por venta. Sólo cuentan los ítems de cada una: el
 * lote no se entrega (ni mail ni ticketera), quedan los PDF.
 */
public record FacturaManualLoteDTO(List<FacturaManualDTO> facturas) {}
