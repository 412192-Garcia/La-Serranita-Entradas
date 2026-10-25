import { Component, DestroyRef, ElementRef, HostListener, computed, inject, input, signal } from '@angular/core';
import { LucideCheck, LucideCreditCard, LucidePrinter } from '@lucide/angular';
import { FacturaService } from '../../services/factura.service';
import { ConectividadService } from '../../services/conectividad.service';
import { ImpresorasService } from '../../services/impresoras.service';
import { ModulosService } from '../../services/modulos.service';
import { PointService, SIN_POINT } from '../../services/point.service';

type EstadoDispositivo = 'ok' | 'mal' | 'elegir' | 'manual' | 'cargando';

/** Del peor al mejor: el botón muestra el peor de los dos. */
const GRAVEDAD: EstadoDispositivo[] = ['mal', 'elegir', 'cargando', 'manual', 'ok'];

/**
 * Botón "Dispositivos" de la barra de la pantalla de venta: un solo botón con un solo punto de
 * estado para la ticketera donde imprime esta tablet y el Point de Mercado Pago con el que cobra.
 * Verde = todo listo; si algo falla, el botón dice qué. El menú tiene el detalle de cada uno, para
 * cambiarlos. El modo de cada Point (integrado o manual) no se toca acá: es de Configuración >
 * Cobros, porque confundía a los boleteros y el Point suele necesitar un reinicio para tomarlo.
 *
 * Está acá y no en Configuración porque lo usan los boleteros, que no ven esa pestaña; y no en el
 * carrito porque se elige una vez y queda guardado en la tablet, no venta por venta.
 */
@Component({
  selector: 'app-selector-dispositivos',
  imports: [LucidePrinter, LucideCreditCard, LucideCheck],
  templateUrl: './selector-dispositivos.html',
  styleUrl: './selector-dispositivos.css',
})
export class SelectorDispositivos {
  private facturaService = inject(FacturaService);
  private host = inject(ElementRef<HTMLElement>);
  readonly impresoras = inject(ImpresorasService);
  readonly point = inject(PointService);
  readonly SIN_POINT = SIN_POINT;

  /** Sin conexión no hay CAE ni se le puede mandar un cobro al Point: el botón se oculta en vez
   * de quedar en rojo (se cobra y se anota a mano, y la factura queda para el mail). */
  readonly enLinea = inject(ConectividadService).enLinea;
  /** En la facturación manual sólo importa la ticketera. */
  conPoint = input(true);
  abierto = signal(false);
  /** Hacia dónde se despliega la lista: en la barra del POS el botón está a la derecha (el menú
   * crece hacia la izquierda); en un formulario está a la izquierda y tiene que crecer al revés. */
  alinear = input<'derecha' | 'izquierda'>('derecha');

  mostrarTicketera = computed(() => this.facturaService.habilitada());
  mostrarPoint = computed(() => this.conPoint() && this.point.habilitado());

  /** ok = lista para imprimir; mal = PC apagada o ticketera apagada; elegir = hay varias y
   * ninguna elegida todavía. */
  estadoTicketera = computed<EstadoDispositivo>(() => {
    if (!this.impresoras.consultado()) return 'cargando';
    const lista = this.impresoras.impresoras();
    if (lista.length === 0) return 'mal';
    const efectiva = this.impresoras.efectiva();
    if (efectiva === null) return 'elegir';
    return efectiva.disponible ? 'ok' : 'mal';
  });

  /** ok = cobra con ese Point; manual = eligieron "Sin posnet"; elegir = hay varios y ninguno
   * elegido; mal = no hay ninguno integrado o no se pudo consultar. */
  estadoPoint = computed<EstadoDispositivo>(() => {
    if (this.point.elegida() === SIN_POINT) return 'manual';
    if (!this.point.consultado()) return 'cargando';
    if (this.point.efectiva()) return 'ok';
    if (this.point.faltaElegir()) return 'elegir';
    return 'mal';
  });

  /** El punto del botón: el peor de los dispositivos que se muestran. */
  estado = computed<EstadoDispositivo>(() => {
    const estados: EstadoDispositivo[] = [];
    if (this.mostrarTicketera()) estados.push(this.estadoTicketera());
    if (this.mostrarPoint()) estados.push(this.estadoPoint());
    return GRAVEDAD.find((g) => estados.includes(g)) ?? 'ok';
  });

  /** "Dispositivos" si está todo bien; si no, lo que hay que mirar primero. */
  etiqueta = computed(() => {
    const ticketera = this.mostrarTicketera() ? this.estadoTicketera() : 'ok';
    const point = this.mostrarPoint() ? this.estadoPoint() : 'ok';
    if (ticketera === 'mal') return this.impresoras.impresoras().length === 0 ? 'Ticketera: PC apagada' : 'Ticketera apagada';
    if (point === 'mal') return 'Point no disponible';
    if (ticketera === 'elegir') return 'Elegir ticketera';
    if (point === 'elegir') return 'Elegir Point';
    // Sólo la ticketera (facturación manual, o sin el módulo de Point): se nombra directo.
    if (!this.mostrarPoint()) return this.impresoras.efectiva() ? `Ticketera: ${this.impresoras.efectiva()!.nombre}` : 'Ticketera';
    return 'Dispositivos';
  });

  /** Lo mismo que el menú, en una línea, para el tooltip y los lectores de pantalla. */
  resumen = computed(() => {
    const partes: string[] = [];
    if (this.mostrarTicketera()) {
      partes.push(this.impresoras.motivoNoDisponible() ?? `Ticketera: ${this.impresoras.efectiva()?.nombre ?? 'sin elegir'}`);
    }
    if (this.mostrarPoint()) {
      const efectiva = this.point.efectiva();
      partes.push(
        this.estadoPoint() === 'manual'
          ? 'Sin posnet: la tarjeta y el QR se anotan a mano'
          : efectiva
            ? `Point: ${efectiva.nombre}`
            : (this.point.errorTerminales() ??
              (this.estadoPoint() === 'elegir'
                ? 'Elegí el Point de esta tablet'
                : 'Ningún Point integrado: la tarjeta y el QR se anotan a mano'))
      );
    }
    return partes.join(' · ');
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    inject(ModulosService).actualizar();
    this.impresoras.seguir(destroyRef);
    this.point.seguir(destroyRef);
  }

  elegirTicketera(nombre: string): void {
    this.impresoras.elegir(nombre);
    this.abierto.set(false);
  }

  elegirPoint(id: string): void {
    this.point.elegir(id);
    this.abierto.set(false);
  }

  /** Se cierra al tocar afuera, como cualquier menú desplegable. */
  @HostListener('document:click', ['$event'])
  alHacerClick(evento: MouseEvent): void {
    if (this.abierto() && !this.host.nativeElement.contains(evento.target as Node)) {
      this.abierto.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  alApretarEscape(): void {
    this.abierto.set(false);
  }
}
