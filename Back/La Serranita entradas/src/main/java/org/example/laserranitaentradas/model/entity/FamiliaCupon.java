package org.example.laserranitaentradas.model.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Entity
@Table(name = "familias_cupones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"cupones"})
@ToString(callSuper = true, exclude = {"cupones"})
public class FamiliaCupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String prefijo;

    @Column
    private String descripcion;

    @OneToMany(mappedBy = "familiaCupon", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Cupon> cupones;

}
