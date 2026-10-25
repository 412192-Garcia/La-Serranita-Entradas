package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lo último que se avisó de cada tarjeta, para avisar sólo los cambios. En la base para que un reinicio no
 * repita todo.
 */
@Entity
@Table(name = "alertas_estado")
@Getter
@Setter
@NoArgsConstructor
public class EstadoAlerta {

    /** Clave de la tarjeta ("facturacion", "backups"...). */
    @Id
    @Column(length = 40)
    private String clave;

    @Column(name = "en_alerta", nullable = false)
    private boolean enAlerta;

    /** Resumen de la tarjeta en el último aviso (si cambia, se vuelve a avisar). */
    @Column(length = 300)
    private String resumen;

    /** Desde cuándo está en alerta. */
    private LocalDateTime desde;

    @Column(name = "ultimo_aviso")
    private LocalDateTime ultimoAviso;
}
