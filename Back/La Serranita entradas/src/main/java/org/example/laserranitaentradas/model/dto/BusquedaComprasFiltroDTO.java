package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.time.LocalDate;
import java.util.List;

/** Filtros de la búsqueda paginada de boletería (GET /api/compras/buscar). Todos opcionales. */
public record BusquedaComprasFiltroDTO(
        String texto,
        LocalDate fecha,
        TipoListadoCompra tipo,
        List<EstadoCompra> estados,
        FormaPago formaPago
) {
}
