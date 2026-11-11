package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Papel que se entrega al validar una anticipada (o a mano, desde Acciones): un extra o plata para la cantina. */
@Entity
@Table(name = "vouchers", indexes = {
        @Index(name = "idx_vouchers_compra", columnList = "id_compra")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"compra", "emitidoPor"})
@ToString(callSuper = true, exclude = {"compra", "emitidoPor"})
@Builder
public class Voucher extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código impreso: el de la reserva más un número. */
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    /** Null en los manuales. En una base existente hace falta scripts/vouchers-manuales.sql. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra")
    private Compra compra;

    /** A nombre de quién va, en los manuales. Los de una compra lo sacan de la compra. */
    @Column(length = 120)
    private String titular;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoVoucher tipo;

    /** EXTRA: el nombre del extra. CANTINA: qué entradas lo generaron. */
    @Column(nullable = false, length = 200)
    private String descripcion;

    /** EXTRA: cuántos vale. */
    private Integer cantidad;

    /** CANTINA: cuánto vale. */
    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_emisor")
    private Usuario emitidoPor;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    /** Se deshizo la validación: el papel ya no vale. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean anulado = false;
}
