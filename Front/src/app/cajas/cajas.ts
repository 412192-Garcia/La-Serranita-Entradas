import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, CajaAbierta, CajaCerrada } from '../services/caja.service';
import { NotificacionService } from '../services/notificacion.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { Spinner } from '../shared/spinner/spinner';
import { CierreCajaModal } from './cierre-caja-modal/cierre-caja-modal';
import { ResumenCierre } from './resumen-cierre/resumen-cierre';
import { CajaOperaciones } from './caja-operaciones/caja-operaciones';
import { Modal } from '../shared/modal/modal';
import { PesosPipe } from '../shared/pesos.pipe';
import { TourStep } from '../shared/tour/tour';
import { crearOrdenable } from '../shared/ordenable';
import { ColumnaOrdenable } from '../shared/columna-ordenable/columna-ordenable';

/** Sólo las columnas que la tabla deja ordenar. "totalRetiros" y "Dif. posnet" quedan afuera
 * a propósito: no son columnas propias de Caja (se computan con JOIN + SUM / a mano), así que el
 * backend no las admite para ordenar (ver CajaServiceImpl.ordenCajasCerradas). */
type CampoOrdenCajaCerrada = 'usuarioNombre' | 'fechaApertura' | 'montoEsperado' | 'diferencia';

const CAJAS_CERRADAS_POR_PAGINA = 20;

@Component({
  selector: 'app-configuracion-cajas',
  imports: [FormsModule, PesosPipe, DatePipe, CabeceraInterna, Spinner, CierreCajaModal, ResumenCierre, Modal, CajaOperaciones, ColumnaOrdenable],
  templateUrl: './cajas.html',
  styleUrls: ['../configuracion/configuracion-shared.css', './cajas.css'],
})
export class ConfiguracionCajas implements OnInit {
  private cajaService = inject(CajaService);
  private notificacionService = inject(NotificacionService);

  /** El resumen de la caja cerrada que está desplegada (el tutorial lo usa para abrir "Corregir caja"). */
  private resumenCierre = viewChild(ResumenCierre);

  // ---------- Tutorial ----------
  // Cajas esconde lo importante detrás de clics: el detalle de una caja abierta, el modal de cierre,
  // el detalle de una cerrada y el modo "Corregir caja". Los pasos abren cada cosa por su cuenta
  // (ver TourStep.antes) y al terminar se cierra sólo lo que el tutorial abrió: lo que el admin ya
  // tenía desplegado no se toca, así una corrección a medias no se pierde.

  private tutorial = { operaciones: false, detalle: false, cierre: false, revision: false };

  alIniciarTutorial(): void {
    this.tutorial = { operaciones: false, detalle: false, cierre: false, revision: false };
  }

  alCerrarTutorial(): void {
    this.tutorialCerrarCierre();
    this.tutorialSalirRevision();
    if (this.tutorial.operaciones) this.filaExpandidaAbiertaId.set(null);
    if (this.tutorial.detalle) {
      this.filaExpandidaId.set(null);
      this.cajaDetalle.set(null);
      this.errorDetalle.set(null);
    }
    this.tutorial = { operaciones: false, detalle: false, cierre: false, revision: false };
  }

  /** Despliega la primera caja abierta (si el admin no tiene ya una desplegada). */
  private tutorialMostrarCajaAbierta(): void {
    this.tutorialCerrarCierre();
    if (this.filaExpandidaAbiertaId() === null && this.cajasAbiertas().length > 0) {
      this.toggleOperaciones(this.cajasAbiertas()[0]);
      this.tutorial.operaciones = true;
    }
  }

  /** Abre el modal de cierre de la primera caja abierta, sin cerrar nada: cancelarlo no borra lo cargado. */
  private tutorialAbrirCierre(): void {
    if (this.mostrarCierre() || this.cajasAbiertas().length === 0) return;
    this.iniciarCierre(this.cajasAbiertas()[0].id);
    this.tutorial.cierre = true;
  }

  private tutorialCerrarCierre(): void {
    if (!this.tutorial.cierre) return;
    this.mostrarCierre.set(false);
    this.tutorial.cierre = false;
  }

  /** Despliega la primera caja cerrada (si no hay ya una desplegada) y sale del modo corrección que abrió el tutorial. */
  private tutorialMostrarCajaCerrada(): void {
    this.tutorialCerrarCierre();
    this.tutorialSalirRevision();
    this.tutorialDesplegarCajaCerrada();
  }

  private tutorialDesplegarCajaCerrada(): void {
    if (this.filaExpandidaId() === null && this.cajasCerradas().length > 0) {
      this.toggleDetalle(this.cajasCerradas()[0].id);
      this.tutorial.detalle = true;
    }
  }

  private tutorialAbrirRevision(): void {
    this.tutorialCerrarCierre();
    this.tutorialDesplegarCajaCerrada();
    // El detalle se carga del backend: el resumen recién existe cuando llega.
    this.cuandoHayResumen((resumen) => {
      if (resumen.modoRevision()) return;
      resumen.abrirRevision();
      this.tutorial.revision = true;
    });
  }

  private tutorialSalirRevision(): void {
    if (!this.tutorial.revision) return;
    this.resumenCierre()?.cerrarRevision();
    this.tutorial.revision = false;
  }

  private cuandoHayResumen(accion: (resumen: ResumenCierre) => void, intentos = 40): void {
    const resumen = this.resumenCierre();
    if (resumen) accion(resumen);
    else if (intentos > 0) setTimeout(() => this.cuandoHayResumen(accion, intentos - 1), 50);
  }

  /**
   * Cuatro bloques: cajas abiertas, cerrar una caja, cajas cerradas (con su detalle) y
   * "Corregir caja". El ranking de boleteros se mudó a Reportes. Los pasos con "alternativo" caen a la tarjeta de la sección si no hay datos
   * (ej. ninguna caja abierta).
   */
  readonly pasosTutorial: TourStep[] = [
    {
      selector: '[data-tour="cajas-abiertas"]',
      titulo: 'Cajas abiertas ahora',
      texto: 'Los turnos en curso: quién abrió, con cuánto arrancó, cuánto lleva vendido y cuántas entradas. Una caja marcada "Atrasada" quedó abierta de un día anterior y hace falta cerrarla. Tocá una fila para ver su detalle.',
      antes: () => this.tutorialCerrarCierre(),
    },
    {
      selector: '[data-tour="kpis-caja-abierta"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Detalle de una caja abierta',
      texto: 'Abrimos la primera. Arriba ves lo vendido hasta ahora, las entradas vendidas y cuántas le quedan en el talonario; debajo, lo vendido por forma de pago.',
      antes: () => this.tutorialMostrarCajaAbierta(),
    },
    {
      selector: '[data-tour="acciones-caja-abierta"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Corregir mientras el boletero trabaja',
      texto: '"+ Agregar venta" carga una que faltó; "Retiro / Aporte" y "Entradas físicas" registran movimientos. Más abajo, el historial lista cada operación y las ventas traen "Editar" y "Cancelar", para arreglar un error sin esperar al cierre.',
      antes: () => this.tutorialMostrarCajaAbierta(),
    },
    {
      selector: '[data-tour="boton-cerrar-caja"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Cerrar una caja',
      texto: 'Este botón cierra el turno de ese boletero.',
      antes: () => this.tutorialCerrarCierre(),
    },
    {
      selector: 'app-cierre-caja-modal [data-tour="conteo-efectivo"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Cerrar: el efectivo',
      texto: 'Contá los billetes por denominación y cargá el cambio. Abajo se suma el total contado: contra ese número se calcula la diferencia de efectivo.',
      antes: () => this.tutorialAbrirCierre(),
    },
    {
      selector: 'app-cierre-caja-modal [data-tour="conteo-posnet"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Cerrar: el posnet',
      texto: 'Cargá lo que dio el cierre del posnet. "Juntos" si tarjeta y QR salieron en un solo comprobante, "Por separado" si no. Podés sumar varios cierres, cada uno con su nota.',
      antes: () => this.tutorialAbrirCierre(),
    },
    {
      selector: 'app-cierre-caja-modal [data-tour="conteo-entradas"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Cerrar: talonario y dólares',
      texto: 'Indicá cuántas entradas quedan en el talonario: se compara con lo esperado (las del inicio, más lo que ingresó, menos las entregadas). Si hubo ventas en dólares, aparece también el campo para contarlos.',
      antes: () => this.tutorialAbrirCierre(),
    },
    {
      selector: 'app-cierre-caja-modal [data-tour="cierre-movimientos"]',
      alternativo: '[data-tour="cajas-abiertas"]',
      titulo: 'Cerrar: movimientos y confirmar',
      texto: 'Acá agregás un retiro o aporte de último momento sin salir del cierre. Cuando todo está cargado, "Confirmar cierre" guarda el turno: recién entonces se ven el esperado y las diferencias. Cancelar no borra lo que ya cargaste.',
      antes: () => this.tutorialAbrirCierre(),
    },
    {
      selector: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Cajas cerradas',
      texto: 'Un turno por fila, en páginas de a 20. La columna "Día" es el día al que corresponde la caja y el más reciente va primero. Tocá el título de una columna para ordenar y, si hay más de un boletero, su nombre arriba para ver solo el suyo. En rojo lo que faltó, en verde lo que sobró y en gris lo que cerró justo.',
      antes: () => this.tutorialCerrarCierre(),
    },
    {
      selector: '[data-tour="resumen-kpis"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Detalle de una caja cerrada',
      texto: 'Tocando una fila se despliega su detalle; abrimos la primera. Arriba, el total vendido y las entradas por tipo. Debajo, por forma de pago: lo esperado, lo contado y la diferencia, con "Ver billetes" para el desglose.',
      antes: () => this.tutorialMostrarCajaCerrada(),
    },
    {
      selector: '[data-tour="resumen-talonario"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'El talonario',
      texto: '"Restantes" es lo que el boletero contó al cerrar; "esperadas", lo que debería quedar según las ventas. Si difieren, dice cuántas faltan o sobran.',
      antes: () => this.tutorialMostrarCajaCerrada(),
    },
    {
      selector: '[data-tour="resumen-ventas"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Resumen de ventas',
      texto: 'Cada forma de pago con sus ventas, agrupadas por tipo de entrada y descuento; los artículos varios van aparte. Al final está el historial de operaciones del turno, con retiros, aportes e ingresos de entradas.',
      antes: () => this.tutorialMostrarCajaCerrada(),
    },
    {
      selector: '[data-tour="acciones-resumen"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Corregir o borrar',
      texto: '"Corregir caja" abre el modo de corrección, que vemos ahora al tocar Siguiente. "Borrar caja" la saca de los listados y del reporte y no se puede deshacer desde la app: pide escribir una palabra para confirmar.',
      antes: () => this.tutorialMostrarCajaCerrada(),
    },
    {
      selector: '[data-tour="revision-conteo"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Corregir: el recuento',
      texto: 'En el modo de corrección, a la izquierda queda el recuento (billetes, posnet y talonario) ya cargado con lo que se contó al cerrar: cambiá lo que estaba mal.',
      antes: () => this.tutorialAbrirRevision(),
    },
    {
      selector: '[data-tour="revision-totales"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Corregir: cómo quedaría el cierre',
      texto: 'A la derecha ves el esperado y el contado por forma de pago, con la diferencia que resultaría. Se actualiza en vivo con cada cambio. Justo debajo está el talonario, con las entradas restantes, las esperadas y las vendidas (pagas).',
      antes: () => this.tutorialAbrirRevision(),
    },
    {
      selector: '[data-tour="revision-matriz"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Corregir: mover ventas',
      texto: 'Cada celda es la cantidad de ventas de ese tipo y tamaño de grupo en esa forma de pago. "−" la saca y "+" la ubica en otra: sirve cuando se cobró con tarjeta pero se anotó en efectivo. Más abajo podés agregar una venta que faltó, o sumar o restar un monto suelto con una nota.',
      antes: () => this.tutorialAbrirRevision(),
    },
    {
      selector: '[data-tour="revision-guardar"]',
      alternativo: '[data-tour="tabla-cajas-cerradas"]',
      titulo: 'Corregir: guardar',
      texto: '"Guardar corrección" aplica todo junto. Queda registrado en "Ajustes manuales" con quién lo hizo y cuándo, y cada ajuste tiene su "Deshacer". "Cancelar" descarta los cambios sin guardar.',
      antes: () => this.tutorialAbrirRevision(),
    },
  ];

  // ---------- Cajas abiertas ahora mismo ----------
  cajasAbiertas = signal<CajaAbierta[]>([]);
  cargandoCajasAbiertas = signal(false);

  /** Id de la caja abierta cuya fila está desplegada mostrando app-caja-operaciones; null = ninguna. */
  filaExpandidaAbiertaId = signal<number | null>(null);

  // ---------- Cerrar caja ----------
  // Corregir un cierre ya hecho NO pasa por acá: lo hace "Corregir caja" en app-resumen-cierre.
  /** Controla sólo la visibilidad del modal (ver [class.oculto]): nunca se destruye, así "Cancelar" no borra lo ya cargado. */
  mostrarCierre = signal(false);
  /** Id de la caja abierta que se está por cerrar. Sólo cambia al apuntar a una caja distinta (no al cancelar). */
  cajaIdCierre = signal<number | null>(null);
  /** Detalle de esa misma caja (vía obtenerDetalle): esperado sigue oculto porque sigue abierta, sólo trae huboVentaDolares/retiros. */
  cajaCierreDetalle = signal<Caja | null>(null);
  /** Resultado a mostrar justo después de cerrar una caja: el admin necesita ver que el cierre se guardó y con qué números, no volver directo a la lista sin feedback. */
  cajaRecienCerrada = signal<Caja | null>(null);

  /** Id de la fila desplegada (null = ninguna); el detalle de sólo lectura de esa caja se carga debajo. */
  filaExpandidaId = signal<number | null>(null);
  cajaDetalle = signal<Caja | null>(null);
  cargandoDetalle = signal(false);
  errorDetalle = signal<string | null>(null);

  // ---------- Cajas cerradas (paginado en el backend) ----------
  cajasCerradas = signal<CajaCerrada[]>([]);
  cargandoCerradas = signal(false);
  errorCerradas = signal<string | null>(null);
  paginaCerradas = signal(0);
  totalPaginasCerradas = signal(1);

  /** Orden de "Cajas cerradas": por defecto el día más reciente primero. El día de una caja es el de
   * su apertura (fechaApertura): una caja atrasada la cierra un admin días después, pero sus
   * ventas son del día en que se abrió. */
  private ordenCajas = crearOrdenable<CampoOrdenCajaCerrada>('fechaApertura');
  ordenarPorCajas = this.ordenCajas.ordenarPor;
  estadoOrdenCajas = this.ordenCajas.estadoOrden;

  /** Vacío = todos los boleteros. */
  filtroBoletero = signal<string>('');

  /** Boleteros con al menos una caja cerrada, para los chips de filtro (ver cargarBoleteros). */
  boleterosDisponibles = signal<string[]>([]);

  ngOnInit(): void {
    // Arranca DESC (más reciente primero): crearOrdenable siempre empieza en ASC, y acá
    // queremos la misma vista que ya trae el backend por defecto.
    this.ordenCajas.direccionOrden.set('DESC');
    this.cargar();
    this.cargarCajasAbiertas();
  }

  /** Tocar una columna reordena (o invierte si ya se estaba ordenando por ella) y vuelve a la página 1: un orden nuevo con la página vieja mostraría filas salteadas. */
  ordenarColumnaCajas(campo: CampoOrdenCajaCerrada): void {
    this.ordenCajas.ordenarColumna(campo);
    this.paginaCerradas.set(0);
    this.cargarCajasCerradas();
  }

  /** Chip de boletero: filtra el listado paginado y vuelve a la página 1. */
  elegirFiltroBoletero(nombre: string): void {
    this.filtroBoletero.set(nombre);
    this.paginaCerradas.set(0);
    this.cargarCajasCerradas();
  }

  paginaCerradasAnterior(): void {
    if (this.paginaCerradas() === 0) return;
    this.paginaCerradas.update((p) => p - 1);
    this.cargarCajasCerradas();
  }

  paginaCerradasSiguiente(): void {
    if (this.paginaCerradas() + 1 >= this.totalPaginasCerradas()) return;
    this.paginaCerradas.update((p) => p + 1);
    this.cargarCajasCerradas();
  }

  private cargarCajasCerradas(): void {
    this.cargandoCerradas.set(true);
    this.errorCerradas.set(null);
    const filtro = this.filtroBoletero() || null;
    this.cajaService
      .obtenerCajasCerradas(
        filtro,
        this.ordenarPorCajas(),
        this.ordenCajas.direccionOrden(),
        this.paginaCerradas(),
        CAJAS_CERRADAS_POR_PAGINA
      )
      .subscribe({
        next: (r) => {
          this.cajasCerradas.set(r.content);
          this.totalPaginasCerradas.set(Math.max(1, r.totalPages));
          this.cargandoCerradas.set(false);
        },
        error: (err) => {
          console.error('Error al cargar las cajas cerradas:', err);
          this.errorCerradas.set('No se pudo cargar el listado de cajas cerradas.');
          this.cargandoCerradas.set(false);
        },
      });
  }

  cargarCajasAbiertas(): void {
    this.cargandoCajasAbiertas.set(true);
    this.cajaService.obtenerCajasAbiertas().subscribe({
      next: (cs) => {
        this.cajasAbiertas.set(cs);
        this.cargandoCajasAbiertas.set(false);
        // Con sólo mostrar la lista ya se "vieron": apaga el aviso CAJA_ATRASADA para este
        // admin (otros admins que no entraron a esta pantalla lo siguen viendo prendido).
        const idsAtrasadas = cs.filter((c) => this.esAtrasada(c)).map((c) => c.id);
        if (idsAtrasadas.length > 0) {
          this.notificacionService.marcarVistas('CAJA_ATRASADA', idsAtrasadas).subscribe({
            error: (err) => console.error('Error al marcar como vistas las cajas atrasadas:', err),
          });
        }
      },
      error: (err) => {
        console.error('Error al cargar las cajas abiertas:', err);
        this.cargandoCajasAbiertas.set(false);
      },
    });
  }

  /** Caja sin cerrar cuya apertura fue un día distinto a hoy: quedó pendiente de que un admin
   * la cierre (ver "caja operativa" en CajaServiceImpl del backend). */
  esAtrasada(c: CajaAbierta): boolean {
    const apertura = new Date(c.fechaApertura);
    const hoy = new Date();
    return (
      apertura.getFullYear() !== hoy.getFullYear() ||
      apertura.getMonth() !== hoy.getMonth() ||
      apertura.getDate() !== hoy.getDate()
    );
  }

  /** Despliega el detalle (ventas/retiros/ingresos) de una caja abierta, para revisar o corregir un error mientras el boletero sigue trabajando — mismo patrón que toggleDetalle en "Cajas cerradas". */
  toggleOperaciones(caja: CajaAbierta): void {
    this.filaExpandidaAbiertaId.set(this.filaExpandidaAbiertaId() === caja.id ? null : caja.id);
  }

  /** Se canceló o editó una venta desde el detalle: refresca los totales de "Cajas abiertas ahora". */
  onCambiosOperaciones(): void {
    this.cargarCajasAbiertas();
  }

  /** Abre el modal para cerrar esa caja abierta (busca su detalle, sin exponer lo esperado: sigue abierta). */
  iniciarCierre(cajaId: number): void {
    this.cajaIdCierre.set(cajaId);
    this.mostrarCierre.set(true);
    this.cajaService.obtenerDetalle(cajaId).subscribe({
      next: (c) => this.cajaCierreDetalle.set(c),
      error: (err) => console.error('Error al cargar el detalle de la caja a cerrar:', err),
    });
  }

  /** Se cerró una caja con éxito: refresca las listas afectadas y muestra el resultado. */
  onCajaCerrada(c: Caja): void {
    this.mostrarCierre.set(false);
    this.cajaIdCierre.set(null);
    this.cajaCierreDetalle.set(null);
    this.cargarCajasAbiertas();
    this.cargar();
    // El modal simplemente desaparece: sin este resultado el admin no tiene forma de saber que se guardó.
    this.cajaRecienCerrada.set(c);
  }

  /** Se agregó un retiro/aporte desde dentro del modal de cierre, sin llegar a cerrar: refresca sólo esa caja, sin tocar el resto del formulario. */
  onCajaActualizadaCierre(c: Caja): void {
    this.cajaCierreDetalle.set(c);
  }

  /** Vuelve a la primera página del listado de cajas cerradas y refresca los chips de boletero. */
  cargar(): void {
    this.cargarBoleteros();
    this.paginaCerradas.set(0);
    this.cargarCajasCerradas();
  }

  /** Los nombres para los chips vienen de un pedido propio: el listado paginado no los trae todos. */
  private cargarBoleteros(): void {
    this.cajaService.obtenerBoleterosConCajasCerradas().subscribe({
      next: (nombres) => this.boleterosDisponibles.set(nombres),
      error: (err) => console.error('Error al cargar los boleteros con cajas cerradas:', err),
    });
  }

  /** Rojo si faltó plata, verde si sobró o cerró justo. Sirve para cualquier diferencia (efectivo o posnet). */
  claseDiferencia(valor: number): string {
    if (valor < 0) return 'diferencia-faltante';
    if (valor > 0) return 'diferencia-sobrante';
    return 'diferencia-exacta';
  }

  /** Despliega el detalle de esa fila, o lo cierra si ya estaba abierta. */
  toggleDetalle(cajaId: number): void {
    if (this.filaExpandidaId() === cajaId) {
      this.filaExpandidaId.set(null);
      this.cajaDetalle.set(null);
      this.errorDetalle.set(null);
      return;
    }
    this.filaExpandidaId.set(cajaId);
    this.cargandoDetalle.set(true);
    this.errorDetalle.set(null);
    this.cajaDetalle.set(null);
    this.cajaService.obtenerDetalle(cajaId).subscribe({
      next: (c) => {
        this.cajaDetalle.set(c);
        this.cargandoDetalle.set(false);
      },
      error: (err) => {
        console.error('Error al cargar el detalle de la caja:', err);
        this.errorDetalle.set('No se pudo cargar el detalle de esta caja.');
        this.cargandoDetalle.set(false);
      },
    });
  }

  /** El admin aplicó o deshizo un ajuste de formas de pago desde el resumen: la respuesta ya trae
   * la caja recalculada. Refresca el detalle desplegado y la fila del listado (las diferencias
   * cambian con el ajuste). */
  onCajaAjustada(c: Caja): void {
    if (this.filaExpandidaId() === c.id) {
      this.cajaDetalle.set(c);
    }
    this.cargarCajasCerradas();
  }

  /** El admin deshabilitó la caja: colapsa la fila y recarga (el backend ya la sacó del listado
   * y de los chips si era la única de ese boletero). */
  onCajaDeshabilitada(_c: Caja): void {
    this.filaExpandidaId.set(null);
    this.cajaDetalle.set(null);
    this.errorDetalle.set(null);
    this.cargar();
  }
}
