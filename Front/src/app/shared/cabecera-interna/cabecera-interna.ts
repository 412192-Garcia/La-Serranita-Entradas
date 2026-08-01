import { Component, Input, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { SesionService } from '../../Services/sesion.service';

/** Enlace opcional que cada pantalla agrega a su cabecera (ir a la otra sección). */
export interface EnlaceCabecera {
  texto: string;
  ruta: string;
}

/**
 * Cabecera común de las pantallas del módulo interno (Boletería y Configuración):
 * título, bajada, enlace a la otra sección, nombre del operador y cierre de sesión.
 *
 * Antes cada pantalla tenía su propia copia del mismo HTML, CSS y método
 * cerrarSesion(); las copias ya habían empezado a divergir (a Boletería le
 * faltaban cursor/font-family en el botón, así que se veía distinto al de
 * Configuración). Tenerlo en un solo lugar evita que vuelva a pasar.
 */
@Component({
  selector: 'app-cabecera-interna',
  imports: [RouterLink],
  templateUrl: './cabecera-interna.html',
  styleUrl: './cabecera-interna.css',
})
export class CabeceraInterna {
  private sesion = inject(SesionService);
  private router = inject(Router);

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';
  /** Un solo enlace (atajo para el caso más común); null lo oculta. */
  @Input() enlace: EnlaceCabecera | null = null;
  /** Varios enlaces, cuando una pantalla lleva a más de una sección. */
  @Input() enlaces: EnlaceCabecera[] = [];

  /** Unifica ambas entradas para que la plantilla recorra una sola lista. */
  get enlacesVisibles(): EnlaceCabecera[] {
    return this.enlace ? [this.enlace, ...this.enlaces] : this.enlaces;
  }

  readonly operador = this.sesion.usuario;

  cerrarSesion(): void {
    this.sesion.cerrarSesion();
    this.router.navigateByUrl('/login');
  }
}
