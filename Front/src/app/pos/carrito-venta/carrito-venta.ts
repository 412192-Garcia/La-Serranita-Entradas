import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
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
import { FormaPagoPos, FormaPagoVentaPos } from '../../models/compra';
import { FORMAS_PAGO } from '../../models/forma-pago';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { ConectividadService } from '../../services/conectividad.service';
import { DescuentoEfectivo } from '../../services/configuracion.service';
import { cotizarLocalmente, ReservaParaCotizar } from '../../shared/calculo-precio.util';
import { aFechaISO, aFechaHoraISO } from '../../shared/fecha.util';
import { PesosPipe } from '../../shared/pesos.pipe';
import { LucideMail, LucidePrinter, LucideTrash2 } from '@lucide/angular';
import { FacturaService } from '../../services/factura.service';
import { DestinoFactura, FacturacionPos } from '../../models/factura';
import { ImpresorasService } from '../../services/impresoras.service';
import { CobroPoint, FormaPagoPoint, PointService, TerminalPoint } from '../../services/point.service';
import { CobroPointModal } from '../cobro-point/cobro-point';


export interface ItemVentaResumen {
  cantidad: number;
  nombre: string;
}

/** Lo que necesita el comprobante de venta, capturado en el momento del cobro. */
export interface VentaPosConfirmada {
  venta: Reserva;
  formaPago: FormaPagoVentaPos;
  vuelto: number | null;
  items: ItemVentaResumen[];
  /** Si este cobro en efectivo se hizo en dólares (no es una forma de pago aparte, sigue siendo EFECTIVO_BOLETERIA). */
  pagoEnDolares: boolean;
  /** La venta quedó encolada sin confirmar contra el servidor: los datos son los calculados localmente. */
  pendiente: boolean;
  /** Pago mixto: segunda forma de pago usada, con su monto. Null en una venta con una sola forma. */
  formaPagoSecundaria: FormaPagoPos | null;
  montoFormaPagoSecundaria: number | null;
  /** Lo que se pidió de factura; null si no se factura (mail con el campo vacío, $0, etc.). */
  facturacion: FacturacionPos | null;
}

const EMAIL_VALIDO = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Una parte de la venta que se cobra con el Point: la tarjeta o el QR. */
interface ParteCobroPoint {
  forma: FormaPagoPoint;
  monto: number;
}

/** La ventana del Point abierta: qué parte se cobra y con qué Point, o un cobro que se retoma. */
interface PedidoPoint {
  monto: number;
  forma: FormaPagoPoint;
  terminal: TerminalPoint;
  reanudar: CobroPoint | null;
  /** "Parte 1 de 2" en un pago mixto tarjeta + QR. */
  paso: string | null;
}

@Component({
  selector: 'app-carrito-venta',
  imports: [PesosPipe, DecimalPipe, FormsModule, MoneyInputDirective, LucideTrash2, LucidePrinter, LucideMail, CobroPointModal],
  templateUrl: './carrito-venta.html',
  styleUrl: './carrito-venta.css',
})
export class CarritoVenta {
  private boleteriaService = inject(BoleteriaService);
  private conectividad = inject(ConectividadService);
  private pendientes = inject(OperacionesPendientesService);
  private facturaService = inject(FacturaService);

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

  /** Arranca sin elegir a propósito: si viniera Efectivo por defecto, es muy fácil cobrar en
   * efectivo una venta que en realidad fue con tarjeta por olvidarse de cambiarlo. */
  formaPago = signal<FormaPagoPos | null>(null);

  // ---------- Descuento: promo con nombre, manual ad-hoc, o precio fijo, mutuamente excluyentes ----------
  modoDescuento = signal<'ninguno' | 'promo' | 'manual' | 'precioFijo'>('ninguno');
  promocionSeleccionadaId = signal<number | null>(null);
  descuentoManualPorcentaje = signal<number | null>(null);
  descuentoManualMonto = signal<number | null>(null);
  /** Precio final que el cajero quiere cobrar (ej. "5 entradas a 5.000$ en total"): la app
   * calcula la diferencia contra el precio de lista como descuento manual por monto, así el
   * cajero no tiene que restar a mano. */
  precioFijoDeseado = signal<number | null>(null);

  // ---------- Factura: "Imprimir factura" o "Enviar por mail", una u otra. Mail con el campo
  // vacío = no facturar, sin error. La forma de pago sólo cambia cuál arranca marcada, según
  // Configuración > Cobros (de fábrica: tarjeta y QR en Imprimir, efectivo en mail vacío). ----------
  /** Facturación de boletería (submódulo de Facturación): apagada, el POS no ofrece facturar. */
  readonly facturacionHabilitada = this.facturaService.habilitadaBoleteria;
  destinoFactura = signal<DestinoFactura>('MAIL');
  emailFactura = signal('');
  /** Ticketeras: compartido con el botón "Ticketera" de la barra, donde se elige (una vez por
   * tablet, no en cada venta). Acá sólo importa si se puede imprimir y, si no, por qué. */
  private impresoras = inject(ImpresorasService);
  imprimirFacturaDisponible = this.impresoras.lista;
  motivoImprimirNoDisponible = this.impresoras.motivoNoDisponible;
  emailFacturaInvalido = computed(() => {
    const email = this.emailFactura().trim();
    return this.destinoFactura() === 'MAIL' && email !== '' && !EMAIL_VALIDO.test(email);
  });

  // ---------- Cobro con el Point de Mercado Pago: si esta tablet tiene un Point elegido y hay
  // señal, la parte en tarjeta y/o QR se manda al Point y la venta se registra recién con los
  // pagos aprobados. Sin Point (o sin señal) se anota a mano, como siempre. ----------
  readonly point = inject(PointService);
  /** Lo que hay que cobrar con el Point: la tarjeta y/o el QR (el total, o cada parte de un pago mixto). */
  partesPoint = computed<ParteCobroPoint[]>(() => {
    if (this.sinCobro()) return [];
    const partes: ParteCobroPoint[] = [];
    const agregar = (forma: FormaPagoPos | null, monto: number | null) => {
      // En centavos exactos: lo que cobra el Point vuelve de la base con 2 decimales, y una resta
      // como total − secundaria puede dar 7747.449999999999.
      if ((forma === 'TARJETA' || forma === 'MERCADO_PAGO_QR') && monto !== null && monto > 0)
        partes.push({ forma, monto: Math.round(monto * 100) / 100 });
    };
    if (this.pagoMixtoActivo()) {
      agregar(this.formaPago(), this.montoFormaPagoPrincipal());
      agregar(this.formaPagoSecundaria(), this.montoFormaPagoSecundaria());
    } else {
      agregar(this.formaPago(), this.total());
    }
    return partes;
  });
  cobraConPoint = computed(() => this.partesPoint().length > 0 && this.point.usable());
  /** Hay varios Points y esta tablet no eligió ninguno: no se sabe a cuál mandar el cobro. */
  faltaElegirPoint = computed(
    () => this.partesPoint().length > 0 && this.point.faltaElegir() && this.conectividad.enLinea()
  );
  /** Ventana del Point abierta: qué parte se cobra y con qué Point, o retomando un cobro que quedó esperando. */
  pedidoPoint = signal<PedidoPoint | null>(null);
  pedidosPointAbiertos = computed(() => {
    const p = this.pedidoPoint();
    return p ? [p] : [];
  });
  /** Lo que la ventana del Point le devuelve al cobro en curso. */
  private resolverPoint: ((r: CobroPoint | null) => void) | null = null;
  /** Cobros que el Point aprobó y todavía no son una venta (la venta se rechazó, se recargó la página...). */
  cobrosSinVenta = computed(() => this.point.cobrosPendientes().filter((c) => c.estado === 'APROBADO' && c.compraId === null));
  /** Qué cobro aprobado paga cada parte de esta venta (misma forma y mismo monto): esas partes no
   * se vuelven a cobrar. Null en una parte que todavía hay que cobrar. */
  private asignacionCobros = computed<(CobroPoint | null)[]>(() => {
    const libres = [...this.cobrosSinVenta()];
    return this.partesPoint().map((parte) => {
      const i = libres.findIndex((c) => c.formaPago === parte.forma && Math.round(c.monto * 100) === Math.round(parte.monto * 100));
      return i >= 0 ? libres.splice(i, 1)[0] : null;
    });
  });
  /** Los cobros aprobados que esta venta va a usar. */
  cobrosQueSirven = computed(() =>
    this.cobraConPoint() ? this.asignacionCobros().filter((c): c is CobroPoint => c !== null) : []
  );
  /** Cobros aprobados que no son de esta venta: hay que devolverlos o descartarlos antes de cobrar
   * otra vez con el Point (si no, quedarían olvidados). */
  cobrosQueSobran = computed(() => {
    const usados = new Set(this.cobrosQueSirven().map((c) => c.id));
    return this.cobrosSinVenta().filter((c) => !usados.has(c.id));
  });
  /** Ya está todo cobrado en el Point: "Registrar venta" sin pasar otra tarjeta. */
  todoCobradoEnPoint = computed(() => this.cobraConPoint() && this.asignacionCobros().every((c) => c !== null));
  devolviendoCobroId = signal<number | null>(null);

  promocionesActivas = computed(() => this.promociones().filter((p) => p.activo));

  /** Lo que devuelve el backend para el carrito actual; null mientras no haya nada cargado. */
  cotizacion = signal<CotizacionResponse | null>(null);
  /** Hay una cotización pedida que todavía no volvió: el total en pantalla puede ser el anterior. */
  cotizando = signal(false);
  private pedidoCotizacion = new Subject<{
    formaPago: FormaPagoPos;
    entradas: LineaVentaPos[];
    articulos: LineaArticuloPos[];
    descuento: DescuentoPos;
    reservaId: number | undefined;
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

  // ---------- Pago mixto: el cliente paga parte con una forma y el resto con otra (ej. mitad
  // efectivo, mitad tarjeta). Excluyente con dólares. ----------
  pagoMixtoActivo = signal(false);
  formaPagoSecundaria = signal<FormaPagoPos | null>(null);
  /** Lo que se cobra con formaPagoSecundaria; lo de la forma principal es el resto del total. */
  montoFormaPagoSecundaria = signal<number | null>(null);

  /** Opciones para la segunda forma: las 3 presenciales salvo la ya elegida como principal. La
   * tarjeta es la excepción: tarjeta + tarjeta = dos tarjetas distintas ("Tarjeta 1" y "Tarjeta 2"). */
  opcionesFormaPagoSecundaria = computed(() =>
    FORMAS_PAGO.filter((f) => f.valor !== this.formaPago() || f.valor === 'TARJETA').map((f) =>
      f.valor === this.formaPago() ? { ...f, etiqueta: 'Tarjeta 2' } : f
    )
  );
  /** Pago mixto con dos tarjetas distintas. */
  dosTarjetas = computed(
    () => this.pagoMixtoActivo() && this.formaPago() === 'TARJETA' && this.formaPagoSecundaria() === 'TARJETA'
  );

  /** Etiqueta de la forma principal elegida, para mostrar "Efectivo: $X" en el pago mixto. */
  etiquetaFormaPagoActual = computed(() =>
    this.dosTarjetas() ? 'Tarjeta 1' : (FORMAS_PAGO.find((f) => f.valor === this.formaPago())?.etiqueta ?? '')
  );

  /** Etiqueta de la segunda forma ya elegida, para el label "Monto en Tarjeta". Genérico
   * mientras no eligió ninguna todavía (el input queda deshabilitado hasta entonces). */
  etiquetaFormaPagoSecundariaActual = computed(
    () => FORMAS_PAGO.find((f) => f.valor === this.formaPagoSecundaria())?.etiqueta ?? 'la 2ª forma'
  );

  /** Lo que se cobra con la forma principal cuando hay pago mixto: el resto del total. */
  montoFormaPagoPrincipal = computed(() => Math.max(0, this.total() - (this.montoFormaPagoSecundaria() ?? 0)));

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

  /**
   * Precios de la reserva cargada (si hay una): el cliente paga lo que reservó, no lo que cueste
   * hoy. Es lo que el backend usa al cobrar, así que el carrito tiene que mostrar lo mismo.
   */
  private reservaParaCotizar = computed<ReservaParaCotizar | null>(() => {
    const reserva = this.compraReservada();
    if (!reserva) return null;
    const preciosLista: Record<number, number> = {};
    const cantidades: Record<number, number> = {};
    let reusarMonto = true;
    for (const d of reserva.detalles ?? []) {
      if (!d.tipoEntrada) {
        reusarMonto = false; // línea de artículo propia: el total de la reserva la incluye
        continue;
      }
      cantidades[d.tipoEntrada.id] = (cantidades[d.tipoEntrada.id] ?? 0) + d.cantidad;
      if (d.precioUnitario != null) preciosLista[d.tipoEntrada.id] = d.precioUnitario;
    }
    return { montoTotal: reserva.montoTotal, preciosLista, cantidades, reusarMonto };
  });

  /** Precio de lista unitario a mostrar y cotizar para un tipo: el de la reserva si hay una cargada. */
  precioLista(tipo: TipoEntrada): number {
    return this.reservaParaCotizar()?.preciosLista[tipo.id] ?? tipo.precio;
  }

  /** Precio de lista, sin promociones: sirve de fallback mientras llega la cotización. */
  private subtotalLista = computed(
    () =>
      this.lineas().reduce((acc, l) => acc + this.precioLista(l.tipo) * l.cantidad, 0) +
      this.articulosCarrito().reduce((acc, a) => acc + a.precioUnitario * a.cantidad, 0) +
      this.entradasFijas().reduce((acc, e) => acc + e.precioUnitario * e.cantidad, 0)
  );

  total = computed(() => this.cotizacion()?.subtotal ?? this.subtotalLista());
  ahorro = computed(() => this.cotizacion()?.ahorro ?? 0);
  /** Tope del descuento manual en $: el bruto real después del precio de grupo (si corresponde),
   * no el precio de lista — si no, pedir un descuento mayor al bruto ya bonificado hace que el
   * backend clampee el cobro a $0 en vez de al monto pedido (ver setDescuentoManualMonto y
   * setPrecioFijoDeseado). */
  montoMaximoDescuento = computed(() => Math.max(0, this.subtotalLista() - this.ahorro()));

  /** Cuánto de esta venta hay que cobrar en efectivo de verdad: el total si se paga todo en
   * efectivo, o sólo la parte que le toca a efectivo si hay pago mixto (efectivo puede ser
   * la principal o la secundaria). Null si esta venta no tiene nada de efectivo — no
   * corresponde mostrar "Paga con"/vuelto. */
  montoEfectivoACobrar = computed<number | null>(() => {
    if (this.pagoMixtoActivo()) {
      if (this.formaPago() === 'EFECTIVO_BOLETERIA') return this.montoFormaPagoPrincipal();
      if (this.formaPagoSecundaria() === 'EFECTIVO_BOLETERIA') return this.montoFormaPagoSecundaria() ?? 0;
      return null;
    }
    return this.formaPago() === 'EFECTIVO_BOLETERIA' ? this.total() : null;
  });

  /** El vuelto se calcula contra lo que hay que cobrar en efectivo (montoEfectivoACobrar), no
   * contra el total: con pago mixto sólo una parte del total se cobra en efectivo. */
  vuelto = computed(() => {
    const pagaCon = this.pagaCon();
    if (pagaCon === null) return null;
    const diferencia = pagaCon - (this.montoEfectivoACobrar() ?? 0);
    return diferencia >= 0 ? diferencia : null;
  });

  /** Cuánto falta para llegar a lo que hay que cobrar en efectivo. Null si "paga con" está vacío o ya alcanza. */
  falta = computed(() => {
    const pagaCon = this.pagaCon();
    if (pagaCon === null) return null;
    const diferencia = (this.montoEfectivoACobrar() ?? 0) - pagaCon;
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

  /** Carrito con total $0 (todo bonificado / descuento del 100%): no hay nada que cobrar, así
   * que no se pide forma de pago — la venta se registra como SIN_COBRO. */
  sinCobro = computed(() => this.hayItems() && this.total() === 0);

  /** Igual que en la compra online: no se puede entrar sólo con menores. */
  private tieneEntradas = computed(() => this.lineas().length > 0);
  private tieneObligatorio = computed(() => this.lineas().some((l) => l.tipo.obligatorio));

  /** Pagando en dólares hace falta la cotización y que "paga con" alcance el total: sin eso no hay un vuelto válido que calcular. */
  private faltaCompletarPagoDolares = computed(() => this.pagoEnDolares() && this.vueltoEnPesosPorDolares() === null);

  /** Con pago mixto activo hace falta elegir la segunda forma y un monto válido (entre 0 y el
   * total, sin llevarse todo — si no, no tiene sentido separarlo de una venta normal). */
  private faltaCompletarPagoMixto = computed(() => {
    if (!this.pagoMixtoActivo()) return false;
    const monto = this.montoFormaPagoSecundaria();
    return this.formaPagoSecundaria() === null || monto === null || monto <= 0 || monto >= this.total();
  });

  puedeCobrar = computed(
    () =>
      this.hayItems() &&
      (this.sinCobro() || this.formaPago() !== null) &&
      (!this.tieneEntradas() || this.tieneObligatorio()) &&
      !this.faltaCompletarPagoDolares() &&
      !this.faltaCompletarPagoMixto() &&
      !this.faltaElegirPoint() &&
      this.pedidoPoint() === null &&
      // El Point cobra el total que se ve: con una cotización en camino (cambió la forma de pago o
      // el descuento) ese total puede ser el anterior, y el backend rechazaría la venta.
      !(this.cobraConPoint() && this.cotizando()) &&
      // Un cobro aprobado sin venta que no es de este monto: antes de pasar otra tarjeta hay que
      // devolverlo o descartarlo, si no quedaría olvidado.
      !(this.cobraConPoint() && this.cobrosQueSobran().length > 0) &&
      // Las validaciones de factura sólo cuentan si de verdad se va a facturar: con la
      // facturación apagada o una venta de $0 el selector no se ve, y un email mal escrito que
      // quedó cargado no puede trabar el cobro sin que el cajero vea por qué.
      (!this.facturacionHabilitada() ||
        this.sinCobro() ||
        (!this.emailFacturaInvalido() && !(this.destinoFactura() === 'IMPRIMIR' && !this.imprimirFacturaDisponible()))) &&
      !this.cobrando()
  );

  motivoBloqueo = computed(() => {
    if (!this.hayItems()) return null;
    if (this.faltaElegirPoint()) {
      return 'Elegí el Point de esta tablet en el botón de dispositivos (o "Sin posnet" para anotar la tarjeta a mano).';
    }
    if (this.cobraConPoint() && this.cobrosQueSobran().length > 0) {
      return 'Antes de volver a cobrar con el Point, devolvé o resolvé el cobro de arriba.';
    }
    // Una venta sólo de artículos (sin entradas) no exige ningún pase obligatorio.
    if (this.tieneEntradas() && !this.tieneObligatorio()) {
      const obligatorios = this.tiposEntrada().filter((t) => t.obligatorio).map((t) => t.nombre).join(' o ');
      return `Falta un pase de tipo ${obligatorios || 'obligatorio'}: no se puede ingresar sólo con menores.`;
    }
    return null;
  });

  constructor() {
    this.facturaService.actualizarEstadoServicio();

    // Se recargó la página con un cobro esperando en el Point: se retoma esa espera (sin carrito:
    // si sale aprobado queda el aviso de "cobro sin venta" para usarlo o devolverlo).
    const pendiente = this.point.cobrosPendientes().find((c) => c.enCurso);
    if (pendiente) {
      const terminal = this.point.terminales().find((t) => t.id === pendiente.terminalId) ?? {
        id: pendiente.terminalId,
        nombre: 'Point',
        serie: '',
        modo: 'PDV',
        integrado: true,
        habilitado: true,
      };
      this.pedidoPoint.set({ monto: pendiente.monto, forma: pendiente.formaPago, terminal, reanudar: pendiente, paso: null });
    }

    // Si se corta la señal con "Imprimir" marcado, pasa a mail (vacío = sin factura): imprimir
    // no se puede, y así el botón Cobrar no queda trabado sin que se entienda por qué.
    effect(() => {
      const disponible = this.imprimirFacturaDisponible();
      if (!disponible && this.destinoFactura() === 'IMPRIMIR') {
        this.destinoFactura.set('MAIL');
      } else if (disponible && !untracked(() => this.destinoElegidoAMano())) {
        // Y al revés: si se pasó a mail sólo porque la ticketera no estaba (y el cajero no tocó
        // nada), cuando vuelve se recupera lo que corresponde a la forma de pago.
        const formaPago = untracked(() => this.formaPago());
        if (formaPago !== null) untracked(() => this.aplicarDestinoFacturaPorDefecto(formaPago));
      }
    });

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
      // Sin forma de pago elegida no se cotiza: el TOTAL cae al precio de lista (subtotalLista),
      // y recién al elegir Efectivo/Tarjeta/QR se pide la cotización real (promo sólo en efectivo).
      if (formaPago === null || (entradas.length === 0 && articulos.length === 0)) {
        this.cotizacion.set(null);
        untracked(() => this.cotizando.set(false));
        return;
      }
      untracked(() => this.cotizando.set(true));
      this.pedidoCotizacion.next({ formaPago, entradas, articulos, descuento, reservaId: this.compraReservada()?.id });
    });

    this.pedidoCotizacion
      .pipe(
        switchMap(({ formaPago, entradas, articulos, descuento, reservaId }) => {
          // Sin señal ni siquiera se intenta: el cálculo local da el mismo número y evita
          // que el boletero vea el total "colgado" esperando un timeout en cada tecla.
          const local = () => this.cotizarSinConexion(formaPago, entradas, articulos, descuento);
          if (!this.conectividad.enLinea()) return of(local());
          return this.boleteriaService
            .cotizar(formaPago, entradas, descuento, articulos, reservaId)
            .pipe(catchError(() => of(local())));
        }),
        takeUntilDestroyed()
      )
      .subscribe((cotizacion) => {
        this.cotizacion.set(cotizacion);
        this.cotizando.set(false);
      });
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
      this.entradasFijas(),
      this.reservaParaCotizar()
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
    if (this.modoDescuento() === 'precioFijo' && this.precioFijoDeseado() !== null) {
      return { descuentoManualMonto: this.montoMaximoDescuento() - this.precioFijoDeseado()! };
    }
    return {};
  }

  cambiarModoDescuento(modo: 'ninguno' | 'promo' | 'manual' | 'precioFijo'): void {
    this.modoDescuento.set(modo);
    this.promocionSeleccionadaId.set(null);
    this.descuentoManualPorcentaje.set(null);
    this.descuentoManualMonto.set(null);
    this.precioFijoDeseado.set(null);
  }

  /** No deja tipear un precio final mayor al bruto real (de lista, o ya bonificado por precio de
   * grupo si corresponde) ni negativo. */
  setPrecioFijoDeseado(valor: number | null): void {
    const acotado = valor === null ? null : Math.max(0, Math.min(valor, this.montoMaximoDescuento()));
    this.precioFijoDeseado.set(acotado);
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
    this.precioFijoDeseado.set(null);
  }

  private limpiarPagoDolares(): void {
    this.pagoEnDolares.set(false);
    this.cotizacionDolar.set(null);
    this.pagaConDolares.set(null);
  }

  private limpiarPagoMixto(): void {
    this.pagoMixtoActivo.set(false);
    this.formaPagoSecundaria.set(null);
    this.montoFormaPagoSecundaria.set(null);
  }

  /** El checkbox de dólares sólo tiene sentido pagando en efectivo: cambiar de forma de pago lo apaga. */
  setFormaPago(formaPago: FormaPagoPos): void {
    const anterior = this.formaPago();
    this.formaPago.set(formaPago);
    if (formaPago !== anterior) {
      this.destinoElegidoAMano.set(false);
      this.aplicarDestinoFacturaPorDefecto(formaPago);
    }
    if (formaPago !== 'EFECTIVO_BOLETERIA') {
      this.limpiarPagoDolares();
    }
    // La principal y la secundaria tienen que ser distintas (salvo tarjeta + tarjeta). Si tocan como principal la que
    // ya estaba de secundaria, lo más probable es que se confundieron de botón — no que
    // quisieron cancelar el pago mixto — así que se intercambian (con sus montos) en vez de
    // perder la carga.
    if (this.pagoMixtoActivo() && this.formaPagoSecundaria() === formaPago && formaPago !== 'TARJETA') {
      const montoSecundarioViejo = this.montoFormaPagoSecundaria() ?? 0;
      this.formaPagoSecundaria.set(anterior);
      this.montoFormaPagoSecundaria.set(Math.max(0, this.total() - montoSecundarioViejo));
    }
  }

  /** El cajero eligió Imprimir/Mail a mano en esta venta: ya no se lo cambia solo. */
  private destinoElegidoAMano = signal(false);

  /** Lo que diga Configuración > Cobros para esa forma de pago; sin ticketera, mail (vacío = sin
   * factura). El email que ya se haya tipeado no se borra: cambiar de forma de pago no debería
   * hacer perder lo cargado. */
  private aplicarDestinoFacturaPorDefecto(formaPago: FormaPagoPos): void {
    const predeterminada = this.facturaService.predeterminada();
    const destino =
      formaPago === 'EFECTIVO_BOLETERIA' ? predeterminada.efectivo : formaPago === 'TARJETA' ? predeterminada.tarjeta : predeterminada.qr;
    this.destinoFactura.set(destino === 'IMPRIMIR' && this.imprimirFacturaDisponible() ? 'IMPRIMIR' : 'MAIL');
  }

  setDestinoFactura(destino: DestinoFactura): void {
    if (destino === 'IMPRIMIR' && !this.imprimirFacturaDisponible()) return;
    this.destinoFactura.set(destino);
    this.destinoElegidoAMano.set(true);
  }

  /** Lo que viaja en el cobro. Null = no se factura. */
  private facturacionPayload(): FacturacionPos | null {
    if (!this.facturacionHabilitada() || this.sinCobro()) return null;
    if (this.destinoFactura() === 'IMPRIMIR') return { destino: 'IMPRIMIR', email: null, impresora: this.impresoras.efectiva()?.nombre ?? null };
    const email = this.emailFactura().trim();
    return email === '' ? null : { destino: 'MAIL', email };
  }

  /** Tildar dólares oculta el "paga con" en pesos (son mutuamente excluyentes dentro de Efectivo)
   * y apaga el pago mixto (dólares ya asume el pago 100% en efectivo). */
  setPagoEnDolares(valor: boolean): void {
    this.pagoEnDolares.set(valor);
    this.cotizacionDolar.set(null);
    this.pagaConDolares.set(null);
    if (valor) {
      this.pagaCon.set(null);
      this.limpiarPagoMixto();
    }
  }

  /** Pago mixto y dólares son excluyentes: activar uno apaga el otro. */
  setPagoMixtoActivo(valor: boolean): void {
    this.pagoMixtoActivo.set(valor);
    if (valor) {
      this.limpiarPagoDolares();
    } else {
      this.formaPagoSecundaria.set(null);
      this.montoFormaPagoSecundaria.set(null);
    }
  }

  /** No deja tipear un monto mayor o igual al total (la principal se llevaría $0 o menos). */
  setMontoFormaPagoSecundaria(valor: number | null): void {
    const tope = Math.max(0, this.total() - 1);
    this.montoFormaPagoSecundaria.set(valor === null ? null : Math.max(0, Math.min(valor, tope)));
  }

  /** Simétrico a setMontoFormaPagoSecundaria: el pago mixto se puede tipear desde cualquiera
   * de las dos formas, la otra se ajusta sola para seguir sumando el total. */
  setMontoFormaPagoPrincipal(valor: number | null): void {
    if (valor === null) {
      this.montoFormaPagoSecundaria.set(null);
      return;
    }
    const tope = Math.max(0, this.total() - 1);
    const principalAcotado = Math.max(0, Math.min(valor, tope));
    this.montoFormaPagoSecundaria.set(this.total() - principalAcotado);
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
    this.formaPago.set(null);
    this.pagaCon.set(null);
    this.limpiarPagoDolares();
    this.limpiarPagoMixto();
    this.destinoFactura.set('MAIL');
    this.emailFactura.set('');
    this.destinoElegidoAMano.set(false);
    this.error.set(null);
    this.limpiarDescuento();
    this.limpiar.emit();
  }

  async cobrar(): Promise<void> {
    if (!this.puedeCobrar()) return;
    if (!this.cobraConPoint()) {
      await this.registrar([]);
      return;
    }
    // Primero se cobra en el Point cada parte que falta (tarjeta y/o QR, de a una); la venta se
    // registra recién con todo aprobado. Lo ya aprobado de un intento anterior no se vuelve a cobrar.
    this.error.set(null);
    const partes = this.partesPoint();
    const asignados = this.asignacionCobros();
    const terminal = this.point.efectiva()!;
    const ids: number[] = [];
    for (let i = 0; i < partes.length; i++) {
      const ya = asignados[i];
      if (ya) {
        ids.push(ya.id);
        continue;
      }
      const paso = partes.length > 1 ? `Parte ${i + 1} de ${partes.length}` : null;
      const r = await this.cobrarParteEnPoint({ monto: partes[i].monto, forma: partes[i].forma, terminal, reanudar: null, paso });
      if (r === null) return; // no salió o se canceló: el carrito queda como estaba
      ids.push(r.id);
    }
    await this.registrar(ids);
  }

  private cobrarParteEnPoint(pedido: PedidoPoint): Promise<CobroPoint | null> {
    return new Promise((resolve) => {
      this.resolverPoint = resolve;
      this.pedidoPoint.set(pedido);
    });
  }

  private cerrarPoint(r: CobroPoint | null): void {
    const pedido = this.pedidoPoint();
    this.pedidoPoint.set(null);
    const resolver = this.resolverPoint;
    this.resolverPoint = null;
    // Retomado después de recargar: no hay cobro en curso que lo espere; si salió aprobado queda
    // el aviso de cobro sin venta.
    if (pedido?.reanudar || !resolver) return;
    resolver(r);
  }

  onPointAprobado(cobro: CobroPoint): void {
    this.cerrarPoint(cobro);
  }

  /** El Point no confirmó pero el boletero vio que el pago salió: la venta se registra con ese
   * cobro igual (el backend lo acepta en curso), así cuando MP lo apruebe ya es de esta venta.
   *
   * Queda recordado como cobrado hasta que la venta se registre: si se rechaza, o si se retomó
   * después de recargar (no hay carrito esperándolo), el aviso de "cobro sin venta" lo ofrece para
   * la próxima venta en vez de que se pierda y se le vuelva a cobrar al cliente. */
  onPointAMano(cobro: CobroPoint): void {
    this.point.recordarCobro({ ...cobro, estado: 'APROBADO', enCurso: false, motivo: null });
    this.cerrarPoint(cobro);
  }

  onPointCerrado(): void {
    this.cerrarPoint(null);
  }

  /** Devuelve al cliente un cobro aprobado que no terminó en venta. */
  devolverCobroSinVenta(c: CobroPoint): void {
    this.devolviendoCobroId.set(c.id);
    this.error.set(null);
    this.point.devolverCobro(c.id).subscribe({
      next: () => {
        this.devolviendoCobroId.set(null);
        this.point.olvidarCobros([c.id]);
      },
      error: (err) => {
        this.devolviendoCobroId.set(null);
        this.error.set(typeof err?.error === 'string' && err.error ? err.error : 'No se pudo devolver el cobro.');
      },
    });
  }

  /** El boletero ya lo resolvió por su cuenta (cargó la venta a mano, lo devolvió en el Point...). */
  descartarCobroSinVenta(c: CobroPoint): void {
    this.point.olvidarCobros([c.id]);
  }

  etiquetaFormaPoint(forma: FormaPagoPoint): string {
    return forma === 'MERCADO_PAGO_QR' ? 'QR' : 'tarjeta';
  }

  private async registrar(cobrosPointIds: number[]): Promise<void> {
    const formaPago: FormaPagoVentaPos | null = this.sinCobro() ? 'SIN_COBRO' : this.formaPago();
    if (formaPago === null) return;

    this.cobrando.set(true);
    this.error.set(null);

    const entradas = [
      ...this.lineas().map((l) => ({ tipoEntradaId: l.tipo.id, cantidad: l.cantidad })),
      ...this.entradasFijasPayload(),
    ];
    const articulos = this.articulosCarritoPayload();
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

    const pagoMixtoPayload = this.pagoMixtoActivo()
      ? { formaPagoSecundaria: this.formaPagoSecundaria(), montoFormaPagoSecundaria: this.montoFormaPagoSecundaria() }
      : {};

    const reservaId = this.compraReservada()?.id;
    const facturacion = this.facturacionPayload();

    const payload: VentaPosRequest = {
      formaPago,
      entradas,
      articulos,
      cajaId: this.cajaId(),
      ...(reservaId ? { compraReservadaId: reservaId } : {}),
      ...this.descuentoPayload(),
      ...dolaresPayload,
      ...pagoMixtoPayload,
      ...(facturacion ? { facturacion } : {}),
      ...(cobrosPointIds.length ? { cobrosPointIds } : {}),
    };

    // Toda venta pasa por la cola: si hay señal se confirma al instante y sigue todo igual que
    // antes, y si no, queda guardada y se sincroniza sola (ver OperacionesPendientesService).
    const resultado = await this.pendientes.ejecutar<Reserva>({ tipo: 'VENTA', payload });
    this.cobrando.set(false);

    const pagoMixtoConfirmado = this.pagoMixtoActivo()
      ? { formaPagoSecundaria: this.formaPagoSecundaria(), montoFormaPagoSecundaria: this.montoFormaPagoSecundaria() }
      : { formaPagoSecundaria: null, montoFormaPagoSecundaria: null };

    // Registrada o guardada en la cola: el cobro del Point ya es de esta venta (si la cola la
    // sincroniza después, viaja con su id). Rechazada, el cobro queda para reintentar o devolver.
    if (cobrosPointIds.length && (resultado.confirmada || !resultado.rechazada)) {
      this.point.olvidarCobros(cobrosPointIds);
    }

    if (resultado.confirmada) {
      this.ventaRegistrada.emit({ venta: resultado.resultado, formaPago, vuelto, items, pagoEnDolares, pendiente: false, ...pagoMixtoConfirmado, facturacion });
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
      fechaVisita: reserva?.fechaVisita ?? aFechaISO(new Date()),
      montoTotal: total,
      descuentoAplicado: 0,
      estado: reserva ? 'USADO' : 'VENDIDO_EN_PUERTA',
      formaPago,
      detalles: null,
      fechaValidacion: aFechaHoraISO(),
      usuarioValidador: null,
      receptorNombre: null,
      receptorEmail: null,
      receptorDni: null,
      receptorTelefono: null,
    };
    this.ventaRegistrada.emit({ venta: ventaLocal, formaPago, vuelto, items, pagoEnDolares, pendiente: true, ...pagoMixtoConfirmado, facturacion });
  }
}
