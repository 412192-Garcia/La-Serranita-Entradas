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
 * salvo que un DiaApertura puntual tenga su propio horaApertura/horaCierre ("horario especial").
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

}
