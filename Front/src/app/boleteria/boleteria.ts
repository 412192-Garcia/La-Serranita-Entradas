import { Component, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BoleteriaService, Reserva } from '../services/boleteria.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { CajaService, Caja } from '../services/caja.service';
import { AperturaCaja } from '../pos/apertura-caja/apertura-caja';
import { LucideEllipsisVertical, LucideChevronDown } from '@lucide/angular';
import { Modal } from '../shared/modal/modal';
import { TourStep } from '../shared/tour/tour';
import { DetectorEscaneoDni } from '../shared/escaner-dni.util';
import {
  ESTADOS_ANTICIPADA_PRINCIPALES,
  ESTADOS_ANTICIPADA_SECUNDARIOS,
  FORMAS_PAGO_FILTRABLES,
  ReservasBusqueda,
} from '../reservas/busqueda-reservas';
import { BuscadorReservas } from '../reservas/buscador-reservas/buscador-reservas';
import { PanelRegalos } from '../reservas/panel-regalos/panel-regalos';
import { ListadoReservas } from '../reservas/listado-reservas/listado-reservas';

/** ~7 pasos, todos apuntando a elementos siempre presentes en el DOM (nada detrás de "Más
 * filtros" ni de una fila de resultado puntual, que dependen de los datos del momento). La única
 * excepción es el botón de regalos, que sólo existe si hay: ese paso trae un `alternativo`. */
const PASOS_TUTORIAL: TourStep[] = [
  {
    selector: '[data-tour="caja"]',
    titulo: 'Tu caja',
    texto: 'Acá ves si tu caja está abierta. Si está cerrada, tocá para abrirla antes de cobrar cualquier cosa.',
  },
  {
    selector: '[data-tour="buscador"]',
    titulo: 'Buscar una reserva',
    texto: 'Buscá por DNI, nombre, email o código de reserva. También podés escanear el DNI: el lector actúa como si tipearas y apretaras Enter.',
  },
  {
    selector: '[data-tour="fecha"]',
    titulo: 'Día a consultar',
    texto: 'Elegí un día puntual, o tocá "Ver todas las fechas" para buscar sin importar cuándo es la visita.',
  },
  {
    selector: '[data-tour="regalos"]',
    // El botón sólo existe si hay regalos: sin ellos se resalta la barra de fecha, que siempre está.
    alternativo: '[data-tour="fecha"]',
    titulo: 'Regalos',
    texto: 'Un regalo no tiene fecha de visita (quien lo recibe elige cuándo venir), así que no aparece en la lista del día: va en este botón, que sólo se ve cuando hay regalos. Tocalo para desplegarlos. En cada uno, "Para <nombre> · DNI" es la persona que se presenta en la puerta, no quien compró.',
  },
  {
    selector: '[data-tour="estado"]',
    titulo: 'Pagadas o a cobrar',
    texto: 'Filtrá entre lo ya pagado online y lo que falta cobrar en caja.',
  },
  {
    selector: '[data-tour="mas-filtros"]',
    titulo: 'Más filtros',
    texto: 'Acá encontrás el resto de los estados (ya utilizada, pago pendiente, cancelada, reembolsada), el tipo de listado (Anticipada o venta de Boletería) y la forma de pago, por si necesitás afinar más la búsqueda.',
  },
  {
    selector: '[data-tour="resultados"]',
    titulo: 'Validar o cobrar',
    texto: 'Cada resultado tiene un botón para validar el ingreso o cobrar y validar (si te equivocás, tenés unos segundos para "Cancelar validación"), y un menú ⋮ para editar el contacto, reenviar el mail o reembolsar. Ese menú es sólo de esta pantalla: los boleteros validan y cobran desde el POS.',
  },
];

/**
 * Control de Accesos: buscar una reserva y validar el ingreso o cobrarla. Es la pantalla completa
 * (cabecera, caja, filtros y el menú ⋮ con editar / reenviar mail / reembolsar); el buscador, el
 * listado y la fila los comparte con el panel de anticipadas del POS (ver `reservas/`).
 */
@Component({
  selector: 'app-boleteria',
  imports: [
    FormsModule,
    CabeceraInterna,
    AperturaCaja,
    Modal,
    BuscadorReservas,
    PanelRegalos,
    ListadoReservas,
    LucideEllipsisVertical,
    LucideChevronDown,
  ],
  providers: [ReservasBusqueda],
  templateUrl: './boleteria.html',
  styleUrl: './boleteria.css',
})
export class Boleteria implements OnInit, OnDestroy {
  private boleteriaService = inject(BoleteriaService);
  private cajaService = inject(CajaService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  protected busqueda = inject(ReservasBusqueda);

  readonly pasosTutorial = PASOS_TUTORIAL;

  readonly estadosPrincipales = ESTADOS_ANTICIPADA_PRINCIPALES;
  readonly estadosSecundarios = ESTADOS_ANTICIPADA_SECUNDARIOS;
  readonly formasPagoFiltrables = FORMAS_PAGO_FILTRABLES;

  /** undefined = todavía no llegó la respuesta; null = no tiene ninguna caja abierta. */
  cajaActual = signal<Caja | null | undefined>(undefined);
  mostrarAperturaCaja = signal(false);

  /** El propio indicador de "caja cerrada" duplica como botón para abrirla, sin tener que ir a Vender entradas. */
  onCajaAbierta(caja: Caja): void {
    this.cajaActual.set(caja);
    this.mostrarAperturaCaja.set(false);
  }

  /** Estado/Forma de pago quedan colapsados por defecto: son los filtros que casi no se tocan en el uso diario. */
  mostrarMasFiltros = signal(false);

  /** Detecta un escaneo de DNI cuando el foco no está en un campo de texto (ver DetectorEscaneoDni). */
  private detectorEscaneo = new DetectorEscaneoDni((escaneo) => {
    if (this.busqueda.buscando()) return;
    this.busqueda.texto.set(escaneo);
    this.busqueda.buscar();
  });

  ngOnInit(): void {
    // Si se llega acá desde el escaneo de fondo del POS, ya viene con el DNI: precargamos el
    // buscador y disparamos la búsqueda en vez de la de "hoy" por defecto. Se limpia el query
    // param enseguida para que un refresh no repita la búsqueda ni el botón "atrás" quede
    // pegado en esta URL.
    const dniDesdeEscaneo = this.route.snapshot.queryParamMap.get('dni');
    if (dniDesdeEscaneo) {
      this.busqueda.texto.set(dniDesdeEscaneo);
      this.busqueda.buscar();
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    } else {
      // Al entrar, mostramos directamente lo accionable de hoy: es lo que un boletero
      // necesita ver primero, sin tener que buscar nada.
      this.busqueda.ejecutarBusqueda();
    }

    // Acá no se abre/cierra caja (eso es en Vender entradas), pero conviene saber de
    // un vistazo si ya la abriste antes de mandarte a buscar una reserva para cobrar.
    this.cajaService.getActual().subscribe({
      next: (caja) => this.cajaActual.set(caja),
      error: (err) => {
        console.error('Error al consultar la caja actual:', err);
        this.cajaActual.set(null);
      },
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKeydownGlobal(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.menuAbiertoId() !== null) {
      this.menuAbiertoId.set(null);
      return;
    }
    this.detectorEscaneo.procesarTecla(event);
  }

  ngOnDestroy(): void {
    this.detectorEscaneo.destruir();
  }

  // ---------- Menú de acciones (deshacer / editar contacto / reenviar mail / reembolsar) ----------

  /** Igual al límite que ya valida el backend (`CompraServiceImpl.deshacerValidacion`): un
   * "Deshacer validación" en el menú "⋮", disponible más allá de los 8s de la fila roja —para
   * cuando el error se nota minutos después, no al toque— pero no ilimitado, porque más allá
   * de esta ventana el backend lo rechaza igual (y correr más ese límite ahí arriescaría
   * descuadrar una caja que ya se cerró con esa venta adentro). */
  private static readonly VENTANA_DESHACER_MENU_MS = 120_000;

  menuAbiertoId = signal<number | null>(null);

  toggleMenu(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.update((actual) => (actual === reserva.id ? null : reserva.id));
  }

  @HostListener('document:click')
  cerrarMenu(): void {
    this.menuAbiertoId.set(null);
  }

  editando = signal<Reserva | null>(null);
  /** dni/receptorDni: cuál de los dos aplica depende de esRegaloDe(reserva) — un titular normal
   * usa dni, un regalo usa receptorDni (es el que se presenta en la puerta para ingresar). */
  formEdit = { nombre: '', apellido: '', dni: '', email: '', telefono: '', receptorDni: '' };
  guardandoEdit = signal(false);
  errorEdit = signal<string | null>(null);

  /** Sin fecha de visita = regalo: el DNI que vale para entrar es el de quien lo recibe, no el
   * del comprador (ver receptorDni en Compra). */
  esRegaloDe(reserva: Reserva): boolean {
    return reserva.fechaVisita == null;
  }

  abrirEdicion(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    this.errorEdit.set(null);
    this.formEdit = {
      nombre: reserva.cliente?.nombre ?? '',
      apellido: reserva.cliente?.apellido ?? '',
      dni: reserva.cliente?.dni ?? '',
      email: reserva.contactEmail ?? '',
      telefono: reserva.contactPhone ?? '',
      receptorDni: reserva.receptorDni ?? '',
    };
    this.editando.set(reserva);
  }

  cancelarEdicion(): void {
    this.editando.set(null);
    this.errorEdit.set(null);
  }

  guardarEdicion(): void {
    const reserva = this.editando();
    if (!reserva || this.guardandoEdit()) return;

    // El DNI es lo que se compara contra el documento real en la puerta: si se está por
    // cambiar, mejor confirmar — no es un dato de contacto cualquiera como el teléfono.
    const dniTitularCambio = !this.esRegaloDe(reserva) && this.formEdit.dni.trim() !== (reserva.cliente?.dni ?? '');
    const dniReceptorCambio = this.esRegaloDe(reserva) && this.formEdit.receptorDni.trim() !== (reserva.receptorDni ?? '');
    if (dniTitularCambio || dniReceptorCambio) {
      const confirmado = window.confirm(
        'Estás por cambiar el DNI de esta reserva: es lo que se compara contra el documento real al validar el ingreso. ¿Confirmás?'
      );
      if (!confirmado) return;
    }

    this.guardandoEdit.set(true);
    this.errorEdit.set(null);

    this.boleteriaService.editarContacto(reserva.id, { ...this.formEdit }).subscribe({
      next: (actualizada) => {
        this.busqueda.reemplazarEnResultado(actualizada);
        this.guardandoEdit.set(false);
        this.editando.set(null);
      },
      error: (err) => {
        console.error('Error al editar el contacto:', err);
        this.errorEdit.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar los cambios.');
        this.guardandoEdit.set(false);
      },
    });
  }

  reenviandoId = signal<number | null>(null);
  avisoReenvio = signal<string | null>(null);

  reenviarMail(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    this.reenviandoId.set(reserva.id);
    this.avisoReenvio.set(null);

    this.boleteriaService.reenviarMail(reserva.id).subscribe({
      next: () => {
        this.reenviandoId.set(null);
        this.avisoReenvio.set(`Mail reenviado (compra #${reserva.codigoReserva}).`);
      },
      error: (err) => {
        console.error('Error al reenviar el mail:', err);
        this.reenviandoId.set(null);
        this.avisoReenvio.set(typeof err?.error === 'string' ? err.error : 'No se pudo reenviar el mail.');
      },
    });
  }

  /** Sólo tiene sentido para lo pagado online que todavía no ingresó: es lo único que se reembolsa. */
  puedeReembolsar(reserva: Reserva): boolean {
    return reserva.estado === 'APROBADO';
  }

  /** Habilita "Deshacer validación" en el menú "⋮" para cualquier USADO reciente, más allá de
   * los 8s de la fila roja — el backend vuelve a chequear la ventana igual, así que esto es
   * sólo para no mostrar un botón que de entrada ya sabemos que va a fallar. */
  puedeDeshacerDesdeMenu(reserva: Reserva): boolean {
    if (reserva.estado !== 'USADO' || !reserva.fechaValidacion) return false;
    const validado = new Date(reserva.fechaValidacion).getTime();
    return Date.now() - validado < Boleteria.VENTANA_DESHACER_MENU_MS;
  }

  reembolsandoId = signal<number | null>(null);

  reembolsar(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    const confirmado = window.confirm(
      `¿Reembolsar la compra #${reserva.codigoReserva} por ${reserva.montoTotal}? Esto devuelve el dinero real a través de Mercado Pago y no se puede deshacer.`
    );
    if (!confirmado) return;

    this.reembolsandoId.set(reserva.id);
    this.busqueda.errorAccion.set(null);

    this.boleteriaService.reembolsar(reserva.id).subscribe({
      next: (actualizada) => {
        this.busqueda.reemplazarEnResultado(actualizada);
        this.reembolsandoId.set(null);
      },
      error: (err) => {
        console.error('Error al reembolsar:', err);
        this.busqueda.errorAccion.set(typeof err?.error === 'string' ? err.error : 'No se pudo procesar el reembolso.');
        this.reembolsandoId.set(null);
      },
    });
  }
}
