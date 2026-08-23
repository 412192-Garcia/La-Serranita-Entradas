import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideMenu, LucideX, LucideLogOut, LucideWifiOff, LucideCircleHelp } from '@lucide/angular';
import { SesionService } from '../../services/sesion.service';
import { ConectividadService } from '../../services/conectividad.service';
import { RechazoService } from '../../services/rechazo.service';
import { Tour, TourStep } from '../tour/tour';

interface EnlaceCabecera {
  texto: string;
  ruta: string;
  soloAdmin?: boolean;
}

/** Todos los destinos del módulo interno: cada uno se resalta como activo cuando corresponde
 * (ver esRutaActual), pero ninguno se saca de la lista por estar parado ahí — antes se sacaba,
 * y eso hacía que el menú cambiara de contenido según la pantalla, algo confuso. */
const TODOS_LOS_ENLACES: EnlaceCabecera[] = [
  { texto: 'Control de accesos', ruta: '/boleteria' },
  { texto: 'Vender entradas', ruta: '/pos' },
  { texto: 'Hoy', ruta: '/hoy', soloAdmin: true },
  { texto: 'Acciones', ruta: '/acciones', soloAdmin: true },
  { texto: 'Configuración', ruta: '/configuracion', soloAdmin: true },
  { texto: 'Reportes', ruta: '/reportes', soloAdmin: true },
  { texto: 'Cajas', ruta: '/cajas', soloAdmin: true },
  { texto: 'Mi cuenta', ruta: '/mi-cuenta' },
];

/**
 * Cabecera común de las pantallas del módulo interno (Boletería, POS, Acciones
 * y Configuración): título, bajada, menú deslizable con todos los destinos
 * disponibles, nombre del operador y cierre de sesión.
 *
 * El menú siempre muestra los mismos destinos en cualquier pantalla — sólo se
 * ocultan por rol (Acciones/Configuración son sólo ADMIN), nunca por "no
 * aplica acá"; la pantalla en la que ya estás parada se marca como activa
 * (ver esRutaActual), no se saca de la lista.
 */
@Component({
  selector: 'app-cabecera-interna',
  imports: [RouterLink, Tour, LucideMenu, LucideX, LucideLogOut, LucideWifiOff, LucideCircleHelp],
  templateUrl: './cabecera-interna.html',
  styleUrl: './cabecera-interna.css',
})
export class CabeceraInterna implements OnInit {
  private sesion = inject(SesionService);
  private router = inject(Router);
  private rechazoService = inject(RechazoService);

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';
  /** Cuando la pantalla define pasos, se muestra el botón "Tutorial" acá — centralizado en
   * la cabecera común para no repetir el botón + <app-tour> en cada pantalla. Vacío = sin tutorial. */
  @Input() pasosTutorial: TourStep[] = [];

  tourActivo = signal(false);

  readonly operador = this.sesion.usuario;

  /** Estado real de conexión al backend (no sólo navigator.onLine): lo mantiene ConectividadService. */
  readonly enLinea = inject(ConectividadService).enLinea;

  /** Cada pantalla instancia su propia cabecera (no hay un shell persistente entre navegaciones),
   * así que leerla una sola vez acá alcanza: se recalcula sola al entrar a la próxima pantalla. */
  private readonly rutaActual = this.router.url;

  readonly enlacesVisibles = computed(() => {
    const esAdmin = this.sesion.rol() === 'ADMIN';
    return TODOS_LOS_ENLACES.filter((e) => esAdmin || !e.soloAdmin);
  });

  /** Punto rojo de aviso: hay operaciones rechazadas pendientes de revisión (ver Acciones ›
   * Operaciones rechazadas). Sólo se consulta para ADMIN — BOLETERO no tiene acceso a ese endpoint. */
  hayRechazosPendientes = signal(false);

  menuAbierto = signal(false);

  ngOnInit(): void {
    if (this.sesion.rol() === 'ADMIN') {
      this.rechazoService.listar(false).subscribe({
        next: (rs) => this.hayRechazosPendientes.set(rs.length > 0),
        error: (err) => console.error('Error al consultar operaciones rechazadas pendientes:', err),
      });
    }
  }

  esRutaActual(ruta: string): boolean {
    return this.rutaActual.startsWith(ruta);
  }

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
