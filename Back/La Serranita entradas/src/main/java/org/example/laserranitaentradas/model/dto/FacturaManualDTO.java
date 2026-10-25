package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.DestinoFactura;

import java.math.BigDecimal;
import java.util.List;

/**
 * Factura manual sin venta (B, o A con receptor).
 *
 * @param destino IMPRIMIR, MAIL o NINGUNO (sólo el PDF)
 * @param impresora ticketera, sólo con IMPRIMIR (null = la única conectada)
 * @param receptor null = Factura B a Consumidor Final; con datos = Factura A
 */
public record FacturaManualDTO(List<Item> items, DestinoFactura destino, String email, String impresora,
                               Receptor receptor) {

    public FacturaManualDTO(List<Item> items, DestinoFactura destino, String email, String impresora) {
        this(items, destino, email, impresora, null);
    }

    /**
     * Receptor de una Factura A.
     *
     * @param cuit 11 dígitos (se aceptan guiones)
     * @param condicionIva código ARCA: 1 Responsable Inscripto, 6 Monotributo, 13 Monotributista Social, 16
     * Monotributo Trabajador Independiente Promovido
     */
    public record Receptor(String cuit, String razonSocial, String domicilio, Integer condicionIva) {}

    /**
     * Un renglón.
     *
     * @param tipo ENTRADA o ARTICULO del catálogo, LIBRE a mano. Decide el concepto de ARCA (servicio o
     * producto).
     * @param subtotal cantidad × precio, IVA incluido (también en Factura A)
     */
    public record Item(String tipo, Integer cantidad, String descripcion, BigDecimal subtotal) {}
}
