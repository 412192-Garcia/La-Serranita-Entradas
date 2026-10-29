package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/** Souvenirs y otros artículos que se venden en boletería. */
@Entity
@Table(name = "articulos_varios", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nombre_articulo_vario", columnNames = "nombre")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class ArticuloVario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    /** Precio sugerido: el cajero puede ajustarlo al vender. */
    private BigDecimal precioSugerido;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
