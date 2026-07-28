import { Component, HostListener, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { BoleteriaService, EstadoCompra, Reserva } from '../Services/boleteria.service';
import { FormaPagoType } from '../models/compra';
import { SesionService } from '../Services/sesion.service';
import { CabeceraInterna, EnlaceCabecera } from '../shared/cabecera-interna/cabecera-interna';

function hoyComoFechaInput(): string {
  const hoy = new Date();
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const dia = String(hoy.getDate()).padStart(2, '0');
  return `${hoy.getFullYear()}-${mes}-${dia}`;
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
  // Si no matchea el formato esperado, nos quedamos solo con los dígitos (por si el lector
  // antepone algún caracter de control) en vez de usar el texto crudo.
  return limpio.replace(/\D/g, '') || limpio;
}

/** Qué generó los resultados actuales: elige el mensaje de "sin resultados" y resalta la vista activa. */
type ModoBusqueda = 'dni' | 'dia' | 'todas' | 'texto';

type FiltroFormaPago = 'TODAS' | FormaPagoType;

/** Estados que no requieren ninguna acción en boletería. */
const ESTADOS_NO_ACCIONABLES: EstadoCompra[] = ['USADO', 'CANCELADO', 'PENDIENTE_PAGO'];

/** A nivel de módulo para no reconstruir el objeto en cada lectura. */
const ETIQUETAS_ESTADO: Record<EstadoCompra, string> = {
  APROBADO: 'Pagada online',
  RESERVADO_EFECTIVO: 'A cobrar en caja',
  USADO: 'Ya utilizada',
  PENDIENTE_PAGO: 'Pago pendiente',
  CANCELADO: 'Cancelada',
};

interface DetalleLinea {
  nombre: string;
  cantidad: number;
}

/**
 * Reserva ya preparada para el template. Antes el HTML llamaba a pases(), extras(),
 * totalPases(), esHoy() y etiquetaEstado() por fila, y cada uno se re-ejecutaba en cada
 * ciclo de detección de cambios — incluyendo cada tecla que "tipea" el lector de DNI,
 * que manda unos 50 caracteres de golpe. Ahora se calcula una sola vez por resultado.
 */
interface ReservaVista {
  reserva: Reserva;
  pases: DetalleLinea[];
  extras: DetalleLinea[];
  totalPases: number;
  esHoy: boolean;
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
    esHoy: reserva.fechaVisita === hoy,
    etiquetaEstado: ETIQUETAS_ESTADO[reserva.estado] ?? reserva.estado,
  };
}

@Component({
  selector: 'app-boleteria',
  imports: [CurrencyPipe, DatePipe, FormsModule, CabeceraInterna],
  templateUrl: './boleteria.html',
  styleUrl: './boleteria.css',
})
export class Boleteria implements OnInit {
  private boleteriaService = inject(BoleteriaService);
  private sesion = inject(SesionService);

  dni = signal('');
  fecha = signal(hoyComoFechaInput());
  modo = signal<ModoBusqueda>('dia');
  buscando = signal(false);
  /** null = todavía no se buscó nada. */
  resultados = signal<Reserva[] | null>(null);
  errorBusqueda = signal<string | null>(null);

  /** Por defecto solo mostramos lo accionable (a validar o a cobrar). */
  mostrarTodas = signal(false);

  /** Filtro adicional por forma de pago (además del de estado). */
  filtroFormaPago = signal<FiltroFormaPago>('TODAS');

  /** Lo que realmente se ve en pantalla, ya preparado para el template. */
  visibles = computed<ReservaVista[] | null>(() => {
    const rs = this.resultados();
    if (rs === null) return null;

    const mostrarTodas = this.mostrarTodas();
    const formaPago = this.filtroFormaPago();
    const hoy = hoyComoFechaInput();
    return rs
      .filter((r) => mostrarTodas || !ESTADOS_NO_ACCIONABLES.includes(r.estado))
      .filter((r) => formaPago === 'TODAS' || r.formaPago === formaPago)
      .map((r) => aVista(r, hoy));
  });

  /** Suma de pases (excluye extras) de todas las reservas listadas actualmente. */
  totalPasesListados = computed(() =>
    (this.visibles() ?? []).reduce((acc, v) => acc + v.totalPases, 0)
  );

  /** ID de la compra que se está validando (para bloquear sólo esa tarjeta). */
  procesandoId = signal<number | null>(null);
  errorAccion = signal<string | null>(null);

  private readonly enlaceConfiguracion: EnlaceCabecera = {
    texto: 'Configuración',
    ruta: '/configuracion',
  };
  /** El acceso a Configuración sólo se le ofrece a los ADMIN. */
  readonly enlaceCabecera = computed(() =>
    this.sesion.rol() === 'ADMIN' ? this.enlaceConfiguracion : null
  );

  /** Acumula las teclas de un posible escaneo cuando el foco no está en un campo de texto. */
  private bufferEscaneo = '';
  private ultimoKeyEscaneo = 0;
  /** Un lector de código de barras "tipea" cada caracter en pocos milisegundos; una persona no. */
  private static readonly UMBRAL_MS_ENTRE_TECLAS = 150;
  /** Por debajo de esto no lo tratamos como escaneo (evita reaccionar a un Enter suelto). */
  private static readonly LARGO_MINIMO_ESCANEO = 6;

  ngOnInit(): void {
    // Al entrar, mostramos directamente las reservas de hoy: es lo que un
    // boletero necesita ver primero, sin tener que buscar nada.
    this.verReservasDelDia();
  }

  /**
   * Permite escanear el DNI sin haber clickeado antes el campo de búsqueda: mientras el foco
   * no esté en un input/textarea/select (que ya manejan su propio tipeo normalmente), juntamos
   * las teclas que lleguen en ráfaga rápida y, al ver un Enter, lo tratamos como un escaneo.
   */
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
        this.dni.set(escaneo);
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

  /**
   * Un único campo de búsqueda sirve para DNI, código de reserva o nombre. Un escaneo de
   * DNI o un DNI tipeado a mano siguen yendo por el endpoint exacto (rápido, y es el flujo
   * más frecuente); cualquier otra cosa se busca por texto contra el universo completo de
   * reservas, porque no hay forma de saber de antemano si es un nombre o un código.
   */
  buscar(): void {
    const crudo = this.dni().trim();
    if (!crudo || this.buscando()) return;

    // Si lo que llegó es un escaneo PDF417 (lector de DNI), extraemos el DNI puro antes de
    // buscar. Para texto libre (nombre, código de reserva) esto no aplica: se buscaría "0"
    // caracteres si el input no tiene dígitos.
    const query = crudo.includes('@') ? extraerDniDeEscaneo(crudo) : crudo;
    if (query !== crudo) {
      this.dni.set(query);
    }

    if (/^\d{5,}$/.test(query)) {
      this.ejecutarBusqueda(
        'dni',
        this.boleteriaService.buscarPorDni(query),
        'No se pudo consultar el DNI. Revisá la conexión y reintentá.'
      );
      return;
    }

    this.buscarPorTexto(query);
  }

  /** Nombre/apellido o código de reserva: se trae todo y se filtra en el cliente. */
  private buscarPorTexto(query: string): void {
    this.modo.set('texto');
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
    this.resultados.set(null);

    this.boleteriaService.buscarTodas().subscribe({
      next: (todas) => {
        const q = query.toLowerCase();
        const filtradas = todas.filter((r) => {
          const nombreCompleto = `${r.cliente?.nombre ?? ''} ${r.cliente?.apellido ?? ''}`.toLowerCase();
          return (
            r.codigoReserva.toLowerCase().includes(q) ||
            nombreCompleto.includes(q) ||
            (r.cliente?.dni ?? '').includes(q)
          );
        });
        this.resultados.set(filtradas);
        this.buscando.set(false);
      },
      error: (err) => {
        console.error('No se pudo buscar:', err);
        this.errorBusqueda.set('No se pudo buscar. Revisá la conexión y reintentá.');
        this.buscando.set(false);
      },
    });
  }

  /** Trae todas las reservas del día de visita elegido en el selector de fecha. */
  verReservasDelDia(): void {
    const fecha = this.fecha();
    if (!fecha || this.buscando()) return;

    this.dni.set('');
    this.ejecutarBusqueda(
      'dia',
      this.boleteriaService.buscarPorFecha(fecha),
      'No se pudieron traer las reservas del día. Revisá la conexión y reintentá.'
    );
  }

  /**
   * Trae TODAS las reservas sin filtrar por fecha. El parque es laxo con las
   * fechas de visita (una entrada comprada para otro día igual se deja usar),
   * así que esta es la forma de encontrar una reserva cuando no coincide con
   * el día que se está mirando.
   */
  verTodas(): void {
    if (this.buscando()) return;

    this.dni.set('');
    this.ejecutarBusqueda(
      'todas',
      this.boleteriaService.buscarTodas(),
      'No se pudieron traer las reservas. Revisá la conexión y reintentá.'
    );
  }

  /** Las tres búsquedas sólo se diferencian en el modo, el origen y el mensaje de error. */
  private ejecutarBusqueda(modo: ModoBusqueda, origen: Observable<Reserva[]>, mensajeError: string): void {
    this.modo.set(modo);
    this.buscando.set(true);
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
    this.resultados.set(null);

    origen.subscribe({
      next: (res) => {
        this.resultados.set(res);
        this.buscando.set(false);
      },
      error: (err) => {
        console.error(mensajeError, err);
        this.errorBusqueda.set(mensajeError);
        this.buscando.set(false);
      },
    });
  }

  limpiar(): void {
    this.dni.set('');
    this.resultados.set(null);
    this.errorBusqueda.set(null);
    this.errorAccion.set(null);
  }

  /** Compra pagada online (APROBADO) → habilita el ingreso. */
  validarIngreso(reserva: Reserva): void {
    this.ejecutarAccion(reserva, (compraId) => this.boleteriaService.validarIngreso(compraId));
  }

  /** Reserva con pago en efectivo (RESERVADO_EFECTIVO) → cobra y habilita el ingreso. */
  cobrarYValidar(reserva: Reserva): void {
    this.ejecutarAccion(reserva, (compraId) => this.boleteriaService.cobrarEfectivoYValidar(compraId));
  }

  private ejecutarAccion(reserva: Reserva, accion: (compraId: number) => Observable<Reserva>): void {
    this.procesandoId.set(reserva.id);
    this.errorAccion.set(null);

    accion(reserva.id).subscribe({
      next: (actualizada) => {
        // Reemplaza la tarjeta con el estado devuelto por el backend.
        this.resultados.update((rs) =>
          (rs ?? []).map((r) => (r.id === actualizada.id ? actualizada : r))
        );
        this.procesandoId.set(null);
      },
      error: (err) => {
        console.error('Error al validar el ingreso:', err);
        this.errorAccion.set(
          typeof err?.error === 'string'
            ? err.error
            : 'No se pudo completar la validación. Reintentá.'
        );
        this.procesandoId.set(null);
      },
    });
  }

  /** Comparación barata y sin asignaciones, por eso sigue siendo un método y no parte del view model. */
  procesando(reserva: Reserva): boolean {
    return this.procesandoId() === reserva.id;
  }

  // ---------- Menú de acciones (editar contacto / reenviar mail) ----------

  /** ID de la reserva cuyo menú de 3 puntitos está abierto (null = ninguno). */
  menuAbiertoId = signal<number | null>(null);

  toggleMenu(reserva: Reserva, event: MouseEvent): void {
    event.stopPropagation();
    this.menuAbiertoId.update((actual) => (actual === reserva.id ? null : reserva.id));
  }

  @HostListener('document:click')
  cerrarMenu(): void {
    this.menuAbiertoId.set(null);
  }

  /** Reserva que se está editando (null = el modal de edición está cerrado). */
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
        this.resultados.update((rs) => (rs ?? []).map((r) => (r.id === actualizada.id ? actualizada : r)));
        this.guardandoEdit.set(false);
        this.editando.set(null);
      },
      error: (err) => {
        console.error('Error al editar el contacto:', err);
        this.errorEdit.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo guardar los cambios.'
        );
        this.guardandoEdit.set(false);
      },
    });
  }

  /** ID de la reserva cuyo mail se está reenviando (para bloquear sólo ese botón del menú). */
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
        this.avisoReenvio.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo reenviar el mail.'
        );
      },
    });
  }
}
