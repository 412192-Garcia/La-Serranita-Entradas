import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { AusenteValidacion, BoleteriaService, CampoOrdenCompras, EstadoCompra, Pagina, Reserva, TipoListadoCompra, VoucherReserva } from '../services/boleteria.service';
import { ImpresorasService } from '../services/impresoras.service';
import { ToastService } from '../shared/toast/toast.service';
import { FormaPagoType } from '../models/compra';
import { crearOrdenable } from '../shared/ordenable';
import { esEscaneoDocumento, extraerDniDeEscaneo } from '../shared/escaner-dni.util';
import { aFechaISO } from '../shared/fecha.util';
import { ReservaVista, aVista, esRegalo, etiquetaGrupoFecha } from '../shared/reserva-vista.util';

function hoyComoFechaInput(): string {
  return aFechaISO(new Date());
}

/** Un "contenedor" de la vista agrupada por día (sólo se arma cuando `todasLasFechas()` está activo). */
export interface GrupoDia {
  fecha: string;
  etiqueta: string;
  esHoy: boolean;
  vistas: ReservaVista[];
  totalPases: number;
}

export interface OpcionFiltro<T> {
  valor: T;
  etiqueta: string;
}

const FORMAS_PAGO: OpcionFiltro<FormaPagoType>[] = [
  { valor: 'MERCADO_PAGO', etiqueta: 'Mercado Pago' },
  { valor: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
  { valor: 'RESERVA_ADMIN', etiqueta: 'Generada (admin)' },
  { valor: 'SIN_COBRO', etiqueta: 'Sin cobro' },
];

/** La venta de puerta nunca es online ni generada por un admin. */
const FORMAS_PAGO_PUERTA: ReadonlySet<FormaPagoType> = new Set(['EFECTIVO_BOLETERIA', 'TARJETA', 'MERCADO_PAGO_QR', 'SIN_COBRO']);

/** Siempre a la vista. */
export const ESTADOS_ANTICIPADA_PRINCIPALES: OpcionFiltro<EstadoCompra>[] = [
  { valor: 'RESERVADO_EFECTIVO', etiqueta: 'A cobrar en caja' },
  { valor: 'APROBADO', etiqueta: 'Pagada online' },
  { valor: 'USADO', etiqueta: 'Ya utilizada' },
];

/** Los que casi no se usan: van en "Más filtros". */
export const ESTADOS_ANTICIPADA_SECUNDARIOS: OpcionFiltro<EstadoCompra>[] = [
  { valor: 'PENDIENTE_PAGO', etiqueta: 'Pago pendiente' },
  { valor: 'CANCELADO', etiqueta: 'Cancelada' },
  { valor: 'REEMBOLSADA', etiqueta: 'Reembolsada' },
];

const VALORES_ESTADOS_SECUNDARIOS = new Set(ESTADOS_ANTICIPADA_SECUNDARIOS.map((e) => e.valor));

/** Lo que falta entrar. Cada pantalla puede cambiarlo con `usarEstadosPorDefecto`. */
const ESTADOS_POR_DEFECTO: readonly EstadoCompra[] = ['APROBADO', 'RESERVADO_EFECTIVO'];

/** Devuelve un Set nuevo con el valor agregado o sacado: los signals necesitan otra referencia. */
function alternar<T>(actuales: ReadonlySet<T>, valor: T): ReadonlySet<T> {
  const copia = new Set(actuales);
  if (!copia.delete(valor)) copia.add(valor);
  return copia;
}


const TAMANIO_PAGINA = 50;
/** Techo duro del backend para /buscar (ver CompraController): si un rango lo pasa, se pide por partes. */
const TAMANIO_MAXIMO_VENTANA_AGRUPADA = 200;
/** Techo duro del backend para /fechas-visita (ver CompraController). */
const TAMANIO_MAXIMO_DIAS_AGRUPADOS = 200;

/** Pide la primera página y, si hay más, el resto en paralelo; devuelve todo junto en una sola página. */
function traerTodasLasPaginas<T>(pedir: (page: number) => Observable<Pagina<T>>): Observable<Pagina<T>> {
  return pedir(0).pipe(
    switchMap((primera) => {
      if (primera.totalPages <= 1) return of(primera);
      const resto = Array.from({ length: primera.totalPages - 1 }, (_, i) => pedir(i + 1));
      return forkJoin(resto).pipe(
        map((otras) => ({ ...primera, content: [primera, ...otras].flatMap((p) => p.content), totalPages: 1 }))
      );
    })
  );
}

/** Cómo se pagina la vista "todas las fechas": una página por semana o por mes. */
export type AgrupacionFechas = 'semana' | 'mes';

const MESES_LARGOS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** Etiqueta compacta de un rango de fechas para los botones del paginador por semana:
 * "25–31 ago" (mismo mes), "28 ago – 3 sep" (cruza mes). */
function rangoFechasCorto(desde: string, hasta: string): string {
  const d = new Date(desde + 'T00:00:00');
  const h = new Date(hasta + 'T00:00:00');
  if (desde === hasta) return `${d.getDate()} ${MESES_CORTOS[d.getMonth()]}`;
  if (d.getMonth() === h.getMonth()) return `${d.getDate()}–${h.getDate()} ${MESES_CORTOS[h.getMonth()]}`;
  return `${d.getDate()} ${MESES_CORTOS[d.getMonth()]} – ${h.getDate()} ${MESES_CORTOS[h.getMonth()]}`;
}

function sumarDias(fecha: string, dias: number): string {
  const d = new Date(fecha + 'T00:00:00');
  d.setDate(d.getDate() + dias);
  return aFechaISO(d);
}

/** El lunes de la semana calendario de `fecha` (yyyy-MM-dd). Las páginas de la vista agrupada
 * son semanas fijas lunes–domingo, no ventanas móviles ancladas en "hoy": así el rango de una
 * página no cambia porque entró o salió una reserva. */
function lunesDeLaSemana(fecha: string): string {
  const d = new Date(fecha + 'T00:00:00');
  const diaSemana = (d.getDay() + 6) % 7; // 0 = lunes … 6 = domingo
  return sumarDias(fecha, -diaSemana);
}

function primeroDelMes(fecha: string): string {
  return fecha.slice(0, 8) + '01';
}

function ultimoDelMes(fecha: string): string {
  const d = new Date(fecha + 'T00:00:00');
  return aFechaISO(new Date(d.getFullYear(), d.getMonth() + 1, 0));
}

/** "Noviembre 2026", o "Nov 2026" si `corto`. */
function nombreMes(fecha: string, corto = false): string {
  const d = new Date(fecha + 'T00:00:00');
  const mes = (corto ? MESES_CORTOS : MESES_LARGOS)[d.getMonth()];
  return `${mes[0].toUpperCase()}${mes.slice(1)} ${d.getFullYear()}`;
}

/**
 * Estado y lógica de la búsqueda de reservas: filtros, vista de un día o agrupada por semana/mes,
 * regalos, paginación y las acciones de validar/deshacer sobre el listado. Lo comparten Control
 * de Accesos y el panel de anticipadas del POS.
 *
 * NO es un singleton: lo provee (`providers`) cada pantalla que lo usa, así cada una tiene su
 * propia instancia con su propio texto, fecha y página, y los componentes de `reservas/` (buscador,
 * listado, fila) lo toman de la pantalla donde están puestos.
 */
@Injectable()
export class ReservasBusqueda implements OnDestroy {
  private boleteriaService = inject(BoleteriaService);
  private impresoras = inject(ImpresorasService);
  private toast = inject(ToastService);

  texto = signal('');
  fecha = signal(hoyComoFechaInput());
  /** false = sólo el día elegido; true = todas las fechas (una entrada de otro día igual se deja usar). */
  todasLasFechas = signal(false);
  buscando = signal(false);
  errorBusqueda = signal<string | null>(null);
  errorAccion = signal<string | null>(null);

  /** BOLETERIA = venta de puerta; ANTICIPADA = todo lo que se reservó de antemano. */
  tipoListado = signal<TipoListadoCompra>('ANTICIPADA');
  private estadosPorDefecto: readonly EstadoCompra[] = ESTADOS_POR_DEFECTO;
  estadosActivos = signal<ReadonlySet<EstadoCompra>>(new Set(ESTADOS_POR_DEFECTO));
  formaPago = signal<FormaPagoType | ''>('');
  /** Parte del código de descuento usado en la compra; vacío = sin filtro. */
  cupon = signal('');

  formasPagoDisponibles = computed(() =>
    this.tipoListado() === 'BOLETERIA' ? FORMAS_PAGO.filter((f) => FORMAS_PAGO_PUERTA.has(f.valor)) : FORMAS_PAGO
  );
  agrupacion = signal<AgrupacionFechas>('semana');
  pagina = signal(0);
  private orden = crearOrdenable<CampoOrdenCompras>('fechaVisita');
  ordenarPor = this.orden.ordenarPor;
  direccionOrden = this.orden.direccionOrden;
  estadoOrden = this.orden.estadoOrden;

  /** Lo último que devolvió el backend; null = todavía no se buscó nada. */
  private resultado = signal<Pagina<Reserva> | null>(null);

  /** Ya vienen filtradas y paginadas por el backend: acá sólo se preparan para el template. */
  visibles = computed<ReservaVista[] | null>(() => {
    const res = this.resultado();
    if (res === null) return null;
    const ocultos = this.idsOcultosPorValidacion();
    const hoy = hoyComoFechaInput();
    return res.content.filter((r) => !ocultos.has(r.id)).map((r) => aVista(r, hoy));
  });

  /** Encabezado de "Ver esa fecha": qué día se está mirando, mismo criterio de etiqueta que usan los contenedores de "Ver todas las fechas". */
  etiquetaFechaVista = computed(() => etiquetaGrupoFecha(this.fecha(), hoyComoFechaInput()));

  // ---------- Regalos (fechaVisita null): bloque colapsable aparte, visible en ambos modos ----------
  // Al no tener fecha, no encajan en la paginación por día/fila de arriba (antes quedaban
  // enterrados en la última página de "Ver todas las fechas", y no aparecían en "Ver esa fecha"
  // directamente). Se buscan aparte con `sinFecha: true` y se muestran siempre, colapsados por
  // defecto, sin importar qué modo esté activo.
  private regalosResultado = signal<Pagina<Reserva> | null>(null);
  regalosExpandido = signal(false);

  regalosVisibles = computed<ReservaVista[] | null>(() => {
    const res = this.regalosResultado();
    if (res === null) return null;
    const ocultos = this.idsOcultosPorValidacion();
    const hoy = hoyComoFechaInput();
    return res.content.filter((r) => !ocultos.has(r.id)).map((r) => aVista(r, hoy));
  });

  totalRegalos = computed(() => this.regalosVisibles()?.length ?? 0);

  /** El bloque junta regalos y reservas sin fecha generadas desde Acciones (sin receptor): el
   * botón sólo dice "regalos" si todos lo son. */
  soloRegalos = computed(() => (this.regalosVisibles() ?? []).every((v) => esRegalo(v.reserva)));

  toggleRegalos(): void {
    this.regalosExpandido.update((v) => !v);
  }

  totalElementos = computed(() => this.resultado()?.totalElements ?? 0);
  totalPaginas = computed(() => this.resultado()?.totalPages ?? 0);
  /** Suma de pases sólo de esta página: con paginación real no hay forma barata de sumar todas. */
  totalPasesPagina = computed(() => (this.visibles() ?? []).reduce((acc, v) => acc + v.totalPases, 0));

  // ---------- Vista agrupada por día ("Ver todas las fechas") ----------
  // Paginada por SEMANA (lunes–domingo) o MES calendario (`agrupacion`), no por fila ni por ventana móvil:
  // `ejecutarBusquedaAgrupada` trae de una todos los días con actividad (`diasConActividad`)
  // y arma una página por cada semana que tenga al menos un día con compras (`paginasDias`).
  // El rango de una página es fijo (no se corre porque entró o salió una reserva); la semana
  // que contiene "hoy" (`paginaHoy`) es el punto de partida y queda marcada, y hay un botón
  // "Esta semana" para volver desde cualquier lado.
  private diasConActividad = signal<string[]>([]);

  /** Una página por cada semana (lunes–domingo) o mes calendario con al menos un día con compras. */
  paginasDias = computed<{ indice: number; desde: string; hasta: string }[]>(() => {
    const porMes = this.agrupacion() === 'mes';
    const inicios = [...new Set(this.diasConActividad().map(porMes ? primeroDelMes : lunesDeLaSemana))].sort();
    return inicios.map((desde, indice) => ({
      indice,
      desde,
      hasta: porMes ? ultimoDelMes(desde) : sumarDias(desde, 6),
    }));
  });

  /** Texto del botón de la página que contiene hoy. */
  etiquetaPaginaHoy = computed(() => (this.agrupacion() === 'mes' ? 'Este mes' : 'Esta semana'));

  totalPaginasAgrupado = computed(() => this.paginasDias().length);

  /** Índice de la semana que contiene "hoy"; si esa semana no tiene actividad, la primera que
   * termina en hoy o después (o la última si ya pasaron todas). 0 si todavía no cargó nada. */
  paginaHoy = computed<number>(() => {
    const paginas = this.paginasDias();
    if (paginas.length === 0) return 0;
    const hoy = hoyComoFechaInput();
    const idx = paginas.findIndex((p) => p.hasta >= hoy);
    return idx === -1 ? paginas.length - 1 : idx;
  });

  /** Siempre 3 ranuras (semana anterior · actual · siguiente), con `null` en los bordes de la
   * lista: así la barra tiene ancho fijo y no se sacude al navegar (los botones también son de
   * ancho fijo, ver .btn-pagina-dia). */
  paginasVisibles = computed<({ indice: number; desde: string; hasta: string } | null)[]>(() => {
    const todas = this.paginasDias();
    const actual = this.pagina();
    return [-1, 0, 1].map((offset) => todas[actual + offset] ?? null);
  });

  etiquetaRangoPagina(pagina: { desde: string; hasta: string }): string {
    return this.agrupacion() === 'mes' ? nombreMes(pagina.desde) : rangoFechasCorto(pagina.desde, pagina.hasta);
  }

  /** Texto del botón de una página: "Esta semana"/"Este mes" sólo si de verdad contiene hoy. */
  etiquetaBotonPagina(pagina: { desde: string; hasta: string }): string {
    const hoy = hoyComoFechaInput();
    if (pagina.desde <= hoy && hoy <= pagina.hasta) return this.etiquetaPaginaHoy();
    return this.agrupacion() === 'mes' ? nombreMes(pagina.desde, true) : this.etiquetaRangoPagina(pagina);
  }

  /** Cambia entre páginas por semana o por mes y vuelve a la que contiene hoy. */
  cambiarAgrupacion(modo: AgrupacionFechas): void {
    if (modo === this.agrupacion() || this.buscando()) return;
    this.agrupacion.set(modo);
    if (!this.todasLasFechas()) return;
    this.pagina.set(this.paginaHoy());
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.limpiarOcultamientosPorValidacion();
    this.ejecutarBusquedaAgrupada(false);
  }

  irAPagina(indice: number): void {
    if (this.buscando() || indice === this.pagina()) return;
    this.pagina.set(indice);
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.limpiarOcultamientosPorValidacion();
    this.ejecutarBusquedaAgrupada(false);
  }

  /** Los regalos (fechaVisita null) no entran acá: /fechas-visita ya los excluye, así que
   * `visibles()` en este modo son siempre reservas con fecha real. */
  gruposDia = computed<GrupoDia[]>(() => {
    const hoy = hoyComoFechaInput();
    const vistas = this.visibles() ?? [];

    const porFecha = new Map<string, ReservaVista[]>();
    for (const v of vistas) {
      const f = v.reserva.fechaVisita;
      if (!f) continue;
      const lista = porFecha.get(f);
      if (lista) lista.push(v);
      else porFecha.set(f, [v]);
    }

    return [...porFecha.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([fecha, vs]) => ({
        fecha,
        etiqueta: etiquetaGrupoFecha(fecha, hoy),
        esHoy: fecha === hoy,
        vistas: vs,
        totalPases: vs.reduce((acc, v) => acc + v.totalPases, 0),
      }));
  });

  // ---------- Filtros ----------

  estadoActivo(estado: EstadoCompra): boolean {
    return this.estadosActivos().has(estado);
  }

  /** Llamarlo antes de la primera búsqueda. */
  usarEstadosPorDefecto(estados: readonly EstadoCompra[]): void {
    this.estadosPorDefecto = estados;
    this.estadosActivos.set(new Set(estados));
  }

  toggleTipoListado(tipo: TipoListadoCompra): void {
    if (this.tipoListado() === tipo) return;
    this.aplicarFiltro(() => {
      this.tipoListado.set(tipo);
      // En la venta de puerta no hay cupones ni pagos online.
      if (tipo === 'BOLETERIA') {
        this.cupon.set('');
        if (this.formaPago() && !FORMAS_PAGO_PUERTA.has(this.formaPago() as FormaPagoType)) this.formaPago.set('');
      }
    });
  }

  /** Devuelve false si era el último estado elegido: sin ninguno no habría nada que mostrar. */
  toggleEstado(estado: EstadoCompra): boolean {
    const actuales = this.estadosActivos();
    if (actuales.size === 1 && actuales.has(estado)) return false;
    this.aplicarFiltro(() => this.estadosActivos.set(alternar(actuales, estado)));
    return true;
  }

  onFormaPagoChange(valor: string): void {
    this.aplicarFiltro(() => this.formaPago.set(valor as FormaPagoType | ''));
  }

  /** Con un cupón se pasa a "todas las fechas": lo normal es querer ver todas las reservas que lo usaron. */
  onCuponChange(valor: string): void {
    const limpio = valor.trim();
    if (limpio === this.cupon()) return;
    this.aplicarFiltro(() => {
      this.cupon.set(limpio);
      if (limpio) this.todasLasFechas.set(true);
    });
  }

  private aplicarFiltro(cambio: () => void): void {
    cambio();
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  /** Los filtros que difieren del valor por defecto, y si están en "Más filtros". En Boletería
   * no cuentan los estados ni el cupón, que ahí no se usan. */
  private filtrosCambiados = computed<{ enMasFiltros: boolean }[]>(() => {
    const anticipada = this.tipoListado() === 'ANTICIPADA';
    const cambiados: { enMasFiltros: boolean }[] = [];
    if (anticipada) {
      const activos = this.estadosActivos();
      const porDefecto = new Set(this.estadosPorDefecto);
      for (const e of new Set([...activos, ...porDefecto])) {
        if (activos.has(e) !== porDefecto.has(e)) cambiados.push({ enMasFiltros: VALORES_ESTADOS_SECUNDARIOS.has(e) });
      }
      if (this.cupon()) cambiados.push({ enMasFiltros: true });
    } else {
      cambiados.push({ enMasFiltros: true });
    }
    if (this.formaPago()) cambiados.push({ enMasFiltros: true });
    return cambiados;
  });

  /** El contador del botón "Filtros" en celular. */
  cantidadFiltrosActivos = computed(() => this.filtrosCambiados().length);
  filtrosEnDefecto = computed(() => this.filtrosCambiados().length === 0);
  /** El puntito de aviso de "Más filtros". */
  hayFiltrosEnMasFiltros = computed(() => this.filtrosCambiados().some((f) => f.enMasFiltros));

  restablecerFiltros(): void {
    this.aplicarFiltro(() => {
      this.tipoListado.set('ANTICIPADA');
      this.estadosActivos.set(new Set(this.estadosPorDefecto));
      this.formaPago.set('');
      this.cupon.set('');
    });
  }

  /** Clic en un encabezado ordenable: si ya se ordena por esa columna, invierte la dirección; si no, la adopta ascendente. */
  ordenarColumna(campo: CampoOrdenCompras): void {
    this.orden.ordenarColumna(campo);
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  paginaAnterior(): void {
    if (this.pagina() === 0) return;
    this.pagina.update((p) => p - 1);
    this.ejecutarBusqueda(false);
  }

  paginaSiguiente(): void {
    const total = this.todasLasFechas() ? this.totalPaginasAgrupado() : this.totalPaginas();
    if (this.pagina() + 1 >= total) return;
    this.pagina.update((p) => p + 1);
    this.ejecutarBusqueda(false);
  }

  // ---------- Búsqueda ----------

  /** Un único campo sirve para DNI, código de reserva, nombre o email: el backend prueba los cuatro. */
  buscar(): void {
    const crudo = this.texto().trim();
    if (!crudo || this.buscando()) return;

    // Si lo que llegó es un escaneo PDF417 (lector de DNI), extraemos el DNI puro antes de
    // buscar; para texto libre esto no aplica (se perderían los caracteres no numéricos).
    const query = esEscaneoDocumento(crudo) ? extraerDniDeEscaneo(crudo) : crudo;
    if (query !== crudo) this.texto.set(query);

    this.todasLasFechas.set(true);
    this.pagina.set(0);
    this.ejecutarBusqueda(true, true);
  }

  /** Trae las reservas del día de visita elegido en el selector de fecha. */
  verEsaFecha(): void {
    if (!this.fecha() || this.buscando()) return;
    this.texto.set('');
    this.todasLasFechas.set(false);
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  /**
   * Sin filtrar por fecha: el parque es laxo (una entrada comprada para otro día
   * igual se deja usar), así que esta es la forma de encontrar una reserva cuando
   * no coincide con el día que se está mirando.
   */
  verTodasLasFechas(): void {
    if (this.buscando()) return;
    this.texto.set('');
    this.todasLasFechas.set(true);
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  limpiar(): void {
    this.texto.set('');
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
  }

  /** Filtros comunes a cualquier búsqueda, sin fecha/orden/paginación. Al buscar a alguien por
   * texto no se filtra por estado: si ya entró o la cancelaron, igual tiene que aparecer. */
  private construirFiltroBase() {
    const tipo = this.tipoListado();
    const texto = this.texto().trim() || undefined;
    const anticipada = tipo === 'ANTICIPADA';
    return {
      texto,
      tipo,
      estados: anticipada && !texto ? Array.from(this.estadosActivos()) : undefined,
      formaPago: this.formaPago() || undefined,
      cupon: anticipada ? this.cupon() || undefined : undefined,
    };
  }

  /** `autoExpandirRegalos`: sólo lo pide `buscar()` (búsqueda explícita por texto) — si lo que
   * escribiste matchea sólo un regalo, la lista principal queda vacía y el match real está
   * colapsado arriba; sin esto era fácil pensar "no tiene entrada" cuando sí la tiene. El resto
   * de los disparadores (cambiar de página, tocar un chip de Estado, etc.) no fuerzan la
   * expansión — respetan si el boletero ya lo cerró a mano. */
  ejecutarBusqueda(reubicar = this.todasLasFechas(), autoExpandirRegalos = false): void {
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
    this.limpiarOcultamientosPorValidacion();
    this.buscarRegalos(autoExpandirRegalos);

    if (this.todasLasFechas()) {
      this.ejecutarBusquedaAgrupada(reubicar);
    } else {
      this.ejecutarBusquedaSimple();
    }
  }

  /** El bloque colapsable de regalos: mismos filtros (texto/estado/tipo/forma de pago) que la
   * búsqueda principal, pero sin fecha ni paginación propia — se repite en cada búsqueda nueva
   * en vez de sólo cuando cambia el filtro, porque es más simple que rastrear cuáles de los
   * tantos disparadores de `ejecutarBusqueda` tocan el filtro y cuáles sólo cambian de página;
   * el pedido es liviano igual. */
  private buscarRegalos(autoExpandir = false): void {
    this.boleteriaService
      .buscar({ ...this.construirFiltroBase(), sinFecha: true, page: 0, size: TAMANIO_MAXIMO_VENTANA_AGRUPADA })
      .subscribe({
        next: (res) => {
          this.regalosResultado.set(res);
          if (autoExpandir && res.content.length > 0) this.regalosExpandido.set(true);
        },
        error: (err) => console.error('No se pudieron traer los regalos:', err),
      });
  }

  /** Un día puntual ("Ver esa fecha"): la tabla de siempre, paginada por cantidad de filas. */
  private ejecutarBusquedaSimple(): void {
    this.boleteriaService
      .buscar({
        ...this.construirFiltroBase(),
        fecha: this.fecha(),
        ordenarPor: this.ordenarPor(),
        direccion: this.direccionOrden(),
        page: this.pagina(),
        size: TAMANIO_PAGINA,
      })
      .subscribe({
        next: (res) => {
          this.resultado.set(res);
          this.buscando.set(false);
        },
        error: (err) => {
          console.error('No se pudo buscar:', err);
          this.errorBusqueda.set('No se pudo buscar. Revisá la conexión y reintentá.');
          this.buscando.set(false);
        },
      });
  }

  /**
   * Vista agrupada. Si `reubicar` (recién se activó "todas las fechas" o cambió un filtro),
   * primero refresca la lista de días con actividad y se planta en la página que contiene
   * "hoy"; si sólo se está navegando (`irAPagina`/Anterior/Siguiente), la lista de días ya
   * está en memoria y sólo trae las compras de la página actual. Cada página cubre el rango
   * `desde`/`hasta` de su trozo de `paginasDias`, así ningún día queda partido entre dos.
   */
  private ejecutarBusquedaAgrupada(reubicar: boolean): void {
    if (!reubicar) {
      this.buscarComprasDeLaPaginaActual();
      return;
    }
    traerTodasLasPaginas((page) =>
      this.boleteriaService.buscarFechasDistintas({ ...this.construirFiltroBase(), page, size: TAMANIO_MAXIMO_DIAS_AGRUPADOS })
    ).subscribe({
      next: (paginaFechas) => {
        this.diasConActividad.set(paginaFechas.content);
        this.pagina.set(this.paginaHoy());
        this.buscarComprasDeLaPaginaActual();
      },
      error: (err) => {
        console.error('No se pudieron traer los días con actividad:', err);
        this.diasConActividad.set([]);
        this.pagina.set(0);
        this.errorBusqueda.set('No se pudo buscar. Revisá la conexión y reintentá.');
        this.buscando.set(false);
      },
    });
  }

  /** Trae todas las compras del rango de fechas de la página actual (`paginasDias`). Los
   * regalos (sin fecha) no aparecen acá — /fechas-visita ya los excluye, ver su propio
   * bloque colapsable aparte. */
  private buscarComprasDeLaPaginaActual(): void {
    const paginaActual = this.paginasDias()[this.pagina()];
    if (!paginaActual) {
      this.resultado.set({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 });
      this.buscando.set(false);
      return;
    }
    traerTodasLasPaginas((page) =>
      this.boleteriaService.buscar({
        ...this.construirFiltroBase(),
        fechaDesde: paginaActual.desde,
        fechaHasta: paginaActual.hasta,
        ordenarPor: 'fechaVisita',
        direccion: 'ASC',
        page,
        size: TAMANIO_MAXIMO_VENTANA_AGRUPADA,
      })
    ).subscribe({
        next: (res) => {
          this.resultado.set(res);
          this.buscando.set(false);
          queueMicrotask(() => this.centrarEnHoy());
        },
        error: (err) => {
          console.error('No se pudo buscar:', err);
          this.errorBusqueda.set('No se pudo buscar. Revisá la conexión y reintentá.');
          this.buscando.set(false);
        },
      });
  }

  /** Al entrar a la vista agrupada (o volver a la página que contiene "hoy"), lleva el scroll
   * a ese contenedor para que sea el punto de partida visual; en cualquier otra página el
   * selector simplemente no encuentra nada y no hace nada. */
  private centrarEnHoy(): void {
    document.querySelector('[data-grupo-hoy]')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // ---------- Validar / cobrar ----------

  /** Compra ya pagada online (APROBADO) → habilita el ingreso. */
  validarIngreso(reserva: Reserva, ausentes: AusenteValidacion[] = []): void {
    this.ejecutarAccion(reserva, () =>
      this.boleteriaService.validarIngreso(reserva.id, { ausentes, impresora: this.impresoraVouchers() })
    );
  }

  /** Reserva a la que se le está eligiendo quiénes vinieron. */
  validandoParcial = signal<Reserva | null>(null);

  /** Pagada online y para más de una persona: se puede validar sólo a los que vinieron. */
  admiteValidacionParcial(reserva: Reserva): boolean {
    if (reserva.formaPago !== 'MERCADO_PAGO' || reserva.montoTotal <= 0) return false;
    const personas = (reserva.detalles ?? [])
      .filter((d) => d.tipoEntrada?.tipo === 'ENTRADA')
      .reduce((a, d) => a + d.cantidad, 0);
    return personas > 1;
  }

  confirmarValidacionParcial(ausentes: AusenteValidacion[]): void {
    const reserva = this.validandoParcial();
    this.validandoParcial.set(null);
    if (reserva) this.validarIngreso(reserva, ausentes);
  }

  vouchersVigentes(reserva: Reserva): VoucherReserva[] {
    return (reserva.vouchers ?? []).filter((v) => !v.anulado);
  }

  reimprimiendoId = signal<number | null>(null);

  reimprimirVouchers(reserva: Reserva): void {
    this.reimprimiendoId.set(reserva.id);
    this.boleteriaService.reimprimirVouchers(reserva.id, this.impresoraVouchers()).subscribe({
      next: () => {
        this.reimprimiendoId.set(null);
        this.toast.exito('Vouchers enviados a la ticketera');
      },
      error: (err) => {
        this.reimprimiendoId.set(null);
        this.toast.error(typeof err?.error === 'string' ? err.error : 'No se pudieron reimprimir los vouchers');
      },
    });
  }

  /** La ticketera de esta tablet; null = la única conectada. */
  private impresoraVouchers(): string | null {
    return this.impresoras.efectiva()?.nombre ?? this.impresoras.elegida();
  }

  /** Reserva con pago en efectivo (RESERVADO_EFECTIVO) → cobra y habilita el ingreso. */
  cobrarYValidar(reserva: Reserva): void {
    this.ejecutarAccion(reserva, () => this.boleteriaService.cobrarEfectivoYValidar(reserva.id));
  }

  private ejecutarAccion(reserva: Reserva, accion: () => Observable<Reserva>): void {
    this.procesandoId.set(reserva.id);
    this.errorAccion.set(null);

    accion().subscribe({
      next: (actualizada) => {
        this.reemplazarEnResultado(actualizada);
        const vouchers = this.vouchersVigentes(actualizada).length;
        if (actualizada.estado === 'USADO' && vouchers > 0) {
          this.toast.info(vouchers === 1 ? 'Se mandó a imprimir 1 voucher' : `Se mandaron a imprimir ${vouchers} vouchers`);
        }
        if (actualizada.estado === 'USADO') this.iniciarOcultamientoPorValidacion(actualizada.id);
      },
      error: (err) => {
        console.error('Error al validar el ingreso:', err);
        this.errorAccion.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo completar la validación. Reintentá.'
        );
        this.procesandoId.set(null);
      },
    });
  }

  /** Reemplaza una fila con lo que devolvió el backend, sin tener que rehacer toda la búsqueda.
   * Una reserva puede estar en `resultado` (vista principal) y/o en `regalosResultado` (bloque
   * de regalos) a la vez si son el mismo id — se actualizan las dos para que no queden
   * desincronizadas sin importar desde cuál de las dos se disparó la acción. */
  reemplazarEnResultado(actualizada: Reserva): void {
    this.resultado.update((res) =>
      res ? { ...res, content: res.content.map((r) => (r.id === actualizada.id ? actualizada : r)) } : res
    );
    this.regalosResultado.update((res) =>
      res ? { ...res, content: res.content.map((r) => (r.id === actualizada.id ? actualizada : r)) } : res
    );
    this.procesandoId.set(null);
  }

  procesandoId = signal<number | null>(null);

  procesando(reserva: Reserva): boolean {
    return this.procesandoId() === reserva.id;
  }

  // ---------- Fila recién validada: se pone roja, cuenta regresiva y desaparece sola ----------

  /** Cuánto dura la ventana para arrepentirse antes de que la fila empiece a irse. En el POS
   * (Anticipadas) es la ÚNICA forma de deshacer — no tiene el menú ⋮ de Control de Accesos — así
   * que va generosa; ver VENTANA_DESHACER_MENU_MS en Control de Accesos, que da un poco más
   * todavía para el caso de "me di cuenta minutos después". */
  readonly VENTANA_DESHACER_MS = 160_000;
  private static readonly DURACION_ANIMACION_SALIDA_MS = 350;

  /** Ids validados en esta sesión de pantalla, todavía dentro de la ventana para deshacer. */
  recienValidados = signal<ReadonlySet<number>>(new Set());
  /** Ids a los que ya se les venció la ventana y están en pleno slide-out. */
  saliendoIds = signal<ReadonlySet<number>>(new Set());
  /** Ids que ya terminaron de irse: se excluyen de "visibles" aunque el backend los siga trayendo. */
  private idsOcultosPorValidacion = signal<ReadonlySet<number>>(new Set());
  private timersOcultamiento = new Map<number, ReturnType<typeof setTimeout>>();

  deshaciendoId = signal<number | null>(null);

  private iniciarOcultamientoPorValidacion(id: number): void {
    this.cancelarOcultamientoPorValidacion(id);
    this.recienValidados.update((s) => new Set(s).add(id));

    const timerSalida = setTimeout(() => {
      this.saliendoIds.update((s) => new Set(s).add(id));
      const timerOcultar = setTimeout(() => {
        this.idsOcultosPorValidacion.update((s) => new Set(s).add(id));
        this.recienValidados.update((s) => {
          const copia = new Set(s);
          copia.delete(id);
          return copia;
        });
        this.saliendoIds.update((s) => {
          const copia = new Set(s);
          copia.delete(id);
          return copia;
        });
        this.timersOcultamiento.delete(id);
      }, ReservasBusqueda.DURACION_ANIMACION_SALIDA_MS);
      this.timersOcultamiento.set(id, timerOcultar);
    }, this.VENTANA_DESHACER_MS);
    this.timersOcultamiento.set(id, timerSalida);
  }

  /** Saca un id de todo el circuito de ocultamiento (se llama al deshacer o al empezar una búsqueda nueva). */
  private cancelarOcultamientoPorValidacion(id: number): void {
    const timer = this.timersOcultamiento.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timersOcultamiento.delete(id);
    }
    this.recienValidados.update((s) => {
      if (!s.has(id)) return s;
      const copia = new Set(s);
      copia.delete(id);
      return copia;
    });
    this.saliendoIds.update((s) => {
      if (!s.has(id)) return s;
      const copia = new Set(s);
      copia.delete(id);
      return copia;
    });
  }

  /** Se llama al arrancar cada búsqueda nueva: el estado de "recién validado" sólo tiene sentido para la lista actual. */
  private limpiarOcultamientosPorValidacion(): void {
    this.timersOcultamiento.forEach((t) => clearTimeout(t));
    this.timersOcultamiento.clear();
    this.recienValidados.set(new Set());
    this.saliendoIds.set(new Set());
    this.idsOcultosPorValidacion.set(new Set());
  }

  /** Botón "Cancelar validación" dentro de la ventana: vuelve la compra a como estaba antes de validarla. */
  deshacerValidacion(reserva: Reserva, event?: Event): void {
    event?.stopPropagation();
    this.cancelarOcultamientoPorValidacion(reserva.id);
    this.deshaciendoId.set(reserva.id);
    this.errorAccion.set(null);

    this.boleteriaService.deshacerValidacion(reserva.id).subscribe({
      next: (actualizada) => {
        this.reemplazarEnResultado(actualizada);
        this.deshaciendoId.set(null);
      },
      error: (err) => {
        console.error('Error al deshacer la validación:', err);
        this.errorAccion.set(typeof err?.error === 'string' ? err.error : 'No se pudo deshacer la validación.');
        this.deshaciendoId.set(null);
      },
    });
  }

  /** Cancela los timers de "recién validado" pendientes al destruirse la pantalla que lo provee. */
  ngOnDestroy(): void {
    this.timersOcultamiento.forEach((t) => clearTimeout(t));
    this.timersOcultamiento.clear();
  }
}
