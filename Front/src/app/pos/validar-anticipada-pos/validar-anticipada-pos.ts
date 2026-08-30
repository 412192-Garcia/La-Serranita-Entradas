import { Component, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BoleteriaService, Reserva } from '../../services/boleteria.service';
import { PesosPipe } from '../../shared/pesos.pipe';
import { Spinner } from '../../shared/spinner/spinner';
import { LucideX } from '@lucide/angular';

/**
 * Panel de validación de anticipadas dentro del POS: al escanear un DNI (ver Pos.detectorEscaneo)
 * se muestra en lugar del catálogo/carrito, sin destruirlos. Lista sólo las anticipadas
 * PENDIENTES de ese DNI:
 *
 *  - APROBADO (ya pagada online) → botón "Validar ingreso": valida y deja el POS limpio.
 *  - RESERVADO_EFECTIVO (a cobrar en caja) → botón "Cobrar en el POS": carga la reserva en el
 *    carrito (entradas editables, extras de la reserva fijos) y se cobra como una venta normal;
 *    el backend cierra la reserva existente en vez de crear otra compra.
 *
 * Todo lo demás (ventas de puerta, ya usadas, errores de tipeo) cae al link a Control de Accesos.
 */
@Component({
  selector: 'app-validar-anticipada-pos',
  imports: [DatePipe, RouterLink, PesosPipe, Spinner, LucideX],
  templateUrl: './validar-anticipada-pos.html',
  styleUrl: './validar-anticipada-pos.css',
})
export class ValidarAnticipadaPos {
  private boleteriaService = inject(BoleteriaService);

  dni = input.required<string>();
  cerrar = output<void>();
  /** Una APROBADO se validó: el POS cierra el panel y queda limpio para el próximo cliente. */
  validada = output<void>();
  /** Una RESERVADO_EFECTIVO: el POS la carga en el carrito para cobrarla como venta normal. */
  cobrarEnPos = output<Reserva>();

  cargando = signal(false);
  error = signal(false);
  reservas = signal<Reserva[]>([]);
  procesandoId = signal<number | null>(null);
  errorAccion = signal<string | null>(null);

  constructor() {
    // El DNI puede cambiar sin destruir el panel: si el boletero escanea otro documento
    // mientras esto está abierto, se rehace la búsqueda con el nuevo.
    effect(() => this.buscar(this.dni()));
  }

  buscar(dni = this.dni()): void {
    this.cargando.set(true);
    this.error.set(false);
    this.errorAccion.set(null);
    this.boleteriaService
      .buscar({ texto: dni, tipo: 'ANTICIPADA', estados: ['APROBADO', 'RESERVADO_EFECTIVO'], size: 50 })
      .subscribe({
        next: (res) => {
          this.reservas.set(res.content);
          this.cargando.set(false);
        },
        error: (err) => {
          console.error('No se pudieron buscar las anticipadas:', err);
          this.error.set(true);
          this.cargando.set(false);
        },
      });
  }

  /** Unidades de entrada (no extras) de la reserva. */
  pases(r: Reserva): number {
    return (r.detalles ?? [])
      .filter((d) => d.tipoEntrada?.tipo === 'ENTRADA')
      .reduce((acc, d) => acc + d.cantidad, 0);
  }

  /** Compra pagada online (APROBADO) → habilita el ingreso y deja el POS limpio. */
  validar(r: Reserva): void {
    this.procesandoId.set(r.id);
    this.errorAccion.set(null);
    this.boleteriaService.validarIngreso(r.id).subscribe({
      next: () => {
        this.procesandoId.set(null);
        this.validada.emit();
      },
      error: (err) => {
        console.error('No se pudo validar el ingreso:', err);
        this.errorAccion.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo completar la validación. Reintentá.'
        );
        this.procesandoId.set(null);
      },
    });
  }

  procesando(r: Reserva): boolean {
    return this.procesandoId() === r.id;
  }
}
