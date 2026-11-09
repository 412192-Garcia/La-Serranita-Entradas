package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.CorreccionVenta;

import java.time.LocalDateTime;

/** Corrección de un boletero sobre una venta suya. */
public record CorreccionVentaDTO(
        Long compraId,
        String codigoReserva,
        LocalDateTime fecha,
        String usuario,
        CorreccionVenta.Tipo tipo,
        String antes,
        String despues,
        String motivo
) {
    public static CorreccionVentaDTO de(CorreccionVenta c) {
        return new CorreccionVentaDTO(c.getCompra().getId(), c.getCompra().getCodigoReserva(), c.getFecha(), c.getUsuario(),
                c.getTipo(), c.getAntes(), c.getDespues(), c.getMotivo());
    }
}
