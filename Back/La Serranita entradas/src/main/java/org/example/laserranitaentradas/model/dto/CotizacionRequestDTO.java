package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.util.List;

@Data
public class CotizacionRequestDTO {
    FormaPago formaPago;
    List<DetalleCompraDTO> entradas;
}
