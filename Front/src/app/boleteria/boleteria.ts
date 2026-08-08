import { Component, HostListener, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BoleteriaService, CampoOrdenCompras, EstadoCompra, Pagina, Reserva, TipoListadoCompra } from '../Services/boleteria.service';
import { FormaPagoType } from '../models/compra';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { CajaService, Caja } from '../Services/caja.service';
import { AperturaCaja } from '../pos/apertura-caja/apertura-caja';
import { LucideSearchX, LucideEllipsisVertical, LucideArrowUp, LucideArrowDown, LucideChevronsUpDown } from '@lucide/angular';

function hoyComoFechaInput(): string {
  const hoy = new Date();
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const dia = String(hoy.getDate()).padStart(2, '0');
  return `${hoy.getFullYear()}-${mes}-${dia}`;
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

/**
 * Los lectores de código de barras PDF417 (los que leen el dorso del DNI argentino) se
 * comportan como un teclado: "tipean" el texto decodificado en el campo con foco y rematan
 * con Enter, igual que si alguien lo escribiera a mano y apretara Enter. El DNI no viene solo:
 * el PDF417 trae varios campos separados por "@" (apellido@nombre@sexo@dni@ejemplar@fechaNacimiento@...),
 * así que hay que extraer el campo del documento en vez de usar el texto crudo tal cual.
 */
function extraerDniDeEscaneo(valorCrudo: string): string {
  const limpio = valorCrudo.trim();
  if (limpio.includes('@')) {
    const dni = limpio.split('@')[3]?.trim();
    if (dni && /^\d+$/.test(dni)) return dni;
  }
  return limpio.replace(/\D/g, '') || limpio;
}

/** Las cuatro existentes: las dos de la compra online y las que agrega la venta en puerta. */
const FORMAS_PAGO_FILTRABLES: { valor: FormaPagoType; etiqueta: string }[] = [
  { valor: 'MERCADO_PAGO', etiqueta: 'Mercado Pago' },
  { valor: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
  { valor: 'RESERVA_ADMIN', etiqueta: 'Generada (admin)' },
];

/**
 * Estados que puede tener una reserva anticipada (todo menos la venta de puerta, que es
 * su propio "tipo" de listado). Los dos primeros son los que un boletero toca todo el
 * día; los demás casi no se usan, pero quedan a un toque de distancia.
 */
const ESTADOS_ANTICIPADA_FILTRABLES: { valor: EstadoCompra; etiqueta: string }[] = [
  { valor: 'RESERVADO_EFECTIVO', etiqueta: 'A cobrar en caja' },
  { valor: 'APROBADO', etiqueta: 'Pagada online' },
  { valor: 'USADO', etiqueta: 'Ya utilizada' },
  { valor: 'PENDIENTE_PAGO', etiqueta: 'Pago pendiente' },
  { valor: 'CANCELADO', etiqueta: 'Cancelada' },
  { valor: 'REEMBOLSADA', etiqueta: 'Reembolsada' },
];

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

@Component({
  selector: 'app-boleteria',
  imports: [CurrencyPipe, DatePipe, FormsModule, CabeceraInterna, AperturaCaja, LucideSearchX, LucideEllipsisVertical, LucideArrowUp, LucideArrowDown, LucideChevronsUpDown],
  templateUrl: './boleteria.html',
  styleUrl: './boleteria.css',
})
export class Boleteria implements OnInit {
  private boleteriaService = inject(BoleteriaService);
  private cajaService = inject(CajaService);

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
  pagina = signal(0);
  ordenarPor = signal<CampoOrdenCompras>('fechaVisita');
  direccionOrden = signal<'ASC' | 'DESC'>('ASC');

  readonly estadosAnticipadaFiltrables = ESTADOS_ANTICIPADA_FILTRABLES;
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

  totalElementos = computed(() => this.resultado()?.totalElements ?? 0);
  totalPaginas = computed(() => this.resultado()?.totalPages ?? 0);
  /** Suma de pases sólo de esta página: con paginación real no hay forma barata de sumar todas. */
  totalPasesPagina = computed(() => (this.visibles() ?? []).reduce((acc, v) => acc + v.totalPases, 0));

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

  /** Los filtros de anticipada están como vienen de fábrica: no hace falta ofrecer restablecerlos. */
  filtrosEnDefecto = computed(() =>
    this.tipoListado() === 'ANTICIPADA' &&
    mismosElementos(this.estadosActivos(), ESTADOS_POR_DEFECTO) &&
    this.formaPago() === ''
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
    if (this.ordenarPor() === campo) {
      this.direccionOrden.update((d) => (d === 'ASC' ? 'DESC' : 'ASC'));
    } else {
      this.ordenarPor.set(campo);
      this.direccionOrden.set('ASC');
    }
    this.pagina.set(0);
    this.ejecutarBusqueda();
  }

  /** Qué ícono de orden mostrar en el encabezado: sin ordenar, ascendente o descendente. */
  estadoOrden(campo: CampoOrdenCompras): 'sinOrden' | 'asc' | 'desc' {
    if (this.ordenarPor() !== campo) return 'sinOrden';
    return this.direccionOrden() === 'ASC' ? 'asc' : 'desc';
  }

  paginaAnterior(): void {
    if (this.pagina() === 0) return;
    this.pagina.update((p) => p - 1);
    this.ejecutarBusqueda();
  }

  paginaSiguiente(): void {
    if (this.pagina() + 1 >= this.totalPaginas()) return;
    this.pagina.update((p) => p + 1);
    this.ejecutarBusqueda();
  }

  /** Acumula las teclas de un posible escaneo cuando el foco no está en un campo de texto. */
  private bufferEscaneo = '';
  private ultimoKeyEscaneo = 0;
  private static readonly UMBRAL_MS_ENTRE_TECLAS = 150;
  private static readonly LARGO_MINIMO_ESCANEO = 6;

  ngOnInit(): void {
    // Al entrar, mostramos directamente lo accionable de hoy: es lo que un boletero
    // necesita ver primero, sin tener que buscar nada.
    this.ejecutarBusqueda();

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
    const activo = document.activeElement;
    const escribiendoEnCampo =
      activo instanceof HTMLInputElement ||
      activo instanceof HTMLTextAreaElement ||
      activo instanceof HTMLSelectElement;
    if (escribiendoEnCampo) return;

    if (event.key === 'Enter') {
      const escaneo = this.bufferEscaneo;
      this.bufferEscaneo = '';
      if (escaneo.length >= Boleteria.LARGO_MINIMO_ESCANEO && !this.buscando()) {
        event.preventDefault();
        this.texto.set(escaneo);
        this.buscar();
      }
      return;
    }

    if (event.key.length === 1) {
      const ahora = Date.now();
      if (ahora - this.ultimoKeyEscaneo > Boleteria.UMBRAL_MS_ENTRE_TECLAS) {
        this.bufferEscaneo = '';
      }
      this.bufferEscaneo += event.key;
      this.ultimoKeyEscaneo = ahora;
    }
  }

  /** Un único campo sirve para DNI, código de reserva, nombre o email: el backend prueba los cuatro. */
  buscar(): void {
    const crudo = this.texto().trim();
    if (!crudo || this.buscando()) return;

    // Si lo que llegó es un escaneo PDF417 (lector de DNI), extraemos el DNI puro antes de
    // buscar; para texto libre esto no aplica (se perderían los caracteres no numéricos).
    const query = crudo.includes('@') ? extraerDniDeEscaneo(crudo) : crudo;
    if (query !== crudo) this.texto.set(query);

    this.todasLasFechas.set(true);
    this.pagina.set(0);
    this.ejecutarBusqueda();
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

  private ejecutarBusqueda(): void {
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
    this.limpiarOcultamientosPorValidacion();

    const tipo = this.tipoListado();
    this.boleteriaService
      .buscar({
        texto: this.texto().trim() || undefined,
        fecha: this.todasLasFechas() ? undefined : this.fecha(),
        tipo,
        estados: tipo === 'ANTICIPADA' ? Array.from(this.estadosActivos()) : undefined,
        formaPago: this.formaPago() || undefined,
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

  private ejecutarAccion(reserva: Reserva, accion: () => import('rxjs').Observable<Reserva>): void {
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
  private reemplazarEnResultado(actualizada: Reserva): void {
    this.resultado.update((res) =>
      res ? { ...res, content: res.content.map((r) => (r.id === actualizada.id ? actualizada : r)) } : res
    );
    this.procesandoId.set(null);
  }

  procesandoId = signal<number | null>(null);

  procesando(reserva: Reserva): boolean {
    return this.procesandoId() === reserva.id;
  }

  // ---------- Fila recién validada: se pone roja, cuenta regresiva y desaparece sola ----------

  /** Cuánto dura la ventana para arrepentirse antes de que la fila empiece a irse. */
  readonly VENTANA_DESHACER_MS = 8000;
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
  formEdit = { nombre: '', apellido: '', email: '', telefono: '' };
  guardandoEdit = signal(false);
  errorEdit = signal<string | null>(null);

  abrirEdicion(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.set(null);
    this.errorEdit.set(null);
    this.formEdit = {
      nombre: reserva.cliente?.nombre ?? '',
      apellido: reserva.cliente?.apellido ?? '',
      email: reserva.contactEmail ?? '',
      telefono: reserva.contactPhone ?? '',
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
