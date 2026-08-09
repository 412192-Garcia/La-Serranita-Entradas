import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { ThemeService } from './theme.service';

export type Rol = 'ADMIN' | 'BOLETERO';

export interface UsuarioSesion {
  id: number;
  username: string;
  nombre: string;
  rol: Rol;
  token: string;
  /** Color principal elegido en "Mi cuenta". Null = tema por defecto. */
  colorTema: string | null;
  /** Color de fondo de página elegido en "Mi cuenta". Null = fondo por defecto. */
  colorFondo: string | null;
  /** Color de tarjetas elegido en "Mi cuenta". Null = blanco por defecto. */
  colorTarjeta: string | null;
  /** Color de bordes/divisores elegido en "Mi cuenta". Null = gris por defecto. */
  colorBorde: string | null;
  /** Foto de perfil elegida en "Mi cuenta" (data URI base64). Null = sin foto. */
  fotoPerfil: string | null;
}

interface LoginResponse {
  id: number;
  username: string;
  nombre: string;
  apellido: string;
  rol: Rol;
  token: string;
  colorTema: string | null;
  colorFondo: string | null;
  colorTarjeta: string | null;
  colorBorde: string | null;
  fotoPerfil: string | null;
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
  private theme = inject(ThemeService);
  private loginUrl = `${environment.apiBase}/usuarios/login`;

  private usuarioActual = signal<UsuarioSesion | null>(this.leerDeStorage());

  readonly usuario = this.usuarioActual.asReadonly();
  readonly rol = computed(() => this.usuarioActual()?.rol ?? null);
  readonly estaAutenticado = computed(() => this.usuarioActual() !== null);

  constructor() {
    // Al recargar la página no queda nada de :root pisado (era un estilo inline en memoria),
    // así que hay que reaplicar el tema guardado apenas arranca el servicio.
    this.aplicarTema(this.usuarioActual());
  }

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
          colorTema: res.colorTema,
          colorFondo: res.colorFondo,
          colorTarjeta: res.colorTarjeta,
          colorBorde: res.colorBorde,
          fotoPerfil: res.fotoPerfil,
        })
      )
    );
  }

  iniciarSesion(usuario: UsuarioSesion): void {
    this.usuarioActual.set(usuario);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(usuario));
    this.aplicarTema(usuario);
  }

  /** Actualiza los colores personalizados de la sesión activa (después de guardarlos en "Mi cuenta"), sin tocar el resto de los datos ni pedir un nuevo login. */
  actualizarColores(colorTema: string | null, colorFondo: string | null, colorTarjeta: string | null, colorBorde: string | null): void {
    const actual = this.usuarioActual();
    if (!actual) return;
    const actualizado = { ...actual, colorTema, colorFondo, colorTarjeta, colorBorde };
    this.usuarioActual.set(actualizado);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(actualizado));
    this.aplicarTema(actualizado);
  }

  /** Actualiza la foto de perfil de la sesión activa (después de guardarla en "Mi cuenta"), sin tocar el resto de los datos ni pedir un nuevo login. */
  actualizarFoto(fotoPerfil: string | null): void {
    const actual = this.usuarioActual();
    if (!actual) return;
    const actualizado = { ...actual, fotoPerfil };
    this.usuarioActual.set(actualizado);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(actualizado));
  }

  cerrarSesion(): void {
    this.usuarioActual.set(null);
    localStorage.removeItem(STORAGE_KEY);
    this.theme.aplicarPrimario(null);
    this.theme.aplicarFondo(null);
    this.theme.aplicarTarjeta(null);
    this.theme.aplicarBorde(null);
  }

  private aplicarTema(usuario: UsuarioSesion | null): void {
    this.theme.aplicarPrimario(usuario?.colorTema ?? null);
    this.theme.aplicarFondo(usuario?.colorFondo ?? null);
    this.theme.aplicarTarjeta(usuario?.colorTarjeta ?? null);
    this.theme.aplicarBorde(usuario?.colorBorde ?? null);
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
