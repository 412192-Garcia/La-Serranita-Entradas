package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.EstadoCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Compras online (Mercado Pago) empezadas en el rango y qué pasó con cada una: pagadas, abandonadas, rechazadas
 * o todavía pendientes. Las fechas son de creación de la compra.
 */
public record ComprasSinCompletarDTO(
        long iniciadas,
        long pagadas,
        /** Abrió el pago y no lo intentó. */
        long abandonadas,
        /** Intentó pagar y Mercado Pago no lo aprobó. */
        long rechazadas,
        /** No se pudo generar el link de pago. */
        long noIniciadas,
        /** Todavía esperando el pago (se cancelan solas a las pocas horas). */
        long pendientes,
        /** Canceladas por otro motivo o sin motivo registrado (compras viejas). */
        long otras,
        /** Sin completar y sin otra compra después de la misma persona: plata que se perdió. */
        long noRecuperadas,
        BigDecimal montoNoRecuperado,
        /** Hasta dónde llegaron las abandonadas. */
        List<Motivo> pasosAbandonadas,
        List<Motivo> motivosRechazo,
        /** Por dispositivo y navegador, y por si vino del sitio o directo. */
        List<Grupo> porDispositivo,
        List<Grupo> porOrigen,
        Perfil perfilPagadas,
        Perfil perfilSinCompletar,
        List<Dia> porDia,
        List<Intento> intentos
) {
    public record Motivo(String texto, long cantidad) {}

    public record Grupo(String nombre, long iniciadas, long pagadas) {}

    /** Cómo son las compras de un grupo, para comparar pagadas con las que no. */
    public record Perfil(long cantidad, double entradasPromedio, BigDecimal montoPromedio,
                         double diasAnticipacionPromedio, double porcentajeConCupon, double porcentajeRegalo) {}

    public record Dia(LocalDate fecha, long iniciadas, long pagadas) {}

    public record Intento(Long id, String codigoReserva, LocalDateTime fechaCreacion, LocalDate fechaVisita,
                          BigDecimal monto, long entradas, EstadoCompra estado, String motivo, String detalle,
                          String nombre, String email, String telefono,
                          String dispositivo, String origen,
                          /** Los pasos registrados, con la hora: "10:01 Se abrió Mercado Pago". */
                          List<String> pasos,
                          /** Código de la compra que hizo después, si la hizo. */
                          String compraPosterior) {}
}
