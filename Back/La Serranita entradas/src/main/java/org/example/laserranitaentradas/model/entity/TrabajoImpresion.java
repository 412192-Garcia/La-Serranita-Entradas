package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Un ticket a imprimir en la ticketera de la boletería. El backend no llega a la impresora (el
 * parque está detrás de Starlink, sin IP fija ni puertos abiertos): lo imprime el agente de la PC
 * de la entrada, que mantiene una conexión abierta hacia el backend y recibe los trabajos por ahí.
 *
 * Se guarda en la base para no perder un ticket si la conexión con el agente se corta justo
 * después de mandarlo: al reconectar, se le reenvían los que no confirmó.
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
