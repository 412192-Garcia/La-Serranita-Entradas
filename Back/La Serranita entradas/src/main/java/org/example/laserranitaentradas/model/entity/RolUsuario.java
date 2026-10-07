package org.example.laserranitaentradas.model.entity;

/**
 * SUPERADMIN = ADMIN + Sistema (monitoreo, errores, historial de acciones): es el rol del que
 * mantiene la app, no del parque. Hereda todo lo de ADMIN por la jerarquía de SecurityConfig, así
 * que las reglas "hasRole(ADMIN)" no lo nombran. No se puede asignar desde Usuarios (sólo otro
 * SUPERADMIN, el bootstrap o SQL).
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
