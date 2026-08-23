import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SesionService } from '../services/sesion.service';
import { mostrarAvisoGlobal } from '../shared/aviso-global.util';

/** Adjunta el JWT de la sesión a cada request y cierra sesión si el backend responde 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const token = sesion.token();

  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error) => {
      if (error.status === 401 && token) {
        sesion.cerrarSesion();
        // El aviso vive fuera del árbol de Angular (ver aviso-global.util.ts), así que sobrevive
        // al redirect: sin esto, quien estaba a mitad de una venta aterrizaba en el login sin
        // ningún indicio de por qué lo mandaron ahí.
        mostrarAvisoGlobal('Tu sesión expiró: iniciá sesión de nuevo.');
        router.navigateByUrl('/login');
      }
      return throwError(() => error);
    })
  );
};
