package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Una fila del "detalle de caja": una venta o un retiro, para listarlos juntos en orden cronológico. */
@Data
@Builder
public class OperacionCajaDTO {
    /** "VENTA", "ANTICIPADA_VALIDADA", "RETIRO", "APORTE", "INGRESO_ENTRADAS" o "RETIRO_ENTRADAS". */
    private String tipo;
    private LocalDateTime fecha;
    /** Null en los ingresos de entradas físicas: no mueven plata. En una ANTICIPADA_VALIDADA es
     * el valor de la entrada, informativo: esa plata no la cobró esta caja (ver el comentario de
     * Compra.caja), así que no suma a ningún total de "vendido" ni de efectivo esperado. */
    private BigDecimal monto;
    /** Sólo para ventas: parte del monto que corresponde a artículos varios (no entradas). 0 si la venta es sólo entradas. */
    private BigDecimal montoArticulos;
    /** Sólo para ventas: una entrada por cada línea de entrada paga (tipo + cantidad + su parte del monto). */
    private List<SegmentoEntradaDTO> segmentosEntrada;
    /** Ventas: forma en que se cobró. Anticipadas validadas: forma con la que se pagó originalmente. */
    private FormaPago formaPago;
    /** Sólo para ventas: true si se cobró en efectivo-dólares (entró otra moneda, no pesos al cajón). Ese monto no se puede reasignar entre formas de pago. */
    private Boolean pagoEnDolares;
    /** Venta: "2x Pase General, 1x Cuadrito recuerdo". Retiro: el motivo. Ingreso de entradas: "+50 entradas — motivo". */
    private String detalle;
    /** Sólo para ventas: id de la Compra, para poder cancelarla o editarla desde el detalle de caja
     * (ADMIN). Null en retiros/ingresos y también en ANTICIPADA_VALIDADA a propósito: esa compra
     * no pasó por esta caja, así que no se edita ni cancela desde acá (el backend lo rechazaría
     * igual — ver cancelarVenta/editarVenta — esto evita mostrar el botón). */
    private Long compraId;
    /** Sólo en tipo VENTA: true si esta venta es una anticipada (reserva) que se cobró acá — el
     * visitante ya la tenía reservada de antes, no llegó de la calle. A diferencia de
     * ANTICIPADA_VALIDADA, esta plata SÍ es de esta caja (cuenta en todos los totales); sólo
     * cambia la etiqueta para no confundirla con una venta de puerta. Null/false en el resto. */
    private Boolean anticipada;
}
