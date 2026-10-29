package org.example.laserranitaentradas.model.entity;

/**
 * SUPERADMIN = ADMIN + Sistema: el rol de quien mantiene la app. Hereda ADMIN por la jerarquía. No se asigna
 * desde Usuarios.
 */
public enum RolUsuario {
    SUPERADMIN,
    ADMIN,
    BOLETERO;

    /** Para los chequeos a mano en el código ("si es admin, puede..."): el SUPERADMIN también cuenta. */
    public boolean esAdmin() {
        return this == ADMIN || this == SUPERADMIN;
    }
}
