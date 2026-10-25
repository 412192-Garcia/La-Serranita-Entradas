package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Un traspaso entre formas de pago del resumen de cierre. */
@Data
public class AjusteCajaRequestDTO {
    private FormaPago formaOrigen;
    private FormaPago formaDestino;
    private BigDecimal monto;
    private Integer cantidadVentas;
    private String detalle;
    private String nota;
    private List<Long> comprasMovidas;
    /** Composición de una venta del ajuste: id de tipo de entrada → cantidad de pases. */
    private Map<Long, Integer> lineas;
}
