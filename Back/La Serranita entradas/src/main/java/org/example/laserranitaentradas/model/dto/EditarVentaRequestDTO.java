package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.util.List;

/** Corrección de una venta de puerta: entradas, artículos y forma(s) de pago. El descuento queda igual. */
@Data
public class EditarVentaRequestDTO {
    List<DetalleCompraDTO> entradas;
    List<LineaArticuloPosDTO> articulos;
    FormaPago formaPago;
    /** Pago mixto: la segunda forma y lo que se cobró con ella. Null = se pagó con una sola. */
    FormaPago formaPagoSecundaria;
    java.math.BigDecimal montoFormaPagoSecundaria;
}
