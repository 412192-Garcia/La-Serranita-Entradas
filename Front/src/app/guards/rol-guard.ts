import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol, SesionService } from '../services/sesion.service';

/**
 * Guard funcional parametrizable por rol.
 *
 * Uso en las rutas:
 *   { path: 'boleteria', component: Boleteria, canActivate: [rolGuard(['BOLETERO', 'ADMIN'])] }
 */
export function rolGuard(rolesPermitidos: Rol[]): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const router = inject(Router);

    if (sesion.tieneAlgunRol(rolesPermitidos)) {
      return true;
    }

    // Sin sesión: al login. Con sesión pero sin el rol necesario: a su propia pantalla,
    // no al sitio público (ya está identificado, solo no tiene permiso para esto puntual).
    if (!sesion.estaAutenticado()) {
      return router.parseUrl('/login');
    }
    return router.parseUrl('/boleteria');
  };
}
