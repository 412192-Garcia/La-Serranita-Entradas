import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SesionService } from '../Services/sesion.service';

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
        router.navigateByUrl('/login');
      }
      return throwError(() => error);
    })
  );
};
