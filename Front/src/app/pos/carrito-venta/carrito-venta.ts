import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { BoleteriaService, CotizacionResponse, DescuentoPos, LineaArticuloPos, LineaVentaPos, Reserva, VentaPosRequest } from '../../services/boleteria.service';
import { OperacionesPendientesService } from '../../services/operaciones-pendientes.service';
import { TipoEntrada } from '../../models/tipo-entrada';
import { Promocion } from '../../models/promocion';
import { FilaArticuloCarrito, LineaEntradaFija } from '../../models/venta-pos';
import { FormaPagoPos } from '../../models/compra';
import { FORMAS_PAGO } from '../../models/forma-pago';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { ConectividadService } from '../../services/conectividad.service';
import { DescuentoEfectivo } from '../../services/configuracion.service';
import { cotizarLocalmente } from '../../shared/calculo-precio.util';
import { PesosPipe } from '../../shared/pesos.pipe';
import { LucideTrash2 } from '@lucide/angular';

export interface ItemVentaResumen {
  cantidad: number;
  nombre: string;
}

/** Lo que necesita el comprobante de venta, capturado en el momento del cobro. */
export interface VentaPosConfirmada {
  venta: Reserva;
  formaPago: FormaPagoPos;
  vuelto: number | null;
  items: ItemVentaResumen[];
  /** Si este cobro en efectivo se hizo en dólares (no es una forma de pago aparte, sigue siendo EFECTIVO_BOLETERIA). */
  pagoEnDolares: boolean;
  /** La venta quedó encolada sin confirmar contra el servidor: los datos son los calculados localmente. */
  pendiente: boolean;
}

@Component({
  selector: 'app-carrito-venta',
  imports: [PesosPipe, DecimalPipe, FormsModule, MoneyInputDirective, LucideTrash2],
  templateUrl: './carrito-venta.html',
  styleUrl: './carrito-venta.css',
})
export class CarritoVenta {
  private boleteriaService = inject(BoleteriaService);
  private conectividad = inject(ConectividadService);
  private pendientes = inject(OperacionesPendientesService);

  tiposEntrada = input<TipoEntrada[]>([]);
  cantidades = input<Record<number, number>>({});
  articulosCarrito = input<FilaArticuloCarrito[]>([]);
  promociones = input<Promocion[]>([]);
  /** Escalones de precio por grupo, para poder cotizar sin conexión. */
  descuentosEfectivo = input<DescuentoEfectivo[]>([]);
  /** Caja propia del boletero: viaja en el payload por si esto se rechaza, para que un admin
   * sepa exactamente qué caja reabrir y reintentar. */
  cajaId = input.required<number>();
  /** Si viene seteada, este cobro no crea una venta nueva: cierra esta reserva
   * RESERVADO_EFECTIVO (cargada en el POS desde el panel de anticipadas). */
  compraReservada = input<Reserva | null>(null);
  /** Líneas de la reserva que no se editan desde el carrito (extras, tipos fuera del catálogo):
   * se muestran, suman al total y viajan en el cobro, pero sin +/- ni ✕. */
  entradasFijas = input<LineaEntradaFija[]>([]);

  quitarArticulo = output<number>();
  quitarEntrada = output<number>();
  limpiar = output<void>();
  ventaRegistrada = output<VentaPosConfirmada>();

  readonly formasPago = FORMAS_PAGO;

  formaPago = signal<FormaPagoPos>('EFECTIVO_BOLETERIA');

  // ---------- Descuento: promo con nombre o manual ad-hoc, mutuamente excluyentes ----------
  modoDescuento = signal<'ninguno' | 'promo' | 'manual'>('ninguno');
  promocionSeleccionadaId = signal<number | null>(null);
  descuentoManualPorcentaje = signal<number | null>(null);
  descuentoManualMonto = signal<number | null>(null);

  promocionesActivas = computed(() => this.promociones().filter((p) => p.activo));

  /** Lo que devuelve el backend para el carrito actual; null mientras no haya nada cargado. */
  cotizacion = signal<CotizacionResponse | null>(null);
  private pedidoCotizacion = new Subject<{
    formaPago: FormaPagoPos;
    entradas: LineaVentaPos[];
    articulos: LineaArticuloPos[];
    descuento: DescuentoPos;
  }>();

  cobrando = signal(false);
  error = signal<string | null>(null);
  /** Con cuánto pagó el cliente, sólo para calcular el vuelto en efectivo (en pesos). */
  pagaCon = signal<number | null>(null);

  // ---------- Pago en efectivo hecho en dólares: no es una forma de pago aparte (sigue
  // siendo EFECTIVO_BOLETERIA), es un checkbox dentro de Efectivo. ----------
  pagoEnDolares = signal(false);
  /** Cotización del dólar usada para este cobro (ARS por USD). */
  cotizacionDolar = signal<number | null>(null);
  /** Con cuántos dólares pagó el cliente. */
  pagaConDolares = signal<number | null>(null);

  /** Sólo los tipos con cantidad > 0, listos para mostrar en el carrito. */
  lineas = computed(() => {
    const cants = this.cantidades();
    return this.tiposEntrada()
      .filter((t) => (cants[t.id] ?? 0) > 0)
      .map((t) => ({ tipo: t, cantidad: cants[t.id] }));
  });

  hayItems = computed(
    () => this.lineas().length > 0 || this.articulosCarrito().length > 0 || this.entradasFijas().length > 0
  );

  /** {tipoEntradaId, cantidad} de las líneas fijas de la reserva, para cotizar y cobrar. */
  private entradasFijasPayload(): LineaVentaPos[] {
    return this.entradasFijas().map((e) => ({ tipoEntradaId: e.tipoEntradaId, cantidad: e.cantidad }));
  }

  /** Precio de lista, sin promociones: sirve de fallback mientras llega la cotización. */
  private subtotalLista = computed(
    () =>
      this.lineas().reduce((acc, l) => acc + l.tipo.precio * l.cantidad, 0) +
      this.articulosCarrito().reduce((acc, a) => acc + a.precioUnitario * a.cantidad, 0) +
      this.entradasFijas().reduce((acc, e) => acc + e.precioUnitario * e.cantidad, 0)
  );

  total = computed(() => this.cotizacion()?.subtotal ?? this.subtotalLista());
  ahorro = computed(() => this.cotizacion()?.ahorro ?? 0);
  /** Tope del descuento manual en $: no tiene sentido descontar más que la suma de lo que hay
   * en el carrito (ver setDescuentoManualMonto). */
  montoMaximoDescuento = computed(() => this.subtotalLista());

  vuelto = computed(() => {
    const pagaCon = this.pagaCon();
    if (pagaCon === null) return null;
    const diferencia = pagaCon - this.total();
    return diferencia >= 0 ? diferencia : null;
  });

  /** Cuánto falta para llegar al total (pago en pesos). Null si "paga con" está vacío o ya alcanza. */
  falta = computed(() => {
    const pagaCon = this.pagaCon();
    if (pagaCon === null) return null;
    const diferencia = this.total() - pagaCon;
    return diferencia > 0 ? diferencia : null;
  });

  /** A cuántos dólares equivale el total, según la cotización cargada. Null hasta que se carga la cotización. */
  montoEnDolares = computed(() => {
    const cotizacion = this.cotizacionDolar();
    if (cotizacion === null || cotizacion <= 0) return null;
    return this.total() / cotizacion;
  });

  /** Vuelto en PESOS que hay que darle al cliente que pagó en dólares. Null hasta que "paga con" alcanza el total. */
  vueltoEnPesosPorDolares = computed(() => {
    const pagaCon = this.pagaConDolares();
    const cotizacion = this.cotizacionDolar();
    if (pagaCon === null || cotizacion === null || cotizacion <= 0) return null;
    const diferencia = pagaCon * cotizacion - this.total();
    return diferencia >= 0 ? diferencia : null;
  });

  /** Cuánto falta en PESOS para llegar al total, pagando en dólares. Null si aún no hay datos o ya alcanza. */
  faltaEnPesosPorDolares = computed(() => {
    const pagaCon = this.pagaConDolares();
    const cotizacion = this.cotizacionDolar();
    if (pagaCon === null || cotizacion === null || cotizacion <= 0) return null;
    const diferencia = this.total() - pagaCon * cotizacion;
    return diferencia > 0 ? diferencia : null;
  });

  /** Igual que en la compra online: no se puede entrar sólo con menores. */
  private tieneEntradas = computed(() => this.lineas().length > 0);
  private tieneObligatorio = computed(() => this.lineas().some((l) => l.tipo.obligatorio));

  /** Pagando en dólares hace falta la cotización y que "paga con" alcance el total: sin eso no hay un vuelto válido que calcular. */
  private faltaCompletarPagoDolares = computed(() => this.pagoEnDolares() && this.vueltoEnPesosPorDolares() === null);

  puedeCobrar = computed(
    () => this.hayItems() && (!this.tieneEntradas() || this.tieneObligatorio()) && !this.faltaCompletarPagoDolares() && !this.cobrando()
  );

  motivoBloqueo = computed(() => {
    if (!this.hayItems()) return null;
    // Una venta sólo de artículos (sin entradas) no exige ningún pase obligatorio.
    if (this.tieneEntradas() && !this.tieneObligatorio()) {
      const obligatorios = this.tiposEntrada().filter((t) => t.obligatorio).map((t) => t.nombre).join(' o ');
      return `Falta un pase de tipo ${obligatorios || 'obligatorio'}: no se puede ingresar sólo con menores.`;
    }
    return null;
  });

  constructor() {
    // El total depende de la forma de pago (el precio por grupo sólo existe en efectivo) y
    // del descuento elegido, así que se recotiza ante cualquier cambio del carrito, la forma
    // de pago o el descuento. Pagar en dólares no cambia el precio (sigue siendo efectivo),
    // así que no hace falta recotizar al tocar ese checkbox. switchMap descarta respuestas
    // viejas si el boletero sigue tocando botones rápido.
    effect(() => {
      const entradas = [
        ...this.lineas().map((l) => ({ tipoEntradaId: l.tipo.id, cantidad: l.cantidad })),
        ...this.entradasFijasPayload(),
      ];
      const articulos = this.articulosCarritoPayload();
      const formaPago = this.formaPago();
      const descuento = this.descuentoPayload();
      if (entradas.length === 0 && articulos.length === 0) {
        this.cotizacion.set(null);
        return;
      }
      this.pedidoCotizacion.next({ formaPago, entradas, articulos, descuento });
    });

    this.pedidoCotizacion
      .pipe(
        switchMap(({ formaPago, entradas, articulos, descuento }) => {
          // Sin señal ni siquiera se intenta: el cálculo local da el mismo número y evita
          // que el boletero vea el total "colgado" esperando un timeout en cada tecla.
          const local = () => this.cotizarSinConexion(formaPago, entradas, articulos, descuento);
          if (!this.conectividad.enLinea()) return of(local());
          return this.boleteriaService
            .cotizar(formaPago, entradas, descuento, articulos)
            .pipe(catchError(() => of(local())));
        }),
        takeUntilDestroyed()
      )
      .subscribe((cotizacion) => this.cotizacion.set(cotizacion));
  }

  private cotizarSinConexion(
    formaPago: FormaPagoPos,
    entradas: LineaVentaPos[],
    articulos: LineaArticuloPos[],
    descuento: DescuentoPos
  ): CotizacionResponse {
    return cotizarLocalmente(
      formaPago,
      entradas,
      articulos,
      descuento,
      this.tiposEntrada(),
      this.descuentosEfectivo(),
      this.promociones(),
      this.entradasFijas()
    );
  }

  /** Payload de descuento a mandar en cotizar/registrarVentaPos según el modo elegido. */
  private descuentoPayload(): DescuentoPos {
    if (this.modoDescuento() === 'promo' && this.promocionSeleccionadaId() !== null) {
      return { promocionId: this.promocionSeleccionadaId() };
    }
    if (this.modoDescuento() === 'manual') {
      return {
        descuentoManualPorcentaje: this.descuentoManualPorcentaje() ?? undefined,
        descuentoManualMonto: this.descuentoManualMonto() ?? undefined,
      };
    }
    return {};
  }

  cambiarModoDescuento(modo: 'ninguno' | 'promo' | 'manual'): void {
    this.modoDescuento.set(modo);
    this.promocionSeleccionadaId.set(null);
    this.descuentoManualPorcentaje.set(null);
    this.descuentoManualMonto.set(null);
  }

  /** El % y el $ manual son excluyentes entre sí: tipear uno limpia el otro. */
  setDescuentoManualPorcentaje(valor: number | null): void {
    this.descuentoManualPorcentaje.set(valor);
    if (valor !== null) this.descuentoManualMonto.set(null);
  }

  /** No deja cargar un descuento mayor a lo que hay en el carrito — sin esto, nada evitaba
   * escribir de más y que el total diera negativo hasta que lo rechazara el backend. */
  setDescuentoManualMonto(valor: number | null): void {
    const acotado = valor === null ? null : Math.min(valor, this.montoMaximoDescuento());
    this.descuentoManualMonto.set(acotado);
    if (acotado !== null) this.descuentoManualPorcentaje.set(null);
  }

  private limpiarDescuento(): void {
    this.modoDescuento.set('ninguno');
    this.promocionSeleccionadaId.set(null);
    this.descuentoManualPorcentaje.set(null);
    this.descuentoManualMonto.set(null);
  }

  private limpiarPagoDolares(): void {
    this.pagoEnDolares.set(false);
    this.cotizacionDolar.set(null);
    this.pagaConDolares.set(null);
  }

  /** El checkbox de dólares sólo tiene sentido pagando en efectivo: cambiar de forma de pago lo apaga. */
  setFormaPago(formaPago: FormaPagoPos): void {
    this.formaPago.set(formaPago);
    if (formaPago !== 'EFECTIVO_BOLETERIA') {
      this.limpiarPagoDolares();
    }
  }

  /** Tildar dólares oculta el "paga con" en pesos (son mutuamente excluyentes dentro de Efectivo). */
  setPagoEnDolares(valor: boolean): void {
    this.pagoEnDolares.set(valor);
    this.cotizacionDolar.set(null);
    this.pagaConDolares.set(null);
    if (valor) this.pagaCon.set(null);
  }

  private articulosCarritoPayload(): LineaArticuloPos[] {
    return this.articulosCarrito().map((a) => ({
      articuloVarioId: a.articuloVarioId,
      descripcionLibre: a.descripcionLibre,
      precioUnitario: a.precioUnitario,
      cantidad: a.cantidad,
    }));
  }

  onQuitarArticulo(index: number): void {
    this.quitarArticulo.emit(index);
  }

  onQuitarEntrada(tipoEntradaId: number): void {
    this.quitarEntrada.emit(tipoEntradaId);
  }

  onLimpiar(): void {
    this.pagaCon.set(null);
    this.limpiarPagoDolares();
    this.error.set(null);
    this.limpiarDescuento();
    this.limpiar.emit();
  }

  async cobrar(): Promise<void> {
    if (!this.puedeCobrar()) return;

    this.cobrando.set(true);
    this.error.set(null);

    const entradas = [
      ...this.lineas().map((l) => ({ tipoEntradaId: l.tipo.id, cantidad: l.cantidad })),
      ...this.entradasFijasPayload(),
    ];
    const articulos = this.articulosCarritoPayload();
    const formaPago = this.formaPago();
    const pagoEnDolares = this.pagoEnDolares();
    const vuelto = pagoEnDolares ? this.vueltoEnPesosPorDolares() : this.vuelto();
    const total = this.total();
    const items: ItemVentaResumen[] = [
      ...this.lineas().map((l) => ({ cantidad: l.cantidad, nombre: l.tipo.nombre })),
      ...this.entradasFijas().map((e) => ({ cantidad: e.cantidad, nombre: e.nombre })),
      ...this.articulosCarrito().map((a) => ({ cantidad: a.cantidad, nombre: a.nombre })),
    ];

    const dolaresPayload = pagoEnDolares
      ? { cotizacionDolar: this.cotizacionDolar(), dolaresRecibidos: this.pagaConDolares() }
      : {};

    const reservaId = this.compraReservada()?.id;

    const payload: VentaPosRequest = {
      formaPago,
      entradas,
      articulos,
      cajaId: this.cajaId(),
      ...(reservaId ? { compraReservadaId: reservaId } : {}),
      ...this.descuentoPayload(),
      ...dolaresPayload,
    };

    // Toda venta pasa por la cola: si hay señal se confirma al instante y sigue todo igual que
    // antes, y si no, queda guardada y se sincroniza sola (ver OperacionesPendientesService).
    const resultado = await this.pendientes.ejecutar<Reserva>({ tipo: 'VENTA', payload });
    this.cobrando.set(false);

    if (resultado.confirmada) {
      this.ventaRegistrada.emit({ venta: resultado.resultado, formaPago, vuelto, items, pagoEnDolares, pendiente: false });
      return;
    }

    // Rechazo real del servidor (no un problema de conexión): la venta NO se registró, así que
    // mostrarla como "cobrada sin conexión" sería engañoso — el carrito queda intacto para
    // revisar y reintentar, con el motivo bien visible.
    if (resultado.rechazada) {
      this.error.set(resultado.mensaje);
      return;
    }

    // Sin confirmación del servidor se arma un comprobante con lo calculado en el navegador:
    // el cliente ya pagó y está entrando, no se lo puede hacer esperar a que vuelva la señal.
    const reserva = this.compraReservada();
    const ventaLocal: Reserva = {
      id: reserva?.id ?? 0,
      codigoReserva: reserva?.codigoReserva ?? 'PENDIENTE',
      cliente: reserva?.cliente ?? null,
      contactEmail: null,
      contactPhone: null,
      fechaVisita: reserva?.fechaVisita ?? new Date().toISOString().slice(0, 10),
      montoTotal: total,
      descuentoAplicado: 0,
      estado: reserva ? 'USADO' : 'VENDIDO_EN_PUERTA',
      formaPago,
      detalles: null,
      fechaValidacion: new Date().toISOString(),
      usuarioValidador: null,
      receptorNombre: null,
      receptorEmail: null,
      receptorDni: null,
      receptorTelefono: null,
    };
    this.ventaRegistrada.emit({ venta: ventaLocal, formaPago, vuelto, items, pagoEnDolares, pendiente: true });
  }
}
