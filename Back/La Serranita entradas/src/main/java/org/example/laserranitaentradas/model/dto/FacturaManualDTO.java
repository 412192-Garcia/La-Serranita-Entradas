package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.DestinoFactura;

import java.math.BigDecimal;
import java.util.List;

/**
 * Factura B (o A, si viene el receptor) cargada a mano por un admin (Acciones > Facturación manual), sin venta detrás: ítems del
 * catálogo o texto libre, con el importe que se quiera.
 *
 * @param destino   IMPRIMIR (ticketera), MAIL (al email) o NINGUNO (sólo queda el PDF para descargar)
 * @param impresora ticketera elegida, sólo con IMPRIMIR (null = la única conectada)
 * @param receptor  null = Factura B a Consumidor Final; con datos = Factura A a ese CUIT
 */
public record FacturaManualDTO(List<Item> items, DestinoFactura destino, String email, String impresora,
                               Receptor receptor) {

    public FacturaManualDTO(List<Item> items, DestinoFactura destino, String email, String impresora) {
        this(items, destino, email, impresora, null);
    }

    /**
     * Receptor de una Factura A.
     *
     * @param cuit         11 dígitos (se aceptan guiones)
     * @param condicionIva código ARCA: 1 Responsable Inscripto, 6 Monotributo, 13 Monotributista
     *                     Social, 16 Monotributo Trabajador Independiente Promovido
     */
    public record Receptor(String cuit, String razonSocial, String domicilio, Integer condicionIva) {}

    /**
     * Un renglón de la factura.
     *
     * @param tipo     ENTRADA o ARTICULO si se eligió del catálogo, LIBRE si se escribió a mano. Sólo
     *                 decide el concepto de ARCA: las entradas (y lo libre) son servicios, los
     *                 artículos son productos.
     * @param subtotal importe del renglón (cantidad × precio), IVA incluido (también en una Factura A:
     *                 el neto y el IVA se discriminan al armarla)
     */
    public record Item(String tipo, Integer cantidad, String descripcion, BigDecimal subtotal) {}
}
