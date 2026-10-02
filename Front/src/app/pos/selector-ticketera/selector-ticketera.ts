import { Component, DestroyRef, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { LucideCheck, LucidePrinter } from '@lucide/angular';
import { FacturaService } from '../../services/factura.service';
import { ConectividadService } from '../../services/conectividad.service';
import { ImpresorasService } from '../../services/impresoras.service';

/**
 * Botón "Ticketera" de la barra de la pantalla de venta: muestra en qué ticketera imprime esta
 * tablet y si está lista (punto verde / rojo), y despliega la lista para cambiarla. Está acá y no
 * en Configuración porque lo usan los boleteros, que no ven esa pestaña; y no en el carrito porque
 * se elige una vez y queda guardado en la tablet, no se elige venta por venta.
 */
@Component({
  selector: 'app-selector-ticketera',
  imports: [LucidePrinter, LucideCheck],
  templateUrl: './selector-ticketera.html',
  styleUrl: './selector-ticketera.css',
})
export class SelectorTicketera {
  private facturaService = inject(FacturaService);
  private host = inject(ElementRef<HTMLElement>);
  readonly servicio = inject(ImpresorasService);

  readonly habilitada = this.facturaService.habilitada;
  /** Sin conexión no hay CAE, así que no se imprime nada: el botón se oculta en vez de quedar en rojo. */
  readonly enLinea = inject(ConectividadService).enLinea;
  abierto = signal(false);

  /** ok = lista para imprimir; mal = PC apagada o ticketera apagada; elegir = hay varias y
   * ninguna elegida todavía. */
  estado = computed<'ok' | 'mal' | 'elegir' | 'cargando'>(() => {
    if (!this.servicio.consultado()) return 'cargando';
    const lista = this.servicio.impresoras();
    if (lista.length === 0) return 'mal';
    const efectiva = this.servicio.efectiva();
    if (efectiva === null) return 'elegir';
    return efectiva.disponible ? 'ok' : 'mal';
  });

  etiqueta = computed(() => {
    if (!this.servicio.consultado()) return 'Ticketera';
    if (this.servicio.impresoras().length === 0) return 'Ticketera: PC apagada';
    const efectiva = this.servicio.efectiva();
    return efectiva ? `Ticketera: ${efectiva.nombre}` : 'Elegir ticketera';
  });

  constructor() {
    this.servicio.seguir(inject(DestroyRef));
  }

  elegir(nombre: string): void {
    this.servicio.elegir(nombre);
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
