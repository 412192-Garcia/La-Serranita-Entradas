package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Lo que se configura de un Point en Configuración > Points: el nombre ("Boletería 1", para que en
 * las tablets no aparezca como NEWLAND_N950__N950NCB801293324) y si se puede elegir desde las
 * tablets. La lista de Points sale siempre de Mercado Pago; un Point sin fila acá no está
 * habilitado.
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

    /** Aparece en el selector de dispositivos de las tablets. Default en la base para que ddl-auto
     * pueda agregar la columna a una tabla con filas. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean habilitado;

    public TerminalPoint(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
