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
}
