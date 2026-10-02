import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { ImpresoraConectada } from '../models/factura';
import { ConectividadService } from './conectividad.service';
import { FacturaService } from './factura.service';

/** Ticketera elegida en ESTA tablet: cada dispositivo imprime en la que tiene al lado. */
const CLAVE_IMPRESORA = 'pos-impresora';
/** Cada cuánto se revisa qué ticketeras están conectadas y si responden. */
const INTERVALO_MS = 10_000;

/**
 * Estado de las ticketeras para la pantalla de venta, compartido entre el selector de la barra
 * (donde se elige y se ve si está lista) y el carrito (que bloquea "Imprimir factura" si no).
 *
 * La elección se guarda en la tablet y no se pide en cada venta: se elige una vez y sólo se cambia
 * en casos puntuales (otra ticketera, la de siempre rota...).
 */
@Injectable({ providedIn: 'root' })
export class ImpresorasService {
  private facturaService = inject(FacturaService);
  private conectividad = inject(ConectividadService);

  /** Ticketeras con su agente conectado. Vacío = la PC de la entrada está apagada o sin internet. */
  readonly impresoras = signal<ImpresoraConectada[]>([]);
  /** Ya se consultó al menos una vez (para no mostrar "PC apagada" mientras carga). */
  readonly consultado = signal(false);
  readonly elegida = signal<string | null>(this.leerElegida());

  /** La que se usa: la elegida si está conectada; si no, la única conectada; si hay varias y
   * ninguna elegida, null (el boletero elige en la barra). */
  readonly efectiva = computed<ImpresoraConectada | null>(() => {
    const lista = this.impresoras();
    const elegida = lista.find((i) => i.nombre === this.elegida());
    if (elegida) return elegida;
    return lista.length === 1 ? lista[0] : null;
  });

  readonly lista = computed(() => this.conectividad.enLinea() && this.efectiva()?.disponible === true);

  /**
   * Por qué no se puede imprimir, distinguiendo lo que va a pasar seguido: la PC de la entrada
   * apagada (no hay agente conectado) o la ticketera apagada / sin papel (el agente está, pero la
   * impresora no responde). Cada caso se arregla distinto, así que el cajero tiene que saber cuál es.
   */
  readonly motivoNoDisponible = computed<string | null>(() => {
    if (!this.conectividad.enLinea()) return 'Sin conexión: la factura sólo se puede mandar por mail.';
    if (this.impresoras().length === 0) {
      return 'La PC de la entrada está apagada o sin conexión: la factura sólo se puede mandar por mail.';
    }
    const impresora = this.efectiva();
    if (impresora === null) return 'Elegí la ticketera arriba, en el botón "Ticketera".';
    if (!impresora.disponible) {
      return `La ticketera "${impresora.nombre}" está ${impresora.detalle ?? 'apagada o desconectada'}: la factura sólo se puede mandar por mail.`;
    }
    return null;
  });

  constructor() {
    // En una tablet que todavía no sabía que la facturación está activa (primer uso, o se borró
    // el almacenamiento), la primera consulta se salteaba y el botón quedaba en gris hasta el
    // siguiente ciclo. Apenas se confirma que está activa, se consulta.
    effect(() => {
      if (this.facturaService.habilitada()) untracked(() => this.actualizar());
    });
  }

  /** La pantalla de venta lo llama al abrirse: consulta ya y después cada 10 s, mientras viva. */
  seguir(destroyRef: DestroyRef): void {
    this.actualizar();
    interval(INTERVALO_MS)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.actualizar());
  }

  actualizar(): void {
    if (!this.facturaService.habilitada() || !this.conectividad.enLinea()) return;
    this.facturaService.impresoras().subscribe({
      next: (lista) => {
        this.impresoras.set(lista);
        this.consultado.set(true);
      },
      // Si falla la consulta se deja la última lista conocida: un pedido perdido no tiene por qué
      // bloquear Imprimir; si la ticketera de verdad no está, el ticket queda en espera.
      error: () => {},
    });
  }

  elegir(nombre: string): void {
    this.elegida.set(nombre);
    try {
      localStorage.setItem(CLAVE_IMPRESORA, nombre);
    } catch {
      // Sin storage: la elección vale sólo hasta recargar.
    }
  }

  private leerElegida(): string | null {
    try {
      return localStorage.getItem(CLAVE_IMPRESORA);
    } catch {
      return null;
    }
  }
}
