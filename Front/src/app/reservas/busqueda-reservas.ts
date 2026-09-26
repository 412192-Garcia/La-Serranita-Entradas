import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { BoleteriaService, CampoOrdenCompras, EstadoCompra, Pagina, Reserva, TipoListadoCompra } from '../services/boleteria.service';
import { FormaPagoType } from '../models/compra';
import { crearOrdenable } from '../shared/ordenable';
import { esEscaneoDocumento, extraerDniDeEscaneo } from '../shared/escaner-dni.util';
import { aFechaISO } from '../shared/fecha.util';
import { ReservaVista, aVista, etiquetaGrupoFecha } from '../shared/reserva-vista.util';

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

/** Todas las formas de pago posibles: las de la compra online y las que agrega la venta en puerta. */
export const FORMAS_PAGO_FILTRABLES: { valor: FormaPagoType; etiqueta: string }[] = [
  { valor: 'MERCADO_PAGO', etiqueta: 'Mercado Pago' },
  { valor: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
  { valor: 'RESERVA_ADMIN', etiqueta: 'Generada (admin)' },
  { valor: 'SIN_COBRO', etiqueta: 'Sin cobro' },
];

/** Los dos que un boletero toca todo el día, para ver cuánto falta por llegar: siempre
 * visibles, fuera de "Más filtros". También son los que quedan activos por defecto. */
export const ESTADOS_ANTICIPADA_PRINCIPALES: { valor: EstadoCompra; etiqueta: string }[] = [
  { valor: 'RESERVADO_EFECTIVO', etiqueta: 'A cobrar en caja' },
  { valor: 'APROBADO', etiqueta: 'Pagada online' },
];

/** El resto de los estados: casi no se usan y ocupaban mucho espacio a la vista, así que
 * quedan detrás de "Más filtros" junto con el tipo de listado y la forma de pago. */
export const ESTADOS_ANTICIPADA_SECUNDARIOS: { valor: EstadoCompra; etiqueta: string }[] = [
  { valor: 'USADO', etiqueta: 'Ya utilizada' },
  { valor: 'PENDIENTE_PAGO', etiqueta: 'Pago pendiente' },
  { valor: 'CANCELADO', etiqueta: 'Cancelada' },
  { valor: 'REEMBOLSADA', etiqueta: 'Reembolsada' },
];

const VALORES_ESTADOS_SECUNDARIOS = new Set(ESTADOS_ANTICIPADA_SECUNDARIOS.map((e) => e.valor));

/** Los dos que un boletero realmente toca todo el día; el resto se activa a mano. */
const ESTADOS_POR_DEFECTO: EstadoCompra[] = ['APROBADO', 'RESERVADO_EFECTIVO'];

/** Devuelve un Set nuevo con el valor agregado o sacado: los signals necesitan otra referencia. */
function alternar<T>(actuales: ReadonlySet<T>, valor: T): ReadonlySet<T> {
  const copia = new Set(actuales);
  if (!copia.delete(valor)) copia.add(valor);
  return copia;
}

function mismosElementos<T>(conjunto: ReadonlySet<T>, esperados: readonly T[]): boolean {
  return conjunto.size === esperados.length && esperados.every((e) => conjunto.has(e));
}

const TAMANIO_PAGINA = 50;
/** Techo duro del backend para /buscar (ver CompraController): alcanza de sobra para todas
 * las compras de una semana calendario en un parque de este tamaño. */
const TAMANIO_MAXIMO_VENTANA_AGRUPADA = 200;
/** Techo duro del backend para /fechas-visita (ver CompraController). La vista agrupada trae
 * de una todos los días con actividad y arma las páginas en memoria; en el horizonte útil de
 * este parque no se llega a 200 días distintos con reservas anticipadas. */
const TAMANIO_MAXIMO_DIAS_AGRUPADOS = 200;

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

/**
 * Estado y lógica de la búsqueda de reservas: filtros, vista de un día o agrupada por semana,
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

  texto = signal('');
  fecha = signal(hoyComoFechaInput());
  /** false = sólo el día elegido; true = todas las fechas (una entrada de otro día igual se deja usar). */
  todasLasFechas = signal(false);
  buscando = signal(false);
  errorBusqueda = signal<string | null>(null);
  errorAccion = signal<string | null>(null);

  /** BOLETERIA = venta de puerta; ANTICIPADA = todo lo que se reservó de antemano. */
  tipoListado = signal<TipoListadoCompra>('ANTICIPADA');
  estadosActivos = signal<ReadonlySet<EstadoCompra>>(new Set(ESTADOS_POR_DEFECTO));
  formaPago = signal<FormaPagoType | ''>('');
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

  toggleRegalos(): void {
    this.regalosExpandido.update((v) => !v);
  }

  totalElementos = computed(() => this.resultado()?.totalElements ?? 0);
  totalPaginas = computed(() => this.resultado()?.totalPages ?? 0);
  /** Suma de pases sólo de esta página: con paginación real no hay forma barata de sumar todas. */
  totalPasesPagina = computed(() => (this.visibles() ?? []).reduce((acc, v) => acc + v.totalPases, 0));

  // ---------- Vista agrupada por día ("Ver todas las fechas") ----------
  // Paginada por SEMANA CALENDARIO (lunes–domingo), no por fila ni por ventana móvil:
  // `ejecutarBusquedaAgrupada` trae de una todos los días con actividad (`diasConActividad`)
  // y arma una página por cada semana que tenga al menos un día con compras (`paginasDias`).
  // El rango de una página es fijo (no se corre porque entró o salió una reserva); la semana
  // que contiene "hoy" (`paginaHoy`) es el punto de partida y queda marcada, y hay un botón
  // "Hoy" para volver desde cualquier lado.
  private diasConActividad = signal<string[]>([]);

  /** Una página por cada semana calendario (lunes–domingo) con al menos un día con compras.
   * `desde`/`hasta` son el lunes y el domingo de esa semana, para etiquetar el botón. */
  paginasDias = computed<{ indice: number; desde: string; hasta: string }[]>(() => {
    const lunes = [...new Set(this.diasConActividad().map(lunesDeLaSemana))].sort();
    return lunes.map((desde, indice) => ({ indice, desde, hasta: sumarDias(desde, 6) }));
  });

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
    return rangoFechasCorto(pagina.desde, pagina.hasta);
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

  toggleTipoListado(tipo: TipoListadoCompra): void {
    if (this.tipoListado() === tipo) return;
    this.tipoListado.set(tipo);
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  toggleEstado(estado: EstadoCompra): void {
    this.estadosActivos.update((actuales) => alternar(actuales, estado));
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  onFormaPagoChange(valor: string): void {
    this.formaPago.set(valor as FormaPagoType | '');
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  /** Sólo lo que vive detrás de "Más filtros" (tipo de listado, forma de pago, y los estados
   * secundarios que se activaron a mano): controla el puntito de aviso en ese botón — los dos
   * estados principales no cuentan porque ya son siempre visibles. */
  filtrosAvanzadosEnDefecto = computed(() => {
    const hayEstadoSecundarioActivo = [...this.estadosActivos()].some((e) => VALORES_ESTADOS_SECUNDARIOS.has(e));
    return this.tipoListado() === 'ANTICIPADA' && this.formaPago() === '' && !hayEstadoSecundarioActivo;
  });

  /** Todos los filtros a la vez, Estado incluido: controla "Restablecer filtros", que por eso
   * vive fuera del panel colapsable — si no, no habría forma de resetear Estado sin abrirlo. */
  filtrosEnDefecto = computed(() =>
    this.filtrosAvanzadosEnDefecto() && mismosElementos(this.estadosActivos(), ESTADOS_POR_DEFECTO)
  );

  restablecerFiltros(): void {
    this.tipoListado.set('ANTICIPADA');
    this.estadosActivos.set(new Set(ESTADOS_POR_DEFECTO));
    this.formaPago.set('');
    this.pagina.set(0);
    this.ejecutarBusqueda();
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

  /** Filtros comunes a cualquier búsqueda de boletería, sin fecha/orden/paginación. */
  private construirFiltroBase() {
    const tipo = this.tipoListado();
    return {
      texto: this.texto().trim() || undefined,
      tipo,
      estados: tipo === 'ANTICIPADA' ? Array.from(this.estadosActivos()) : undefined,
      formaPago: this.formaPago() || undefined,
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
    this.boleteriaService
      .buscarFechasDistintas({ ...this.construirFiltroBase(), page: 0, size: TAMANIO_MAXIMO_DIAS_AGRUPADOS })
      .subscribe({
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
    this.boleteriaService
      .buscar({
        ...this.construirFiltroBase(),
        fechaDesde: paginaActual.desde,
        fechaHasta: paginaActual.hasta,
        ordenarPor: 'fechaVisita',
        direccion: 'ASC',
        page: 0,
        size: TAMANIO_MAXIMO_VENTANA_AGRUPADA,
      })
      .subscribe({
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
  validarIngreso(reserva: Reserva): void {
    this.ejecutarAccion(reserva, () => this.boleteriaService.validarIngreso(reserva.id));
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
