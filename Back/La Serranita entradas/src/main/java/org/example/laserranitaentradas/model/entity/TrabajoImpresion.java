package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Un ticket para la ticketera. Lo imprime el agente de la PC de la entrada (el backend no llega a la red del
 * parque). Se guarda para reenviarlo si se corta la conexión.
 */
@Entity
@Table(name = "trabajos_impresion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"factura"})
@ToString(callSuper = true, exclude = {"factura"})
@Builder
public class TrabajoImpresion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_factura", nullable = false)
    private Factura factura;

    /** Nombre de la impresora tal como la anuncia el agente (ej. "Boletería"). */
    @Column(nullable = false, length = 60)
    private String impresora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoTrabajoImpresion estado = EstadoTrabajoImpresion.PENDIENTE;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_resultado")
    private LocalDateTime fechaResultado;

    @Column(length = 500)
    private String error;
}
