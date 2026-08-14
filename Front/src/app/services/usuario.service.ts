import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Rol } from './sesion.service';

export interface Usuario {
  id: number;
  username: string;
  nombre: string;
  apellido: string;
  rol: Rol;
  activo: boolean;
  colorTema: string | null;
  colorFondo: string | null;
  colorTarjeta: string | null;
  colorBorde: string | null;
  fotoPerfil: string | null;
}

export interface CrearUsuarioRequest {
  username: string;
  /** Sólo se manda al crear, o al editar si se quiere cambiar. En blanco = no tocar la existente. */
  password?: string;
  nombre: string;
  apellido: string;
  rol: Rol;
  activo: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class UsuarioService {
  private http = inject(HttpClient);
  private usuariosUrl = `${environment.apiBase}/usuarios`;

  getUsuarios(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.usuariosUrl);
  }

  crear(usuario: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.usuariosUrl, usuario);
  }

  actualizar(id: number, usuario: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.usuariosUrl}/${id}`, usuario);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.usuariosUrl}/${id}`);
  }

  /** Self-service: cualquier usuario logueado puede cambiar su propia contraseña (verificando la actual). */
  cambiarMiPassword(passwordActual: string, passwordNueva: string): Observable<void> {
    return this.http.put<void>(`${this.usuariosUrl}/me/password`, { passwordActual, passwordNueva });
  }

  /** Self-service: cualquier usuario logueado puede personalizar su color principal, de fondo, de tarjetas y de bordes. Null en cualquiera vuelve al valor por defecto. */
  cambiarMiTema(colorTema: string | null, colorFondo: string | null, colorTarjeta: string | null, colorBorde: string | null): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.usuariosUrl}/me/tema`, { colorTema, colorFondo, colorTarjeta, colorBorde });
  }

  /** Self-service: cualquier usuario logueado puede subir o sacar su propia foto de perfil (data URI base64, ya redimensionada del lado del cliente). Null la saca. */
  cambiarMiFoto(fotoPerfil: string | null): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.usuariosUrl}/me/foto`, { fotoPerfil });
  }
}
