package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Configuración de un Point: nombre visible y si se elige desde las tablets. La lista sale de Mercado Pago;
 * sin fila acá no está habilitado.
 */
@Entity
@Table(name = "terminales_point")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class TerminalPoint extends BaseEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** Aparece en las tablets. Con default para ddl-auto. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean habilitado;

    public TerminalPoint(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
