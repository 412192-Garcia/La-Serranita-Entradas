import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { BoleteriaService, CotizacionResponse, LineaVentaPos, Reserva } from '../Services/boleteria.service';
import { TipoEntradaService } from '../Services/tipo-entrada.service';
import { CajaService, Caja } from '../Services/caja.service';
import { TipoEntrada } from '../models/tipo-entrada';
import { FormaPagoPos } from '../models/compra';
import { CabeceraInterna, EnlaceCabecera } from '../shared/cabecera-interna/cabecera-interna';
import { LucideLock, LucideLockOpen, LucideCircleCheck } from '@lucide/angular';

interface OpcionPago {
  valor: FormaPagoPos;
  etiqueta: string;
}

/** El orden es el de uso real en una boletería: el efectivo es el caso más frecuente. */
const FORMAS_PAGO: OpcionPago[] = [
  { valor: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
];

/** Números de un toque para las entradas obligatorias (pagas). Más que eso, se escribe a mano. */
const NUMEROS_RAPIDOS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

@Component({
  selector: 'app-pos',
  imports: [CurrencyPipe, FormsModule, CabeceraInterna, LucideLock, LucideLockOpen, LucideCircleCheck],
  templateUrl: './pos.html',
  styleUrl: './pos.css',
})
export class Pos implements OnInit {
  private boleteriaService = inject(BoleteriaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private cajaService = inject(CajaService);

  readonly formasPago = FORMAS_PAGO;
  readonly numerosRapidos = NUMEROS_RAPIDOS;
  readonly enlaceBoleteria: EnlaceCabecera = { texto: 'Ver reservas', ruta: '/boleteria' };

  // ---------- Caja: sin una abierta no se puede vender ----------
  cargandoCaja = signal(true);
  /** Caja abierta del boletero; null = no tiene ninguna en curso (hay que abrir una). */
  caja = signal<Caja | null>(null);

  montoApertura = signal<number | null>(null);
  abriendoCaja = signal(false);
  errorApertura = signal<string | null>(null);

  mostrarRetiro = signal(false);
  retiroMonto = signal<number | null>(null);
  retiroMotivo = signal('');
  registrandoRetiro = signal(false);
  errorRetiro = signal<string | null>(null);

  mostrarCierre = signal(false);
  montoContado = signal<number | null>(null);
  cerrandoCaja = signal(false);
  errorCierre = signal<string | null>(null);
  /** Resumen de la última caja cerrada: se muestra hasta que el boletero inicia la próxima. */
  cajaCerrada = signal<Caja | null>(null);

  tiposEntrada = signal<TipoEntrada[]>([]);
  cargando = signal(true);
  cantidades = signal<Record<number, number>>({});
  formaPago = signal<FormaPagoPos>('EFECTIVO_BOLETERIA');

  /** Tipos (obligatorios) cuyo campo de cantidad manual (10+) está abierto. */
  private customAbierto = signal<ReadonlySet<number>>(new Set());

  /** Lo que devuelve el backend para el carrito actual; null mientras no haya nada cargado. */
  cotizacion = signal<CotizacionResponse | null>(null);
  private pedidoCotizacion = new Subject<{ formaPago: FormaPagoPos; entradas: LineaVentaPos[] }>();

  cobrando = signal(false);
  error = signal<string | null>(null);
  /** Venta recién cerrada: mientras esté seteada se muestra el comprobante en pantalla. */
  ultimaVenta = signal<Reserva | null>(null);
  /** Con cuánto pagó el cliente, sólo para calcular el vuelto en efectivo. */
  pagaCon = signal<number | null>(null);

  // Los extras (ej. menú almuerzo) no se venden en boletería, sólo en la compra online:
  // el catálogo del POS ya se carga filtrado a ENTRADA.
  entradas = computed(() => this.tiposEntrada());

  /** Sólo los tipos con cantidad > 0, listos para mostrar en el carrito. */
  lineas = computed(() => {
    const cants = this.cantidades();
    return this.tiposEntrada()
      .filter((t) => (cants[t.id] ?? 0) > 0)
      .map((t) => ({ tipo: t, cantidad: cants[t.id] }));
  });

  hayItems = computed(() => this.lineas().length > 0);

  /** Precio de lista, sin promociones: sirve de fallback mientras llega la cotización. */
  private subtotalLista = computed(() =>
    this.lineas().reduce((acc, l) => acc + l.tipo.precio * l.cantidad, 0)
  );

  total = computed(() => this.cotizacion()?.subtotal ?? this.subtotalLista());
  ahorro = computed(() => this.cotizacion()?.ahorro ?? 0);

  vuelto = computed(() => {
    const pagaCon = this.pagaCon();
    if (pagaCon === null) return null;
    const diferencia = pagaCon - this.total();
    return diferencia >= 0 ? diferencia : null;
  });

  /** Igual que en la compra online: no se puede entrar sólo con menores. */
  private tieneObligatorio = computed(() =>
    this.lineas().some((l) => l.tipo.obligatorio)
  );

  puedeCobrar = computed(() => this.hayItems() && this.tieneObligatorio() && !this.cobrando());

  motivoBloqueo = computed(() => {
    if (!this.hayItems()) return null;
    if (!this.tieneObligatorio()) {
      const obligatorios = this.entradas().filter((t) => t.obligatorio).map((t) => t.nombre).join(' o ');
      return `Falta un pase de tipo ${obligatorios || 'obligatorio'}: no se puede ingresar sólo con menores.`;
    }
    return null;
  });

  constructor() {
    // El total depende de la forma de pago (el precio por grupo sólo existe en efectivo),
    // así que se recotiza ante cualquier cambio del carrito o del botón de cobro.
    // switchMap descarta respuestas viejas si el boletero sigue tocando botones rápido.
    effect(() => {
      const entradas = this.lineas().map((l) => ({ tipoEntradaId: l.tipo.id, cantidad: l.cantidad }));
      const formaPago = this.formaPago();
      if (entradas.length === 0) {
        this.cotizacion.set(null);
        return;
      }
      this.pedidoCotizacion.next({ formaPago, entradas });
    });

    this.pedidoCotizacion
      .pipe(
        switchMap(({ formaPago, entradas }) =>
          this.boleteriaService.cotizar(formaPago, entradas).pipe(catchError(() => of(null)))
        ),
        takeUntilDestroyed()
      )
      .subscribe((cotizacion) => this.cotizacion.set(cotizacion));
  }

  ngOnInit(): void {
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (ts) => {
        this.tiposEntrada.set(ts.filter((t) => t.activo && t.tipo === 'ENTRADA'));
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los tipos de entrada:', err);
        this.error.set('No se pudieron cargar las entradas. Recargá la página.');
        this.cargando.set(false);
      },
    });

    this.cajaService.getActual().subscribe({
      next: (c) => {
        this.caja.set(c);
        this.cargandoCaja.set(false);
      },
      error: (err) => {
        console.error('Error al consultar la caja:', err);
        this.cargandoCaja.set(false);
      },
    });
  }

  abrirCaja(): void {
    const monto = this.montoApertura();
    if (monto === null || monto < 0) {
      this.errorApertura.set('Ingresá el efectivo con el que arranca la caja.');
      return;
    }
    this.abriendoCaja.set(true);
    this.errorApertura.set(null);
    this.cajaService.abrir(monto).subscribe({
      next: (c) => {
        this.caja.set(c);
        this.abriendoCaja.set(false);
        this.montoApertura.set(null);
      },
      error: (err) => {
        this.errorApertura.set(typeof err?.error === 'string' ? err.error : 'No se pudo abrir la caja. Reintentá.');
        this.abriendoCaja.set(false);
      },
    });
  }

  toggleRetiro(): void {
    this.mostrarRetiro.update((v) => !v);
    this.retiroMonto.set(null);
    this.retiroMotivo.set('');
    this.errorRetiro.set(null);
  }

  confirmarRetiro(): void {
    const monto = this.retiroMonto();
    const motivo = this.retiroMotivo().trim();
    if (monto === null || monto <= 0) {
      this.errorRetiro.set('Ingresá un monto mayor a cero.');
      return;
    }
    if (!motivo) {
      this.errorRetiro.set('Indicá el motivo del retiro.');
      return;
    }
    this.registrandoRetiro.set(true);
    this.errorRetiro.set(null);
    this.cajaService.registrarRetiro(monto, motivo).subscribe({
      next: (c) => {
        this.caja.set(c);
        this.registrandoRetiro.set(false);
        this.mostrarRetiro.set(false);
        this.retiroMonto.set(null);
        this.retiroMotivo.set('');
      },
      error: (err) => {
        this.errorRetiro.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar el retiro. Reintentá.');
        this.registrandoRetiro.set(false);
      },
    });
  }

  toggleCierre(): void {
    this.mostrarCierre.update((v) => !v);
    this.montoContado.set(null);
    this.errorCierre.set(null);
  }

  confirmarCierre(): void {
    const monto = this.montoContado();
    if (monto === null || monto < 0) {
      this.errorCierre.set('Contá el efectivo y cargá el total.');
      return;
    }
    this.cerrandoCaja.set(true);
    this.errorCierre.set(null);
    this.cajaService.cerrar(monto).subscribe({
      next: (c) => {
        this.cajaCerrada.set(c);
        this.caja.set(null);
        this.mostrarCierre.set(false);
        this.cerrandoCaja.set(false);
      },
      error: (err) => {
        this.errorCierre.set(typeof err?.error === 'string' ? err.error : 'No se pudo cerrar la caja. Reintentá.');
        this.cerrandoCaja.set(false);
      },
    });
  }

  /** Cierra la pantalla de resumen del cierre y deja lista la apertura de la próxima caja. */
  iniciarNuevaCaja(): void {
    this.cajaCerrada.set(null);
  }

  // ---------- Validar una reserva anticipada sin salir de la pantalla de venta ----------

  mostrarBusquedaReserva = signal(false);
  textoBusquedaReserva = signal('');
  buscandoReserva = signal(false);
  errorBusquedaReserva = signal<string | null>(null);
  resultadosReserva = signal<Reserva[] | null>(null);
  procesandoReservaId = signal<number | null>(null);

  toggleBusquedaReserva(): void {
    this.mostrarBusquedaReserva.update((v) => !v);
    this.textoBusquedaReserva.set('');
    this.errorBusquedaReserva.set(null);
    this.resultadosReserva.set(null);
  }

  buscarReserva(): void {
    const texto = this.textoBusquedaReserva().trim();
    if (!texto || this.buscandoReserva()) return;

    this.buscandoReserva.set(true);
    this.errorBusquedaReserva.set(null);

    this.boleteriaService.buscar({ texto, tipo: 'ANTICIPADA', page: 0, size: 10 }).subscribe({
      next: (pagina) => {
        this.resultadosReserva.set(pagina.content);
        this.buscandoReserva.set(false);
      },
      error: (err) => {
        console.error('Error al buscar la reserva:', err);
        this.errorBusquedaReserva.set('No se pudo buscar. Revisá la conexión y reintentá.');
        this.buscandoReserva.set(false);
      },
    });
  }

  procesandoReserva(reserva: Reserva): boolean {
    return this.procesandoReservaId() === reserva.id;
  }

  /** Compra ya pagada online (APROBADO) → habilita el ingreso. */
  validarReserva(reserva: Reserva): void {
    this.ejecutarAccionReserva(reserva, this.boleteriaService.validarIngreso(reserva.id));
  }

  /** Reserva con pago en efectivo (RESERVADO_EFECTIVO) → cobra y habilita el ingreso. */
  cobrarReserva(reserva: Reserva): void {
    this.ejecutarAccionReserva(reserva, this.boleteriaService.cobrarEfectivoYValidar(reserva.id));
  }

  private ejecutarAccionReserva(reserva: Reserva, accion: import('rxjs').Observable<Reserva>): void {
    this.procesandoReservaId.set(reserva.id);
    this.errorBusquedaReserva.set(null);

    accion.subscribe({
      next: (actualizada) => {
        this.resultadosReserva.update((rs) => (rs ?? []).map((r) => (r.id === actualizada.id ? actualizada : r)));
        this.procesandoReservaId.set(null);
      },
      error: (err) => {
        console.error('Error al validar la reserva:', err);
        this.errorBusquedaReserva.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo completar la validación. Reintentá.'
        );
        this.procesandoReservaId.set(null);
      },
    });
  }

  getCantidad(id: number): number {
    return this.cantidades()[id] ?? 0;
  }

  cambiarCantidad(id: number, delta: number): void {
    this.cantidades.update((c) => ({ ...c, [id]: Math.max(0, (c[id] ?? 0) + delta) }));
  }

  private setCantidad(id: number, cantidad: number): void {
    const valor = Math.max(0, Math.floor(cantidad) || 0);
    this.cantidades.update((c) => ({ ...c, [id]: valor }));
  }

  mostrarCustom(id: number): boolean {
    return this.customAbierto().has(id);
  }

  /** Toca un número: lo fija como cantidad; tocar el mismo de nuevo la vacía. */
  elegirCantidad(id: number, n: number): void {
    this.setCantidad(id, this.getCantidad(id) === n ? 0 : n);
    this.customAbierto.update((s) => {
      if (!s.has(id)) return s;
      const copia = new Set(s);
      copia.delete(id);
      return copia;
    });
  }

  /** Abre/cierra el paso a cantidad manual (11+). Al abrir, arranca en 11 para no pisar la grilla fija. */
  toggleCustom(id: number): void {
    const estabaAbierto = this.customAbierto().has(id);
    this.customAbierto.update((s) => {
      const copia = new Set(s);
      if (!copia.delete(id)) copia.add(id);
      return copia;
    });
    if (!estabaAbierto && this.getCantidad(id) < 11) {
      this.setCantidad(id, 11);
    }
  }

  incrementarCustom(id: number): void {
    this.setCantidad(id, Math.max(11, this.getCantidad(id) + 1));
  }

  /** Por debajo de 11 ya no tiene sentido seguir en modo manual: vuelve a la grilla fija en 10. */
  decrementarCustom(id: number): void {
    const actual = this.getCantidad(id);
    if (actual <= 11) {
      this.setCantidad(id, 10);
      this.customAbierto.update((s) => {
        const copia = new Set(s);
        copia.delete(id);
        return copia;
      });
    } else {
      this.setCantidad(id, actual - 1);
    }
  }

  limpiar(): void {
    this.cantidades.set({});
    this.pagaCon.set(null);
    this.error.set(null);
  }

  cobrar(): void {
    if (!this.puedeCobrar()) return;

    this.cobrando.set(true);
    this.error.set(null);

    const entradas = this.lineas().map((l) => ({ tipoEntradaId: l.tipo.id, cantidad: l.cantidad }));

    this.boleteriaService.registrarVentaPos({ formaPago: this.formaPago(), entradas }).subscribe({
      next: (venta) => {
        this.ultimaVenta.set(venta);
        this.cobrando.set(false);
      },
      error: (err) => {
        console.error('Error al registrar la venta:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar la venta. Reintentá.');
        this.cobrando.set(false);
      },
    });
  }

  /** Cierra el comprobante y deja la pantalla lista para el próximo cliente. */
  nuevaVenta(): void {
    this.ultimaVenta.set(null);
    this.limpiar();
  }
}
