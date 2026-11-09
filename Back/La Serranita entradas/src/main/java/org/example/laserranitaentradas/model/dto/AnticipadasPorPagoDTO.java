package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Reservas anticipadas según cómo se pagaron: online (Mercado Pago), al llegar, o todavía sin cobrar. */
@Data
@AllArgsConstructor
public class AnticipadasPorPagoDTO {
    /** ONLINE, AL_LLEGAR o SIN_COBRAR. */
    private String clave;
    private String etiqueta;
    private long cantidad;
    private long entradas;
    private BigDecimal monto;
}
