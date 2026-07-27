import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export type Rol = 'ADMIN' | 'BOLETERO';

export interface UsuarioSesion {
  id: number;
  username: string;
  nombre: string;
  rol: Rol;
  token: string;
}

interface LoginResponse {
  id: number;
  username: string;
  nombre: string;
  apellido: string;
  rol: Rol;
  token: string;
}

const STORAGE_KEY = 'serranita.sesion';

/**
 * Sesión del módulo interno (boletería/configuración). El login real valida usuario y
 * contraseña contra el backend (POST /api/usuarios/login); lo que se guarda acá es sólo
 * el resultado (sin contraseña) para no tener que loguearse de nuevo en cada pantalla.
 * La autorización real sigue estando en el backend: esto solo evita mostrar pantallas
 * que el usuario no debería ver, no reemplaza un control de acceso del lado del servidor.
 */
@Injectable({
  providedIn: 'root',
})
export class SesionService {
  private http = inject(HttpClient);
  private loginUrl = `${environment.apiBase}/usuarios/login`;

  private usuarioActual = signal<UsuarioSesion | null>(this.leerDeStorage());

  readonly usuario = this.usuarioActual.asReadonly();
  readonly rol = computed(() => this.usuarioActual()?.rol ?? null);
  readonly estaAutenticado = computed(() => this.usuarioActual() !== null);

  tieneAlgunRol(roles: Rol[]): boolean {
    const rol = this.rol();
    return rol !== null && roles.includes(rol);
  }

  token(): string | null {
    return this.usuarioActual()?.token ?? null;
  }

  login(username: string, password: string): Observable<UsuarioSesion> {
    return this.http.post<LoginResponse>(this.loginUrl, { username, password }).pipe(
      tap((res) =>
        this.iniciarSesion({
          id: res.id,
          username: res.username,
          nombre: res.nombre,
          rol: res.rol,
          token: res.token,
        })
      )
    );
  }

  iniciarSesion(usuario: UsuarioSesion): void {
    this.usuarioActual.set(usuario);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(usuario));
  }

  cerrarSesion(): void {
    this.usuarioActual.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  private leerDeStorage(): UsuarioSesion | null {
    try {
      const crudo = localStorage.getItem(STORAGE_KEY);
      return crudo ? (JSON.parse(crudo) as UsuarioSesion) : null;
    } catch {
      return null;
    }
  }
}
