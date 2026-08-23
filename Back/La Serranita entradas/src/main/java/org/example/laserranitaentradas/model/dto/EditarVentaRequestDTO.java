package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.util.List;

/**
 * Corrección de una venta de puerta mal cargada (ADMIN, desde el detalle de una caja): reemplaza
 * las líneas de entrada, los artículos varios y la forma de pago. El descuento ya aplicado
 * (si lo hay) queda tal cual estaba — no se vuelve a evaluar elegibilidad de promo acá.
 */
@Data
public class EditarVentaRequestDTO {
    List<DetalleCompraDTO> entradas;
    List<LineaArticuloPosDTO> articulos;
    FormaPago formaPago;
}
