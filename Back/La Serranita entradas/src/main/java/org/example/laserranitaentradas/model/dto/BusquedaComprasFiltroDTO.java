package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros de la búsqueda de boletería, todos opcionales. "fecha" es un día; "fechaDesde"/"fechaHasta" un
 * rango. "sinFecha" trae sólo regalos y excluye a los otros tres.
 */
public record BusquedaComprasFiltroDTO(
        String texto,
        LocalDate fecha,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Boolean sinFecha,
        TipoListadoCompra tipo,
        List<EstadoCompra> estados,
        FormaPago formaPago
) {
}
