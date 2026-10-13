package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Si un {@link Modulo} está prendido. Sólo hay fila para los que alguien tocó alguna vez: el resto
 * usa {@link Modulo#isActivoPorDefecto()}. Quién y cuándo lo cambió queda en los campos de auditoría.
 */
@Entity
@Table(name = "modulos_sistema")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class ModuloSistema extends BaseEntity {

    /** Nombre del {@link Modulo}. Texto y no @Enumerated: ver el comentario del enum. */
    @Id
    @Column(length = 40)
    private String codigo;

    @Column(nullable = false)
    private boolean activo;

    public ModuloSistema(String codigo, boolean activo) {
        this.codigo = codigo;
        this.activo = activo;
    }
}
