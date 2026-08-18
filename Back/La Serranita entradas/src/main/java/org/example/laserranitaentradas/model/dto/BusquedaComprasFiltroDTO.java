package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.time.LocalDate;
import java.util.List;

/** Filtros de la búsqueda paginada de boletería (GET /api/compras/buscar). Todos opcionales.
 * "fecha" es un día puntual; "fechaDesde"/"fechaHasta" son un rango (usado por la vista
 * agrupada por día de Boletería) — se pueden usar solos (rango abierto) o combinados.
 * "sinFecha" es mutuamente excluyente con los tres anteriores: trae sólo los regalos
 * (fechaVisita null) — una comparación fechaDesde/fechaHasta nunca matchea NULL en SQL, así
 * que no hay forma de pedirlos con esos filtros. */
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
