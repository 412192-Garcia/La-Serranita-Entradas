package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/** Entradas físicas que se suman o restan al talonario de una caja abierta. */
@Entity
@Table(name = "ingresos_entradas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"caja"})
@ToString(callSuper = true, exclude = {"caja"})
@Builder
public class IngresoEntradas extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @Column(nullable = false)
    private Integer cantidad;

    /** Opcional, para los RETIRO. */
    private String motivo;

    /** Default 'INGRESO' para que ddl-auto la pueda agregar como NOT NULL. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'INGRESO'")
    private TipoMovimientoEntradas tipo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Clave del POS para que un reintento no duplique. Null fuera de la cola offline. */
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;
}
