package org.example.laserranitaentradas.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.time.LocalDate;
import java.util.List;

@Data
public class CompraRequestDTO {
    ClienteDTO cliente;
    LocalDate fecha;
    String cuponCodigo;
    FormaPago formaPago;
    List<DetalleCompraDTO> entradas;
}


