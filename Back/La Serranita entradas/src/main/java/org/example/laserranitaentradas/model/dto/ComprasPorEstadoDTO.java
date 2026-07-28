package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.EstadoCompra;

@Data
@AllArgsConstructor
public class ComprasPorEstadoDTO {
    private EstadoCompra estado;
    private long cantidad;
}
