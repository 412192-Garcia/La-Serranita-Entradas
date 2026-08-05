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
     * El DNI ya está registrado a nombre de otra persona (ver CompraServiceImpl.create):
     * en el primer intento esto viene en false/null y, si hay discrepancia, el backend
     * rechaza y le pide al frontend confirmar; el reintento lo manda en true.
     */
    Boolean confirmarDniExistente;
    /**
     * Sólo importa junto con confirmarDniExistente=true: si el cliente elige actualizar
     * sus datos, el nombre/apellido/edad/localidad tipeados ahora reemplazan a los que
     * ya estaban guardados; si no, se dejan los viejos tal cual (comportamiento previo).
     */
    Boolean actualizarDatosCliente;
    /**
     * Alternativa a confirmarDniExistente: el usuario aclaró explícitamente que NO es la
     * misma persona que ya tiene este DNI registrado (nombre parecido pero es una casualidad).
     * Se crea un cliente nuevo con el mismo DNI, igual que cuando el nombre es muy distinto,
     * sin tocar los datos de quien ya estaba.
     */
    Boolean esOtraPersona;
}


