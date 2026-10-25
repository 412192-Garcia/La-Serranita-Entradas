package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.util.List;

/** Corrección de una venta de puerta: entradas, artículos y forma de pago. El descuento queda igual. */
@Data
public class EditarVentaRequestDTO {
    List<DetalleCompraDTO> entradas;
    List<LineaArticuloPosDTO> articulos;
    FormaPago formaPago;
}
