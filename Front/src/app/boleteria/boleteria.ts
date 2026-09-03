import { Component, HostListener, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { BoleteriaService, CampoOrdenCompras, EstadoCompra, Pagina, Reserva, TipoListadoCompra } from '../services/boleteria.service';
import { FormaPagoType } from '../models/compra';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { CajaService, Caja } from '../services/caja.service';
import { AperturaCaja } from '../pos/apertura-caja/apertura-caja';
import { LucideSearchX, LucideEllipsisVertical, LucideChevronDown, LucideChevronLeft, LucideChevronRight, LucideGift } from '@lucide/angular';
import { Spinner } from '../shared/spinner/spinner';
import { Modal } from '../shared/modal/modal';
import { crearOrdenable } from '../shared/ordenable';
import { ColumnaOrdenable } from '../shared/columna-ordenable/columna-ordenable';
import { PesosPipe } from '../shared/pesos.pipe';
import { TourStep } from '../shared/tour/tour';
import { DetectorEscaneoDni, esEscaneoDocumento, extraerDniDeEscaneo } from '../shared/escaner-dni.util';
import { aFechaISO } from '../shared/fecha.util';

/** ~6 pasos, todos apuntando a elementos siempre presentes en el DOM (nada detrás de "Más
 * filtros" ni de una fila de resultado puntual, que dependen de los datos del momento). */
const PASOS_TUTORIAL: TourStep[] = [
  {
    selector: '[data-tour="caja"]',
    titulo: 'Tu caja',
    texto: 'Acá ves si tu caja está abierta. Si está cerrada, tocá para abrirla antes de cobrar cualquier cosa.',
  },
  {
    selector: '[data-tour="buscador"]',
    titulo: 'Buscar una reserva',
    texto: 'Buscá por DNI, nombre, email o código de reserva. También podés escanear el DNI: el lector actúa como si tipearas y apretaras Enter.',
  },
  {
    selector: '[data-tour="fecha"]',
    titulo: 'Día a consultar',
    texto: 'Elegí un día puntual, o tocá "Ver todas las fechas" para buscar sin importar cuándo es la visita.',
  },
  {
    selector: '[data-tour="estado"]',
    titulo: 'Pagadas o a cobrar',
    texto: 'Filtrá entre lo ya pagado online y lo que falta cobrar en caja.',
  },
  {
    selector: '[data-tour="mas-filtros"]',
    titulo: 'Más filtros',
    texto: 'Acá encontrás el resto de los estados (ya utilizada, pago pendiente, cancelada, reembolsada), el tipo de listado (Anticipada o venta de Boletería) y la forma de pago, por si necesitás afinar más la búsqueda.',
  },
  {
    selector: '[data-tour="resultados"]',
    titulo: 'Validar o cobrar',
    texto: 'Cada resultado tiene un botón para validar el ingreso o cobrar y validar, y un menú ⋮ para editar el contacto, reenviar el mail o reembolsar.',
  },
];

function fechaComoInput(d: Date): string {
  return aFechaISO(d);
}

function hoyComoFechaInput(): string {
  return aFechaISO(new Date());
}

/** "Hoy"/"Mañana"/"Ayer" cuando aplica; null para el resto, que se muestra como día de semana + fecha corta. */
function etiquetaRelativaFecha(fechaVisita: string, hoy: string): string | null {
  if (fechaVisita === hoy) return 'Hoy';
  const dHoy = new Date(hoy + 'T00:00:00');
  const dVisita = new Date(fechaVisita + 'T00:00:00');
  const diffDias = Math.round((dVisita.getTime() - dHoy.getTime()) / 86400000);
  if (diffDias === 1) return 'Mañana';
  if (diffDias === -1) return 'Ayer';
  return null;
}

const DIAS_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** Encabezado de cada "contenedor" del día en la vista agrupada: relativa si aplica (Hoy/Mañana/Ayer), si no día de semana + fecha completa. */
function etiquetaGrupoFecha(fechaVisita: string, hoy: string): string {
  const relativa = etiquetaRelativaFecha(fechaVisita, hoy);
  if (relativa) return relativa;
  const d = new Date(fechaVisita + 'T00:00:00');
  return `${DIAS_SEMANA[d.getDay()]} ${d.getDate()} de ${MESES[d.getMonth()]}`;
}

/** Un "contenedor" de la vista agrupada por día (sólo se arma cuando `todasLasFechas()` está activo). */
interface GrupoDia {
  fecha: string;
  etiqueta: string;
  esHoy: boolean;
  vistas: ReservaVista[];
  totalPases: number;
}

/** Todas las formas de pago posibles: las de la compra online y las que agrega la venta en puerta. */
const FORMAS_PAGO_FILTRABLES: { valor: FormaPagoType; etiqueta: string }[] = [
  { valor: 'MERCADO_PAGO', etiqueta: 'Mercado Pago' },
  { valor: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
  { valor: 'RESERVA_ADMIN', etiqueta: 'Generada (admin)' },
  { valor: 'SIN_COBRO', etiqueta: 'Sin cobro' },
];

/** Los dos que un boletero toca todo el día, para ver cuánto falta por llegar: siempre
 * visibles, fuera de "Más filtros". También son los que quedan activos por defecto. */
const ESTADOS_ANTICIPADA_PRINCIPALES: { valor: EstadoCompra; etiqueta: string }[] = [
  { valor: 'RESERVADO_EFECTIVO', etiqueta: 'A cobrar en caja' },
  { valor: 'APROBADO', etiqueta: 'Pagada online' },
];

/** El resto de los estados: casi no se usan y ocupaban mucho espacio a la vista, así que
 * quedan detrás de "Más filtros" junto con el tipo de listado y la forma de pago. */
const ESTADOS_ANTICIPADA_SECUNDARIOS: { valor: EstadoCompra; etiqueta: string }[] = [
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

/** A nivel de módulo para no reconstruir el objeto en cada lectura. */
const ETIQUETAS_ESTADO: Record<EstadoCompra, string> = {
  APROBADO: 'Pagada online',
  RESERVADO_EFECTIVO: 'A cobrar en caja',
  USADO: 'Ya utilizada',
  PENDIENTE_PAGO: 'Pago pendiente',
  CANCELADO: 'Cancelada',
  VENDIDO_EN_PUERTA: 'Vendida en puerta',
  REEMBOLSADA: 'Reembolsada',
};

interface DetalleLinea {
  nombre: string;
  cantidad: number;
}

/**
 * Reserva ya preparada para el template: pases/extras separados, etiqueta de estado
 * resuelta y la etiqueta relativa de fecha ("Hoy"/"Mañana"/"Ayer" o null si hay que
 * mostrar día de semana + fecha corta), todo calculado una sola vez por resultado en
 * lugar de recalcularse en cada ciclo de detección de cambios.
 */
interface ReservaVista {
  reserva: Reserva;
  pases: DetalleLinea[];
  extras: DetalleLinea[];
  totalPases: number;
  etiquetaFecha: string | null;
  etiquetaEstado: string;
}

function aVista(reserva: Reserva, hoy: string): ReservaVista {
  const pases: DetalleLinea[] = [];
  const extras: DetalleLinea[] = [];
  let totalPases = 0;

  for (const detalle of reserva.detalles ?? []) {
    const tipo = detalle.tipoEntrada;
    if (!tipo) continue;

    if (tipo.tipo === 'ENTRADA') {
      pases.push({ nombre: tipo.nombre, cantidad: detalle.cantidad });
      totalPases += detalle.cantidad;
    } else {
      extras.push({ nombre: tipo.nombre, cantidad: detalle.cantidad });
    }
  }

  return {
    reserva,
    pases,
    extras,
    totalPases,
    etiquetaFecha: reserva.fechaVisita ? etiquetaRelativaFecha(reserva.fechaVisita, hoy) : null,
    etiquetaEstado: ETIQUETAS_ESTADO[reserva.estado] ?? reserva.estado,
  };
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
  return fechaComoInput(d);
}

/** El lunes de la semana calendario de `fecha` (yyyy-MM-dd). Las páginas de la vista agrupada
 * son semanas fijas lunes–domingo, no ventanas móviles ancladas en "hoy": así el rango de una
 * página no cambia porque entró o salió una reserva. */
function lunesDeLaSemana(fecha: string): string {
  const d = new Date(fecha + 'T00:00:00');
  const diaSemana = (d.getDay() + 6) % 7; // 0 = lunes … 6 = domingo
  return sumarDias(fecha, -diaSemana);
}

@Component({
  selector: 'app-boleteria',
  imports: [
    PesosPipe,
    DatePipe,
    NgTemplateOutlet,
    FormsModule,
    CabeceraInterna,
    AperturaCaja,
    Spinner,
    Modal,
    ColumnaOrdenable,
    LucideSearchX,
    LucideEllipsisVertical,
    LucideChevronDown,
    LucideChevronLeft,
    LucideChevronRight,
    LucideGift,
  ],
  templateUrl: './boleteria.html',
  styleUrl: './boleteria.css',
})
export class Boleteria implements OnInit, OnDestroy {
  private boleteriaService = inject(BoleteriaService);
  private cajaService = inject(CajaService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly pasosTutorial = PASOS_TUTORIAL;

  /** undefined = todavía no llegó la respuesta; null = no tiene ninguna caja abierta. */
  cajaActual = signal<Caja | null | undefined>(undefined);
  mostrarAperturaCaja = signal(false);

  /** El propio indicador de "caja cerrada" duplica como botón para abrirla, sin tener que ir a Vender entradas. */
  onCajaAbierta(caja: Caja): void {
    this.cajaActual.set(caja);
    this.mostrarAperturaCaja.set(false);
  }

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
  /** Estado/Forma de pago quedan colapsados por defecto: son los filtros que casi no se tocan en el uso diario. */
  mostrarMasFiltros = signal(false);
  pagina = signal(0);
  private orden = crearOrdenable<CampoOrdenCompras>('fechaVisita');
  ordenarPor = this.orden.ordenarPor;
  direccionOrden = this.orden.direccionOrden;
  estadoOrden = this.orden.estadoOrden;

  readonly estadosPrincipales = ESTADOS_ANTICIPADA_PRINCIPALES;
  readonly estadosSecundarios = ESTADOS_ANTICIPADA_SECUNDARIOS;
  readonly formasPagoFiltrables = FORMAS_PAGO_FILTRABLES;

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

  /** Detecta un escaneo de DNI cuando el foco no está en un campo de texto (ver DetectorEscaneoDni). */
  private detectorEscaneo = new DetectorEscaneoDni((escaneo) => {
    if (this.buscando()) return;
    this.texto.set(escaneo);
    this.buscar();
  });

  ngOnInit(): void {
    // Si se llega acá desde el escaneo de fondo del POS, ya viene con el DNI: precargamos el
    // buscador y disparamos la búsqueda en vez de la de "hoy" por defecto. Se limpia el query
    // param enseguida para que un refresh no repita la búsqueda ni el botón "atrás" quede
    // pegado en esta URL.
    const dniDesdeEscaneo = this.route.snapshot.queryParamMap.get('dni');
    if (dniDesdeEscaneo) {
      this.texto.set(dniDesdeEscaneo);
      this.buscar();
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    } else {
      // Al entrar, mostramos directamente lo accionable de hoy: es lo que un boletero
      // necesita ver primero, sin tener que buscar nada.
      this.ejecutarBusqueda();
    }

    // Acá no se abre/cierra caja (eso es en Vender entradas), pero conviene saber de
    // un vistazo si ya la abriste antes de mandarte a buscar una reserva para cobrar.
    this.cajaService.getActual().subscribe({
      next: (caja) => this.cajaActual.set(caja),
      error: (err) => {
        console.error('Error al consultar la caja actual:', err);
        this.cajaActual.set(null);
      },
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKeydownGlobal(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.menuAbiertoId() !== null) {
      this.menuAbiertoId.set(null);
      return;
    }
    this.detectorEscaneo.procesarTecla(event);
  }

  ngOnDestroy(): void {
    this.detectorEscaneo.destruir();
  }

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
  private ejecutarBusqueda(reubicar = this.todasLasFechas(), autoExpandirRegalos = false): void {
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

  limpiar(): void {
    this.texto.set('');
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
  }

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

  /** Reemplaza una fila con lo que devolvió el backend, sin tener que rehacer toda la búsqueda. */
  /** Una reserva puede estar en `resultado` (vista principal) y/o en `regalosResultado` (bloque
   * de regalos) a la vez si son el mismo id — se actualizan las dos para que no queden
   * desincronizadas sin importar desde cuál de las dos se disparó la acción. */
  private reemplazarEnResultado(actualizada: Reserva): void {
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

  /** Cuánto dura la ventana para arrepentirse antes de que la fila empiece a irse. Corta a
   * propósito: es sólo para el "uy, toqué mal" inmediato, no la única forma de deshacer — ver
   * VENTANA_DESHACER_MENU_MS más abajo para el caso de "me di cuenta minutos después". */
  readonly VENTANA_DESHACER_MS = 8000;
  private static readonly DURACION_ANIMACION_SALIDA_MS = 350;

  /** Igual al límite que ya valida el backend (`CompraServiceImpl.deshacerValidacion`): un
   * "Deshacer validación" en el menú "⋮", disponible más allá de los 8s de arriba —para
   * cuando el error se nota minutos después, no al toque— pero no ilimitado, porque más allá
   * de esta ventana el backend lo rechaza igual (y correr más ese límite ahí arriescaría
   * descuadrar una caja que ya se cerró con esa venta adentro). */
  private static readonly VENTANA_DESHACER_MENU_MS = 120_000;

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
      }, Boleteria.DURACION_ANIMACION_SALIDA_MS);
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
  deshacerValidacion(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
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

  // ---------- Menú de acciones (editar contacto / reenviar mail / reembolsar) ----------

  menuAbiertoId = signal<number | null>(null);

  toggleMenu(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.update((actual) => (actual === reserva.id ? null : reserva.id));
  }

  @HostListener('document:click')
  cerrarMenu(): void {
    this.menuAbiertoId.set(null);
  }

  editando = signal<Reserva | null>(null);
  /** dni/receptorDni: cuál de los dos aplica depende de esRegaloDe(reserva) — un titular normal
   * usa dni, un regalo usa receptorDni (es el que se presenta en la puerta para ingresar). */
  formEdit = { nombre: '', apellido: '', dni: '', email: '', telefono: '', receptorDni: '' };
  guardandoEdit = signal(false);
  errorEdit = signal<string | null>(null);

  /** Sin fecha de visita = regalo: el DNI que vale para entrar es el de quien lo recibe, no el
   * del comprador (ver receptorDni en Compra). */
  esRegaloDe(reserva: Reserva): boolean {
    return reserva.fechaVisita == null;
  }

  abrirEdicion(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    this.errorEdit.set(null);
    this.formEdit = {
      nombre: reserva.cliente?.nombre ?? '',
      apellido: reserva.cliente?.apellido ?? '',
      dni: reserva.cliente?.dni ?? '',
      email: reserva.contactEmail ?? '',
      telefono: reserva.contactPhone ?? '',
      receptorDni: reserva.receptorDni ?? '',
    };
    this.editando.set(reserva);
  }

  cancelarEdicion(): void {
    this.editando.set(null);
    this.errorEdit.set(null);
  }

  guardarEdicion(): void {
    const reserva = this.editando();
    if (!reserva || this.guardandoEdit()) return;

    // El DNI es lo que se compara contra el documento real en la puerta: si se está por
    // cambiar, mejor confirmar — no es un dato de contacto cualquiera como el teléfono.
    const dniTitularCambio = !this.esRegaloDe(reserva) && this.formEdit.dni.trim() !== (reserva.cliente?.dni ?? '');
    const dniReceptorCambio = this.esRegaloDe(reserva) && this.formEdit.receptorDni.trim() !== (reserva.receptorDni ?? '');
    if (dniTitularCambio || dniReceptorCambio) {
      const confirmado = window.confirm(
        'Estás por cambiar el DNI de esta reserva: es lo que se compara contra el documento real al validar el ingreso. ¿Confirmás?'
      );
      if (!confirmado) return;
    }

    this.guardandoEdit.set(true);
    this.errorEdit.set(null);

    this.boleteriaService.editarContacto(reserva.id, { ...this.formEdit }).subscribe({
      next: (actualizada) => {
        this.reemplazarEnResultado(actualizada);
        this.guardandoEdit.set(false);
        this.editando.set(null);
      },
      error: (err) => {
        console.error('Error al editar el contacto:', err);
        this.errorEdit.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar los cambios.');
        this.guardandoEdit.set(false);
      },
    });
  }

  reenviandoId = signal<number | null>(null);
  avisoReenvio = signal<string | null>(null);

  reenviarMail(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    this.reenviandoId.set(reserva.id);
    this.avisoReenvio.set(null);

    this.boleteriaService.reenviarMail(reserva.id).subscribe({
      next: () => {
        this.reenviandoId.set(null);
        this.avisoReenvio.set(`Mail reenviado (compra #${reserva.codigoReserva}).`);
      },
      error: (err) => {
        console.error('Error al reenviar el mail:', err);
        this.reenviandoId.set(null);
        this.avisoReenvio.set(typeof err?.error === 'string' ? err.error : 'No se pudo reenviar el mail.');
      },
    });
  }

  /** Sólo tiene sentido para lo pagado online que todavía no ingresó: es lo único que se reembolsa. */
  puedeReembolsar(reserva: Reserva): boolean {
    return reserva.estado === 'APROBADO';
  }

  /** Habilita "Deshacer validación" en el menú "⋮" para cualquier USADO reciente, más allá de
   * los 8s de la fila roja — el backend vuelve a chequear la ventana igual, así que esto es
   * sólo para no mostrar un botón que de entrada ya sabemos que va a fallar. */
  puedeDeshacerDesdeMenu(reserva: Reserva): boolean {
    if (reserva.estado !== 'USADO' || !reserva.fechaValidacion) return false;
    const validado = new Date(reserva.fechaValidacion).getTime();
    return Date.now() - validado < Boleteria.VENTANA_DESHACER_MENU_MS;
  }

  reembolsandoId = signal<number | null>(null);

  reembolsar(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    const confirmado = window.confirm(
      `¿Reembolsar la compra #${reserva.codigoReserva} por ${reserva.montoTotal}? Esto devuelve el dinero real a través de Mercado Pago y no se puede deshacer.`
    );
    if (!confirmado) return;

    this.reembolsandoId.set(reserva.id);
    this.errorAccion.set(null);

    this.boleteriaService.reembolsar(reserva.id).subscribe({
      next: (actualizada) => {
        this.reemplazarEnResultado(actualizada);
        this.reembolsandoId.set(null);
      },
      error: (err) => {
        console.error('Error al reembolsar:', err);
        this.errorAccion.set(typeof err?.error === 'string' ? err.error : 'No se pudo procesar el reembolso.');
        this.reembolsandoId.set(null);
      },
    });
  }
}
