import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  LucideMenu, LucideX, LucideLogOut, LucideWifiOff, LucideCircleHelp, LucideScanLine, LucideTicket, LucideSun,
  LucideWallet, LucideChartColumn, LucideSettings, LucideWrench, LucideActivity, LucideCircleUser,
} from '@lucide/angular';
import { SesionService } from '../../services/sesion.service';
import { ConectividadService } from '../../services/conectividad.service';
import { NotificacionService, ResumenNotificaciones, TipoNotificacion } from '../../services/notificacion.service';
import { Tour, TourStep } from '../tour/tour';
import { AtrasService, EntradaAtras } from '../modal/atras.service';

/** Destinos de la barra inferior del celular. El resto queda en "Más". */
const RUTAS_BARRA_INFERIOR = ['/hoy', '/reportes', '/cajas'];

interface EnlaceCabecera {
  texto: string;
  ruta: string;
  soloAdmin?: boolean;
  soloSuperadmin?: boolean;
  /** Si este destino tiene avisos asociados (ver resumenNotificaciones), de qué tipos: con que uno
   * esté prendido, se muestra el puntito. */
  tiposNotificacion?: TipoNotificacion[];
}

/** Todos los destinos del módulo interno: cada uno se resalta como activo cuando corresponde
 * (ver esRutaActual), pero ninguno se saca de la lista por estar parado ahí — antes se sacaba,
 * y eso hacía que el menú cambiara de contenido según la pantalla, algo confuso. */
const TODOS_LOS_ENLACES: EnlaceCabecera[] = [
  { texto: 'Control de accesos', ruta: '/boleteria', soloAdmin: true },
  { texto: 'Vender entradas', ruta: '/pos' },
  { texto: 'Hoy', ruta: '/hoy', soloAdmin: true },
  { texto: 'Cajas', ruta: '/cajas', soloAdmin: true, tiposNotificacion: ['CAJA_ATRASADA'] },
  { texto: 'Reportes', ruta: '/reportes', soloAdmin: true },
  { texto: 'Configuración', ruta: '/configuracion', soloAdmin: true },
  { texto: 'Acciones', ruta: '/acciones', soloAdmin: true, tiposNotificacion: ['FACTURACION'] },
  { texto: 'Sistema', ruta: '/sistema', soloSuperadmin: true, tiposNotificacion: ['SISTEMA', 'RECHAZO_OPERACION'] },
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
  imports: [
    RouterLink, Tour, LucideMenu, LucideX, LucideLogOut, LucideWifiOff, LucideCircleHelp,
    LucideScanLine, LucideTicket, LucideSun, LucideWallet, LucideChartColumn, LucideSettings, LucideWrench,
    LucideActivity, LucideCircleUser,
  ],
  templateUrl: './cabecera-interna.html',
  styleUrl: './cabecera-interna.css',
})
export class CabeceraInterna implements OnInit, OnDestroy {
  private sesion = inject(SesionService);
  private router = inject(Router);
  private notificacionService = inject(NotificacionService);
  private atras = inject(AtrasService);

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';
  /** Cuando la pantalla define pasos, se muestra el botón "Tutorial" acá — centralizado en
   * la cabecera común para no repetir el botón + <app-tour> en cada pantalla. Vacío = sin tutorial. */
  @Input() pasosTutorial: TourStep[] = [];
  /** Botón "Tutorial" sólo con el ícono también en pantallas grandes, para pantallas con muchas
   * acciones propias en la barra (ej. el POS). En mobile ya es así siempre. */
  @Input() tutorialCompacto = false;
  /** Sin barra inferior en celular, para pantallas que necesitan el alto (ej. el POS). */
  @Input() sinBarraInferior = false;

  /** Avisan cuándo empieza y termina el tutorial: una pantalla cuyos pasos cambian de modo (ver
   * `TourStep.antes`, ej. el POS pasando a Anticipadas) guarda el modo al empezar y lo repone al terminar. */
  @Output() tutorialIniciado = new EventEmitter<void>();
  @Output() tutorialCerrado = new EventEmitter<void>();

  tourActivo = signal(false);

  iniciarTutorial(): void {
    this.tutorialIniciado.emit();
    this.tourActivo.set(true);
  }

  readonly operador = this.sesion.usuario;

  /** Estado real de conexión al backend (no sólo navigator.onLine): lo mantiene ConectividadService. */
  readonly enLinea = inject(ConectividadService).enLinea;

  /** Cada pantalla instancia su propia cabecera (no hay un shell persistente entre navegaciones),
   * así que leerla una sola vez acá alcanza: se recalcula sola al entrar a la próxima pantalla. */
  private readonly rutaActual = this.router.url;

  readonly enlacesVisibles = computed(() => {
    const esAdmin = this.sesion.esAdmin();
    const esSuperadmin = this.sesion.esSuperadmin();
    return TODOS_LOS_ENLACES.filter((e) => (esAdmin || !e.soloAdmin) && (esSuperadmin || !e.soloSuperadmin));
  });

  /** Qué tipos de aviso están prendidos ahora mismo (ver ngOnInit). Sólo se consulta para ADMIN
   * — BOLETERO no tiene acceso a ese endpoint. */
  private resumenNotificaciones = signal<ResumenNotificaciones>({});

  menuAbierto = signal(false);
  private entradaMenu: EntradaAtras = { cerrar: () => this.menuAbierto.set(false), estaAbierta: () => this.menuAbierto() };

  /** El boletero tiene dos destinos: le alcanza con el menú. */
  get conBarraInferior(): boolean {
    return this.sesion.esAdmin() && !this.sinBarraInferior;
  }

  /** Ya está a la vista en la barra inferior: en celular no se repite en el menú. */
  estaEnBarra(ruta: string): boolean {
    return this.conBarraInferior && RUTAS_BARRA_INFERIOR.includes(ruta);
  }

  get masActivo(): boolean {
    return this.menuAbierto() || !RUTAS_BARRA_INFERIOR.some((r) => this.esRutaActual(r));
  }

  /** Para el puntito de "Más": avisos de los destinos que no están en la barra. */
  get hayAvisoEnMas(): boolean {
    return this.enlacesVisibles().some((e) => !this.estaEnBarra(e.ruta) && this.avisoSinVer(e));
  }

  avisoDeRuta(ruta: string): boolean {
    return this.enlacesVisibles().some((e) => e.ruta === ruta && this.avisoSinVer(e));
  }

  ngOnDestroy(): void {
    if (this.menuAbierto()) this.atras.soltar(this.entradaMenu);
  }

  ngOnInit(): void {
    if (this.sesion.esAdmin()) {
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
    if (!enlace.tiposNotificacion?.length) return false;
    const resumen = this.resumenNotificaciones();
    return enlace.tiposNotificacion.some((t) => !!resumen[t]) && !this.esRutaActual(enlace.ruta);
  }

  /** Para el puntito del botón hamburguesa: si cualquier enlace visible tiene un aviso sin ver. */
  readonly hayAlgunAvisoSinVer = computed(() => this.enlacesVisibles().some((e) => this.avisoSinVer(e)));

  toggleMenu(): void {
    if (this.menuAbierto()) {
      this.cerrarMenu();
    } else {
      this.menuAbierto.set(true);
      this.atras.abrir(this.entradaMenu);
    }
  }

  cerrarMenu(): void {
    if (!this.menuAbierto()) return;
    this.menuAbierto.set(false);
    this.atras.cerrado(this.entradaMenu);
  }

  /** Tocó un destino del menú: si cambia de pantalla, el router reemplaza la entrada del menú. */
  alElegirDestino(ruta: string): void {
    if (this.esRutaActual(ruta)) {
      this.cerrarMenu();
    } else {
      this.menuAbierto.set(false);
      this.atras.soltar(this.entradaMenu);
    }
  }

  cerrarSesion(): void {
    this.menuAbierto.set(false);
    this.atras.soltar(this.entradaMenu);
    this.sesion.cerrarSesion();
    this.router.navigateByUrl('/login', { replaceUrl: true });
  }
}
