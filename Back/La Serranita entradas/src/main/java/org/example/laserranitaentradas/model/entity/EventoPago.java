package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Un paso del comprador en el pago online (se abrió la ventana, se bloqueó, la cerró...). */
@Entity
@Table(name = "eventos_pago", indexes = {
        @Index(name = "idx_eventos_pago_compra", columnList = "id_compra")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra", nullable = false)
    private Compra compra;

    /** Texto y no enum: así sumar un paso no choca con el CHECK que deja ddl-auto. */
    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
