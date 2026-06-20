package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CompraRequestDTO {
    ClienteDTO cliente;
    LocalDate fecha;
    String cuponCodigo;
    List<DetalleCompraDTO> entradas;
}


