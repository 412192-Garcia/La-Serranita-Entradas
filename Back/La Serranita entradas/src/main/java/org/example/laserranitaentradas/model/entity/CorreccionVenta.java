package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Corrección que hizo un boletero sobre una venta suya. Las del admin no se registran acá. */
@Entity
@Table(name = "correcciones_venta", indexes = {
        @Index(name = "idx_correcciones_venta_caja", columnList = "id_caja"),
        @Index(name = "idx_correcciones_venta_compra", columnList = "id_compra")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"compra", "caja"})
@ToString(exclude = {"compra", "caja"})
@Builder
public class CorreccionVenta {

    public enum Tipo { EDICION, CANCELACION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compra", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    /** "Efectivo · 2× Pase General · $68.600". */
    @Column(nullable = false, length = 500)
    private String antes;

    /** Null en una cancelación. */
    @Column(length = 500)
    private String despues;

    @Column(length = 300)
    private String motivo;
}
