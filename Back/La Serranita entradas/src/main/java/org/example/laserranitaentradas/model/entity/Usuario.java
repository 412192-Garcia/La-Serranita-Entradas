package org.example.laserranitaentradas.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(name = "uk_username", columnNames = "username")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class Usuario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolUsuario rol;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    /** Color principal elegido por el usuario para personalizar la interfaz (hex, ej "#39a935"). Null = tema por defecto. */
    @Column(name = "color_tema")
    private String colorTema;

    /** Color de fondo de página elegido por el usuario (hex). Null = fondo por defecto. */
    @Column(name = "color_fondo")
    private String colorFondo;

    /** Color de fondo de las tarjetas elegido por el usuario (hex). Null = blanco por defecto. */
    @Column(name = "color_tarjeta")
    private String colorTarjeta;

    /** Color de bordes/divisores elegido por el usuario (hex). Null = gris por defecto. */
    @Column(name = "color_borde")
    private String colorBorde;

    /** Foto de perfil del usuario, como data URI base64 (ya redimensionada y comprimida del lado del cliente). Null = sin foto. */
    @Column(name = "foto_perfil", columnDefinition = "TEXT")
    private String fotoPerfil;

}

