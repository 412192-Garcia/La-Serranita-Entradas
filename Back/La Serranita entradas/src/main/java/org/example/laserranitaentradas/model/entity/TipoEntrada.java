package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "tipos_entrada", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nombre_tipo_entrada", columnNames = "nombre")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class TipoEntrada extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    /** Toda compra con entradas tiene que llevar al menos uno de un tipo obligatorio. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatorio = false;

    /** Cupo máximo vendible por día para este tipo. Null = sin límite. */
    @Column(name = "maximo_por_dia")
    private Integer maximoPorDia;

    /**
     * Vender este tipo consume talonario. Nullable porque ddl-auto no puede agregar NOT NULL a una tabla con
     * filas; null = false.
     */
    @Column(name = "entrega_entrada")
    @Builder.Default
    private Boolean entregaEntrada = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Tipo tipo;

    /** Orden manual en los listados de venta. Nullable por lo mismo que entregaEntrada; null va al final. */
    private Integer orden;

    /** Sólo se vende en el POS, no online. Nullable; null = false. */
    @Column(name = "solo_pos")
    @Builder.Default
    private Boolean soloPos = false;

    /**
     * El POS muestra botones de 1 a 10 y "10+" en vez de +/-. Nullable; null = false
     * (scripts/accesos-rapidos-tipos-entrada.sql completa los existentes).
     */
    @Column(name = "accesos_rapidos")
    @Builder.Default
    private Boolean accesosRapidos = false;

}
