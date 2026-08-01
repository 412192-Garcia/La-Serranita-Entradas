package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.util.List;

/**
 * Venta presencial en la boletería: el visitante paga y entra en el acto, así que
 * no se cargan datos del cliente (no hay nada que validar después).
 */
@Data
public class VentaPosRequestDTO {
    /** Cómo cobró el boletero. Define además si corresponde el precio promocional por grupo. */
    FormaPago formaPago;
    List<DetalleCompraDTO> entradas;
}
