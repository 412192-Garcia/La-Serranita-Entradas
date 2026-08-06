package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cuántos billetes de una denominación puntual (ej: 2000) contó el boletero al cerrar la caja. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConteoDenominacion {

    @Column(name = "denominacion", nullable = false)
    private Integer denominacion;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;
}
