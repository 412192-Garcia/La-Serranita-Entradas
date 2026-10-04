package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.DestinoFactura;

import java.math.BigDecimal;
import java.util.List;

/**
 * Factura B cargada a mano por un admin (Acciones > Facturación manual), sin venta detrás: ítems del
 * catálogo o texto libre, con el importe que se quiera.
 *
 * @param destino   IMPRIMIR (ticketera), MAIL (al email) o NINGUNO (sólo queda el PDF para descargar)
 * @param impresora ticketera elegida, sólo con IMPRIMIR (null = la única conectada)
 */
public record FacturaManualDTO(List<Item> items, DestinoFactura destino, String email, String impresora) {

    /**
     * Un renglón de la factura.
     *
     * @param tipo     ENTRADA o ARTICULO si se eligió del catálogo, LIBRE si se escribió a mano. Sólo
     *                 decide el concepto de ARCA: las entradas (y lo libre) son servicios, los
     *                 artículos son productos.
     * @param subtotal importe del renglón (cantidad × precio), IVA incluido
     */
    public record Item(String tipo, Integer cantidad, String descripcion, BigDecimal subtotal) {}
}
