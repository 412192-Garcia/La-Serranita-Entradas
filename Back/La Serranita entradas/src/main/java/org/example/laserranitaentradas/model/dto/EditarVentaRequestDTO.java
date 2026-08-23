package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.util.List;

/**
 * Corrección de una venta de puerta mal cargada (ADMIN, desde el detalle de una caja): sólo
 * reemplaza las líneas de entrada y la forma de pago. Los artículos varios y cualquier
 * descuento ya aplicado quedan tal cual estaban — si lo que está mal es un artículo o el
 * descuento, conviene cancelar la venta y volver a cargarla entera.
 */
@Data
public class EditarVentaRequestDTO {
    List<DetalleCompraDTO> entradas;
    FormaPago formaPago;
}
