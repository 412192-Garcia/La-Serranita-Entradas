package org.example.laserranitaentradas.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "cupones", uniqueConstraints = {
        @UniqueConstraint(name = "uk_codigo_cupon", columnNames = "codigo")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"compras"})
@ToString(callSuper = true, exclude = {"compras"})
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

    @Column(nullable = false)
    private LocalDate fechaExpiracion;

    @Column(nullable = false)
    private Integer usosMaximos;

    @Column(nullable = false)
    @Builder.Default
    private Integer usosActuales = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @OneToMany(mappedBy = "cupon", fetch = FetchType.LAZY)
    private List<Compra> compras;

}

