package org.example.laserranitaentradas.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "cupones", uniqueConstraints = {
        @UniqueConstraint(name = "uk_codigo_cupon", columnNames = "codigo")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"compras", "familiaCupon"})
@ToString(callSuper = true, exclude = {"compras", "familiaCupon"})
@Builder
public class Cupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column
    private BigDecimal porcentajeDescuento;

    @Column
    private BigDecimal montoDescuento;

    /** null = sin vencimiento. */
    @Column
    private LocalDate fechaExpiracion;

    /** null = sin límite de usos. */
    @Column
    private Integer usosMaximos;

    /**
     * Primer día de compra en que el cupón vale (inclusive). null = desde siempre. El último
     * día es fechaExpiracion.
     */
    @Column
    private LocalDate fechaDesde;

    /**
     * Sobre qué se aplica el monto fijo. columnDefinition con default porque la tabla ya
     * tiene filas y ddl-auto=update no puede agregar una columna NOT NULL sin él.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'COMPRA' not null")
    @Builder.Default
    private AplicacionDescuento aplicaPor = AplicacionDescuento.COMPRA;

    /** Tipos de entrada a los que alcanza. Vacío = todos. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cupon_tipos_entrada", joinColumns = @JoinColumn(name = "cupon_id"))
    @Column(name = "tipo_entrada_id")
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private Set<Long> tiposEntradaIds = new HashSet<>();

    /** Cantidad mínima de entradas (de las que el cupón alcanza) para poder usarlo. null = sin mínimo. */
    @Column
    private Integer minEntradas;

    /** Cantidad máxima de entradas que reciben el descuento (las más caras primero). null = todas. */
    @Column
    private Integer maxEntradasAfectadas;

    /** Descuento máximo en pesos por compra. null = sin tope. */
    @Column
    private BigDecimal topeDescuento;

    @Column(nullable = false)
    @Builder.Default
    private Integer usosActuales = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @OneToMany(mappedBy = "cupon", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Compra> compras;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "familia_cupon_id")
    @JsonBackReference
    private FamiliaCupon familiaCupon;

}
