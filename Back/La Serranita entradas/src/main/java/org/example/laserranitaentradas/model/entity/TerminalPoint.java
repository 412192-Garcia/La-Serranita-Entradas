package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Nombre que se le dio a un Point en Sistema > Módulos ("Boletería 1"), para que en las tablets
 * no aparezca como NEWLAND_N950__N950NCB801293324. La lista de Points sale siempre de Mercado
 * Pago; acá sólo está el nombre.
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

    public TerminalPoint(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
