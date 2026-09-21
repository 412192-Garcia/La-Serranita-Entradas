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

export interface CuentaReciente {
  username: string;
  nombre: string;
  rol: Rol;
  fotoPerfil: string | null;
}

const STORAGE_KEY = 'serranita.sesion';
const CUENTAS_KEY = 'serranita.cuentasRecientes';
const MAX_CUENTAS_RECIENTES = 5;

/** Cuánto antes del vencimiento se renueva el token de "Mantener sesión iniciada" (el backend lo
 * emite por 14 días, ver `jwt.remember-expiration-ms`): con 7 quedan siempre días de margen. */
const UMBRAL_RENOVACION_MS = 7 * 24 * 60 * 60 * 1000;

/** Lo que este cliente necesita leer del JWT: cuándo vence (ms epoch) y si es de "Mantener sesión
 * iniciada". Null si no se puede leer. No valida la firma: es sólo para decidir cuándo pedir uno
 * nuevo; quien decide si vale es el backend. */
export function leerToken(token: string): { venceEn: number; sesionLarga: boolean } | null {
  try {
    const segmento = token.split('.')[1];
    if (!segmento) return null;
    const base64 = segmento.replace(/-/g, '+').replace(/_/g, '/');
    // El JWT va en base64url, sin el "=" de relleno. atob lo tolera (es lo que dice la especificación),
    // pero completarlo no cuesta nada y evita depender de esa tolerancia.
    const payload = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const { exp, mantener } = JSON.parse(atob(payload));
    return typeof exp === 'number' ? { venceEn: exp * 1000, sesionLarga: mantener === true } : null;
  } catch {
    return null;
  }
}

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
  private renovarUrl = `${environment.apiBase}/usuarios/renovar-sesion`;
  private renovacionIntentada = false;

  private usuarioActual = signal<UsuarioSesion | null>(this.leerDeStorage());
  private cuentasRecientesActual = signal<CuentaReciente[]>(this.leerCuentasRecientes());

  readonly usuario = this.usuarioActual.asReadonly();
  readonly rol = computed(() => this.usuarioActual()?.rol ?? null);
  readonly estaAutenticado = computed(() => this.usuarioActual() !== null);
  readonly cuentasRecientes = this.cuentasRecientesActual.asReadonly();

  tieneAlgunRol(roles: Rol[]): boolean {
    const rol = this.rol();
    return rol !== null && roles.includes(rol);
  }

  token(): string | null {
    return this.usuarioActual()?.token ?? null;
  }

  /** `mantener`: la sesión sobrevive a cerrar el navegador (localStorage) y el backend emite un
   * token de varios días (`jwt.remember-expiration-ms`), para no tener que ingresar cada día. Sin
   * él, dura lo que la pestaña (sessionStorage) y el token, un turno (`jwt.expiration-ms`). Cuando
   * el token vence, el interceptor cierra la sesión. */
  login(username: string, password: string, mantener = true): Observable<UsuarioSesion> {
    return this.http.post<LoginResponse>(this.loginUrl, { username, password, mantenerSesion: mantener }).pipe(
      tap((res) => this.iniciarSesion(this.aUsuarioSesion(res), mantener))
    );
  }

  private aUsuarioSesion(res: LoginResponse): UsuarioSesion {
    return {
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
    };
  }

  /**
   * Sesión deslizante de "Mantener sesión iniciada": cuando al token le quedan menos de
   * UMBRAL_RENOVACION_MS, pide uno nuevo (otra vuelta de la duración larga) — así quien usa la app
   * seguido no vuelve a ingresar nunca, y quien la deja de usar más que esa duración sí.
   *
   * Sólo aplica a sesiones guardadas en localStorage (las de pestaña son de un turno y no se
   * renuevan) y se intenta una vez por carga de la página. Si el backend responde 401 (usuario
   * dado de baja) lo maneja el interceptor cerrando la sesión; cualquier otro fallo (sin conexión,
   * un token de turno viejo que el backend no renueva) se ignora: es un intento de oportunidad.
   */
  renovarSiHaceFalta(): void {
    const actual = this.usuarioActual();
    if (!actual || this.renovacionIntentada || localStorage.getItem(STORAGE_KEY) === null) return;

    // Un token de turno (sin el claim `mantener`) el backend no lo renueva: ni se intenta.
    const token = leerToken(actual.token);
    if (!token?.sesionLarga || token.venceEn - Date.now() > UMBRAL_RENOVACION_MS) return;

    this.renovacionIntentada = true;
    this.http.post<LoginResponse>(this.renovarUrl, {}).subscribe({
      next: (res) => {
        // Sin aplicarTema/recordarCuenta a propósito: esto corre también con la ruta pública
        // abierta y no es un login nuevo; el tema lo aplica App en cada navegación.
        const renovado = this.aUsuarioSesion(res);
        this.usuarioActual.set(renovado);
        this.guardarSesion(renovado, true);
      },
      // Sin conexión (status 0): se reintenta en la próxima navegación en vez de esperar a recargar.
      error: (err) => {
        if (err?.status === 0) this.renovacionIntentada = false;
      },
    });
  }

  iniciarSesion(usuario: UsuarioSesion, mantener = true): void {
    this.usuarioActual.set(usuario);
    this.guardarSesion(usuario, mantener);
    this.aplicarTema(usuario);
    this.recordarCuenta(usuario);
  }

  /** Guarda la sesión en un solo lugar: si quedara una copia en el otro storage, `leerDeStorage`
   * podría levantar una sesión vieja después de cerrar la nueva. */
  private guardarSesion(usuario: UsuarioSesion, persistente: boolean): void {
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    (persistente ? localStorage : sessionStorage).setItem(STORAGE_KEY, JSON.stringify(usuario));
  }

  /** Actualiza la sesión activa en el storage donde ya estaba (no la "promueve" a persistente). */
  private reescribirSesion(usuario: UsuarioSesion): void {
    this.guardarSesion(usuario, localStorage.getItem(STORAGE_KEY) !== null);
  }

  /** Saca esa cuenta de la lista de "usados recientemente" en este dispositivo (botón "x" del login). */
  quitarCuentaReciente(username: string): void {
    const restantes = this.cuentasRecientesActual().filter((c) => c.username !== username);
    this.cuentasRecientesActual.set(restantes);
    localStorage.setItem(CUENTAS_KEY, JSON.stringify(restantes));
  }

  private recordarCuenta(usuario: UsuarioSesion): void {
    const otras = this.cuentasRecientesActual().filter((c) => c.username !== usuario.username);
    const nueva: CuentaReciente = {
      username: usuario.username,
      nombre: usuario.nombre,
      rol: usuario.rol,
      fotoPerfil: usuario.fotoPerfil,
    };
    const actualizadas = [nueva, ...otras].slice(0, MAX_CUENTAS_RECIENTES);
    this.cuentasRecientesActual.set(actualizadas);
    localStorage.setItem(CUENTAS_KEY, JSON.stringify(actualizadas));
  }

  /** Actualiza los colores personalizados de la sesión activa (después de guardarlos en "Mi cuenta"), sin tocar el resto de los datos ni pedir un nuevo login. */
  actualizarColores(colorTema: string | null, colorFondo: string | null, colorTarjeta: string | null, colorBorde: string | null): void {
    const actual = this.usuarioActual();
    if (!actual) return;
    const actualizado = { ...actual, colorTema, colorFondo, colorTarjeta, colorBorde };
    this.usuarioActual.set(actualizado);
    this.reescribirSesion(actualizado);
    this.aplicarTema(actualizado);
  }

  /** Actualiza la foto de perfil de la sesión activa (después de guardarla en "Mi cuenta"), sin tocar el resto de los datos ni pedir un nuevo login. */
  actualizarFoto(fotoPerfil: string | null): void {
    const actual = this.usuarioActual();
    if (!actual) return;
    const actualizado = { ...actual, fotoPerfil };
    this.usuarioActual.set(actualizado);
    this.reescribirSesion(actualizado);
  }

  cerrarSesion(): void {
    this.usuarioActual.set(null);
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    this.theme.aplicarPrimario(null);
    this.theme.aplicarFondo(null);
    this.theme.aplicarTarjeta(null);
    this.theme.aplicarBorde(null);
  }

  /**
   * Pisa las variables CSS de tema con las del usuario (o las resetea al verde por defecto si
   * es null). Al recargar la página no queda nada de :root pisado (era un estilo inline en
   * memoria) — quien decide CUÁNDO llamar a esto es el componente raíz (ver App), no este
   * servicio: el módulo público (compra de entradas) comparte el mismo bundle/injector que el
   * módulo interno, así que si esto se aplicara solo, un dispositivo con sesión de staff
   * guardada terminaría pintando la compra pública con el color personalizado del staff en vez
   * del verde fijo del parque.
   */
  aplicarTema(usuario: UsuarioSesion | null): void {
    this.theme.aplicarPrimario(usuario?.colorTema ?? null);
    this.theme.aplicarFondo(usuario?.colorFondo ?? null);
    this.theme.aplicarTarjeta(usuario?.colorTarjeta ?? null);
    this.theme.aplicarBorde(usuario?.colorBorde ?? null);
  }

  private leerDeStorage(): UsuarioSesion | null {
    try {
      // sessionStorage primero: una sesión sin "mantener" vive sólo ahí; una persistente, en localStorage.
      const crudo = sessionStorage.getItem(STORAGE_KEY) ?? localStorage.getItem(STORAGE_KEY);
      return crudo ? (JSON.parse(crudo) as UsuarioSesion) : null;
    } catch {
      return null;
    }
  }

  private leerCuentasRecientes(): CuentaReciente[] {
    try {
      const crudo = localStorage.getItem(CUENTAS_KEY);
      return crudo ? (JSON.parse(crudo) as CuentaReciente[]) : [];
    } catch {
      return [];
    }
  }
}
