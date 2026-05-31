package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "compras", indexes = {
        @Index(name = "idx_dni_comprador", columnList = "dni")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"usuarioValidador", "cupon", "detalles"})
@ToString(callSuper = true, exclude = {"usuarioValidador", "cupon", "detalles"})
@Builder
public class Compra extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String dni;

    @Column(nullable = false)
    private String nombre;

    @Column
    private String email;

    @Column
    private String telefono;

    @Column(nullable = false)
    private LocalDate fechaVisita;

    @Column(nullable = false)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal descuentoAplicado = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoCompra estado = EstadoCompra.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_validador")
    private Usuario usuarioValidador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cupon")
    private Cupon cupon;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CompraDetalle> detalles;

}

