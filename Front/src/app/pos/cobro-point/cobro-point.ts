import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { interval } from 'rxjs';
import { LucideCreditCard, LucideQrCode } from '@lucide/angular';
import { Modal } from '../../shared/modal/modal';
import { PesosPipe } from '../../shared/pesos.pipe';
import { ConectividadService } from '../../services/conectividad.service';
import { CobroPoint, FormaPagoPoint, PointService, TerminalPoint, TipoTarjetaPoint } from '../../services/point.service';

/** Cada cuánto se pregunta por el cobro mientras se espera la tarjeta (el backend le pregunta a
 * Mercado Pago como mucho cada 3 s; si llega el webhook, antes). */
const INTERVALO_MS = 2_000;

/**
 * Ventana "pasá la tarjeta (o escaneá el QR) en el Point": manda el cobro, espera el resultado y
 * avisa. No registra la venta: eso lo hace el carrito con el cobro aprobado, por la cola de siempre.
 *
 * El cobro queda recordado en la tablet (PointService.cobrosPendientes) desde que se crea hasta que
 * la venta se registra: si se recarga la página en el medio, se retoma desde acá.
 */
@Component({
  selector: 'app-cobro-point',
  imports: [Modal, PesosPipe, LucideCreditCard, LucideQrCode],
  templateUrl: './cobro-point.html',
  styleUrl: './cobro-point.css',
})
export class CobroPointModal implements OnInit {
  private point = inject(PointService);
  readonly enLinea = inject(ConectividadService).enLinea;

  monto = input.required<number>();
  terminal = input.required<TerminalPoint>();
  forma = input<FormaPagoPoint>('TARJETA');
  /** "Parte 1 de 2" en un pago mixto tarjeta + QR. */
  paso = input<string | null>(null);
  /** Cobro ya mandado (se recargó la página mientras se esperaba): se sigue ése en vez de crear otro. */
  reanudar = input<CobroPoint | null>(null);

  aprobado = output<CobroPoint>();
  /** Se cerró sin cobrar (rechazado, cancelado, vencido). */
  cerrado = output<void>();
  /** El boletero vio en el Point que el pago salió y lo anota a mano (Point sin respuesta). Va con
   * el cobro: la venta lo lleva igual, para que cuando MP lo apruebe no quede como cobrado sin venta. */
  anotarAMano = output<CobroPoint>();

  cobro = signal<CobroPoint | null>(null);
  error = signal<string | null>(null);
  cancelando = signal(false);
  /** Mercado Pago aceptó cancelar pero el Point todavía no la soltó (la aplica cuando puede, a
   * veces nunca): no se vuelve a pedir, se espera o se cancela en el propio Point. */
  cancelacionPedida = signal(false);
  /** Débito o crédito: se elige antes de mandar una tarjeta, así el Point pide directo ésa en vez
   * de preguntarle al cliente con qué paga. */
  tipoTarjeta = signal<TipoTarjetaPoint | null>(null);
  /** Falta elegir débito o crédito: todavía no se mandó nada al Point. */
  eligiendoTarjeta = computed(
    () => this.forma() === 'TARJETA' && this.tipoTarjeta() === null && this.cobro() === null && this.error() === null
  );
  /** Un mismo intento usa siempre la misma clave: si se pierde la respuesta, reintentar no crea otra orden. */
  private clave = crypto.randomUUID();
  private consultando = false;

  enCurso = computed(() => this.cobro()?.enCurso ?? false);
  /** Terminó sin cobrar: se muestra el motivo y se puede volver a intentar. */
  fallido = computed(() => {
    const c = this.cobro();
    return c !== null && !c.enCurso && c.estado !== 'APROBADO';
  });
  /** Ni se llegó a crear el cobro (sin respuesta del servidor): se reintenta con la misma clave. */
  sinCrear = computed(() => this.cobro() === null && this.error() !== null);

  constructor() {
    interval(INTERVALO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.consultar());
  }

  ngOnInit(): void {
    const previo = this.reanudar();
    if (previo) {
      this.cobro.set(previo);
      this.consultar();
    } else if (this.forma() !== 'TARJETA') {
      this.enviar();
    }
  }

  elegirTarjeta(tipo: TipoTarjetaPoint): void {
    this.tipoTarjeta.set(tipo);
    this.enviar();
  }

  enviar(): void {
    this.error.set(null);
    this.point.crearCobro(this.terminal().id, this.monto(), this.clave, this.forma(), this.tipoTarjeta()).subscribe({
      next: (c) => this.procesar(c),
      error: (err) => this.error.set(this.mensaje(err, 'No se pudo mandar el cobro al Point.')),
    });
  }

  /** Otro intento desde cero (el anterior se rechazó, venció o se canceló): orden nueva. */
  reintentar(): void {
    this.clave = crypto.randomUUID();
    this.cobro.set(null);
    this.cancelacionPedida.set(false);
    // Un error viejo (ej. falló "Cancelar cobro") taparía la elección de débito o crédito.
    this.error.set(null);
    // Con tarjeta se vuelve a preguntar: puede que haya fallado por haber elegido la equivocada.
    if (this.forma() === 'TARJETA') {
      this.tipoTarjeta.set(null);
      return;
    }
    this.enviar();
  }

  cancelar(): void {
    const c = this.cobro();
    if (!c) return;
    this.cancelando.set(true);
    this.error.set(null);
    this.point.cancelarCobro(c.id).subscribe({
      next: (r) => {
        this.cancelando.set(false);
        if (r.enCurso) this.cancelacionPedida.set(true);
        this.procesar(r);
      },
      error: (err) => {
        this.cancelando.set(false);
        this.error.set(this.mensaje(err, 'No se pudo cancelar el cobro.'));
      },
    });
  }

  cerrar(): void {
    this.olvidar();
    this.cerrado.emit();
  }

  confirmarAMano(): void {
    const c = this.cobro();
    if (!c) return;
    this.olvidar();
    this.anotarAMano.emit(c);
  }

  private olvidar(): void {
    const c = this.cobro();
    if (c && c.estado !== 'APROBADO') this.point.olvidarCobros([c.id]);
  }

  private consultar(): void {
    const c = this.cobro();
    if (!c || !c.enCurso || this.consultando) return;
    this.consultando = true;
    this.point.consultarCobro(c.id).subscribe({
      next: (r) => {
        this.consultando = false;
        this.error.set(null);
        this.procesar(r);
      },
      error: (err) => {
        this.consultando = false;
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 0
            ? 'Sin conexión con el servidor: el cobro sigue en el Point. Cuando vuelva la señal se actualiza solo.'
            : this.mensaje(err, 'No se pudo consultar el cobro.')
        );
      },
    });
  }

  private procesar(c: CobroPoint): void {
    this.cobro.set(c);
    if (c.estado === 'APROBADO') {
      this.point.recordarCobro(c);
      this.aprobado.emit(c);
    } else if (c.enCurso) {
      this.point.recordarCobro(c);
    } else {
      this.point.olvidarCobros([c.id]);
    }
  }

  private mensaje(err: unknown, porDefecto: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) return 'Sin conexión con el servidor.';
      if (typeof err.error === 'string' && err.error) return err.error;
    }
    return porDefecto;
  }
}
