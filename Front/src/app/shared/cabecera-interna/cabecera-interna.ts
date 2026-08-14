import { Component, Input, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideMenu, LucideX, LucideLogOut, LucideWifiOff } from '@lucide/angular';
import { SesionService } from '../../services/sesion.service';
import { ConectividadService } from '../../services/conectividad.service';

interface EnlaceCabecera {
  texto: string;
  ruta: string;
  soloAdmin?: boolean;
}

/** Todos los destinos del módulo interno: cada pantalla se saca a sí misma de su propio menú, el resto se filtra sólo por rol. */
const TODOS_LOS_ENLACES: EnlaceCabecera[] = [
  { texto: 'Control de accesos', ruta: '/boleteria' },
  { texto: 'Vender entradas', ruta: '/pos' },
  { texto: 'Hoy', ruta: '/hoy', soloAdmin: true },
  { texto: 'Generar reserva', ruta: '/crear-reserva', soloAdmin: true },
  { texto: 'Configuración', ruta: '/configuracion', soloAdmin: true },
  { texto: 'Reportes', ruta: '/reportes', soloAdmin: true },
  { texto: 'Cajas', ruta: '/cajas', soloAdmin: true },
  { texto: 'Mi cuenta', ruta: '/mi-cuenta' },
];

/**
 * Cabecera común de las pantallas del módulo interno (Boletería, POS, Generar
 * reserva y Configuración): título, bajada, menú deslizable con todos los
 * destinos disponibles, nombre del operador y cierre de sesión.
 *
 * El menú siempre muestra los mismos destinos en cualquier pantalla — sólo se
 * ocultan por rol (Generar reserva/Configuración son sólo ADMIN) y la pantalla
 * en la que ya estás parado, nunca por "no aplica acá".
 */
@Component({
  selector: 'app-cabecera-interna',
  imports: [RouterLink, LucideMenu, LucideX, LucideLogOut, LucideWifiOff],
  templateUrl: './cabecera-interna.html',
  styleUrl: './cabecera-interna.css',
})
export class CabeceraInterna {
  private sesion = inject(SesionService);
  private router = inject(Router);

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';

  readonly operador = this.sesion.usuario;

  /** Estado real de conexión al backend (no sólo navigator.onLine): lo mantiene ConectividadService. */
  readonly enLinea = inject(ConectividadService).enLinea;

  readonly enlacesVisibles = computed(() => {
    const esAdmin = this.sesion.rol() === 'ADMIN';
    const rutaActual = this.router.url;
    return TODOS_LOS_ENLACES.filter((e) => (esAdmin || !e.soloAdmin) && !rutaActual.startsWith(e.ruta));
  });

  menuAbierto = signal(false);

  toggleMenu(): void {
    this.menuAbierto.update((v) => !v);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  cerrarSesion(): void {
    this.sesion.cerrarSesion();
    this.router.navigateByUrl('/login');
  }
}
