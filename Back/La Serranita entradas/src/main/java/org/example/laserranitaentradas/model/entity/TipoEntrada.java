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

    /** Si es true, toda compra que incluya entradas debe tener al menos una unidad de algún tipo obligatorio (ej: un adulto responsable). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean obligatorio = false;

    /** Cupo máximo vendible por día para este tipo. Null = sin límite. */
    @Column(name = "maximo_por_dia")
    private Integer maximoPorDia;

    /**
     * Si es true, vender este tipo consume talonario físico (ej: Pase General adulto); los
     * tipos de menores no entregan entrada física. Sin nullable=false a propósito: agregar
     * una columna NOT NULL con Hibernate ddl-auto=update falla contra una tabla que ya tiene
     * filas (Postgres no puede completar el default solo). Se trata como false si es null.
     */
    @Column(name = "entrega_entrada")
    @Builder.Default
    private Boolean entregaEntrada = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Tipo tipo;

}
