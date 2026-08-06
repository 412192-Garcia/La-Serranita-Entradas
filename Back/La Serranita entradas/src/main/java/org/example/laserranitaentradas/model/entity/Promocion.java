package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Descuento con nombre para la venta en puerta (ej: "Folleto Verano 2026 — 15%"), a
 * diferencia del cupón online (que es por código) o del descuento manual ad-hoc que el
 * cajero puede tipear directo en la venta sin necesidad de una promo cargada acá.
 */
@Entity
@Table(name = "promociones", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nombre_promocion", columnNames = "nombre")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class Promocion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    /** Exactamente uno de los dos, validado en el service. */
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
