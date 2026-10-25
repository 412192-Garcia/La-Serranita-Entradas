package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un navegador con el POS, según su último latido (cada minuto avisa cuántas operaciones tiene sin subir).
 */
@Entity
@Table(name = "terminales_pos")
@Getter
@Setter
@NoArgsConstructor
public class TerminalPos {

    /** Id que el navegador genera una vez y guarda (localStorage). */
    @Id
    @Column(length = 64)
    private String id;

    /** Último usuario logueado en esa terminal. */
    @Column(length = 100)
    private String usuario;

    @Column(name = "ultima_vez", nullable = false)
    private LocalDateTime ultimaVez;

    /** Operaciones esperando para subir (ventas, retiros, ingresos de entradas). */
    @Column(nullable = false)
    private int pendientes;

    /** Las que el servidor rechazó en un reintento y esperan que alguien las mire. */
    @Column(name = "con_error", nullable = false)
    private int conError;

    /** Hora (de la terminal) de la operación pendiente más vieja, o null si no hay. */
    @Column(name = "pendiente_desde")
    private LocalDateTime pendienteDesde;

    @Column(length = 300)
    private String navegador;
}
