import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideMenu, LucideX, LucideLogOut, LucideWifiOff, LucideCircleHelp } from '@lucide/angular';
import { SesionService } from '../../services/sesion.service';
import { ConectividadService } from '../../services/conectividad.service';
import { NotificacionService, ResumenNotificaciones, TipoNotificacion } from '../../services/notificacion.service';
import { Tour, TourStep } from '../tour/tour';

interface EnlaceCabecera {
  texto: string;
  ruta: string;
  soloAdmin?: boolean;
  /** Si este destino tiene un aviso asociado (ver resumenNotificaciones), qué tipo es. */
  tipoNotificacion?: TipoNotificacion;
}

/** Todos los destinos del módulo interno: cada uno se resalta como activo cuando corresponde
 * (ver esRutaActual), pero ninguno se saca de la lista por estar parado ahí — antes se sacaba,
 * y eso hacía que el menú cambiara de contenido según la pantalla, algo confuso. */
const TODOS_LOS_ENLACES: EnlaceCabecera[] = [
  { texto: 'Control de accesos', ruta: '/boleteria' },
  { texto: 'Vender entradas', ruta: '/pos' },
  { texto: 'Hoy', ruta: '/hoy', soloAdmin: true },
  { texto: 'Cajas', ruta: '/cajas', soloAdmin: true, tipoNotificacion: 'CAJA_ATRASADA' },
  { texto: 'Reportes', ruta: '/reportes', soloAdmin: true },
  { texto: 'Configuración', ruta: '/configuracion', soloAdmin: true },
  { texto: 'Acciones', ruta: '/acciones', soloAdmin: true, tipoNotificacion: 'RECHAZO_OPERACION' },
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
  private notificacionService = inject(NotificacionService);

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

  /** Qué tipos de aviso están prendidos ahora mismo (ver ngOnInit). Sólo se consulta para ADMIN
   * — BOLETERO no tiene acceso a ese endpoint. */
  private resumenNotificaciones = signal<ResumenNotificaciones>({});

  menuAbierto = signal(false);

  ngOnInit(): void {
    if (this.sesion.rol() === 'ADMIN') {
      this.notificacionService.obtenerResumen().subscribe({
        next: (r) => this.resumenNotificaciones.set(r),
        error: (err) => console.error('Error al consultar notificaciones pendientes:', err),
      });
    }
  }

  esRutaActual(ruta: string): boolean {
    return this.rutaActual.startsWith(ruta);
  }

  /** El aviso de este enlace está prendido y no es la pantalla en la que ya está parado el
   * usuario (si está ahí, ya la está viendo — no hace falta el puntito). */
  avisoSinVer(enlace: EnlaceCabecera): boolean {
    if (!enlace.tipoNotificacion) return false;
    return !!this.resumenNotificaciones()[enlace.tipoNotificacion] && !this.esRutaActual(enlace.ruta);
  }

  /** Para el puntito del botón hamburguesa: si cualquier enlace visible tiene un aviso sin ver. */
  readonly hayAlgunAvisoSinVer = computed(() => this.enlacesVisibles().some((e) => this.avisoSinVer(e)));

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
