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
    /** Sólo cuando es un regalo (fecha null): a quién avisarle. */
    ReceptorRegaloDTO receptor;
    /**
     * True en el reintento, después de que el front confirmó que el DNI ya registrado es de la misma persona.
     */
    Boolean confirmarDniExistente;
    /** Con confirmarDniExistente: si true, los datos nuevos reemplazan a los guardados. */
    Boolean actualizarDatosCliente;
    /** El usuario aclaró que NO es la misma persona: cliente nuevo con el mismo DNI. */
    Boolean esOtraPersona;
}


