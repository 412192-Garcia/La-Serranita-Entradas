package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.DayOfWeek;

@Data
@AllArgsConstructor
public class VentasPorHoraDTO {
    /** Día de semana del momento de la compra (mismo momento que "hora" abajo): el front lo usa
     * para filtrar este gráfico en particular por Lun-Dom sin pedirle de nuevo al back — el
     * resto del resumen no tiene este filtro, sólo esta grilla hora x día. */
    private DayOfWeek diaSemana;
    /** 0-23, hora local del momento de la compra (no de la visita). */
    private int hora;
    private long cantidadComprasAnticipada;
    private long cantidadPasesAnticipada;
    /** Venta en puerta (POS): siempre ocurre dentro del horario de atención del parque. */
    private long cantidadComprasBoleteria;
    private long cantidadPasesBoleteria;
}
