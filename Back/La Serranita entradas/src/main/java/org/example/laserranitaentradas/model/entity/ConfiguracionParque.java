package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalTime;

/**
 * Configuración global del parque (fila única, id=1): horario por defecto que rige
 * salvo que un DiaApertura puntual tenga su propio horaApertura/horaCierre ("horario especial"),
 * y el lapso antes del cierre en que se corta la compra online para el mismo día.
 */
@Entity
@Table(name = "configuracion_parque")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class ConfiguracionParque extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    /**
     * Cuántos minutos antes del cierre del parque se corta la compra online (anticipada) para el día
     * de HOY: pasado ese momento ya no se puede comprar para hoy, aunque el parque siga abierto. Rige
     * también con el horario especial de un día puntual (se mide contra el cierre de ese día). 0 = se
     * puede comprar hasta la hora de cierre. columnDefinition con default: la fila ya existe en la
     * base y ddl-auto=update no puede agregar una columna NOT NULL sin valor a una tabla con filas.
     */
    @Column(name = "minutos_limite_compra", nullable = false, columnDefinition = "integer default 60")
    @Builder.Default
    private Integer minutosLimiteCompra = 60;

}
