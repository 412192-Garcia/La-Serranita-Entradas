import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService } from '../services/reporte.service';
import { ReporteResumen } from '../models/reporte';
import { CajaService, Caja, CajaAbierta, CajaCerrada } from '../services/caja.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { FiltroRangoFechas } from '../shared/filtro-rango-fechas/filtro-rango-fechas';
import { Spinner } from '../shared/spinner/spinner';
import { aFechaISO } from '../shared/fecha.util';
import { CierreCajaModal } from './cierre-caja-modal/cierre-caja-modal';
import { ResumenCierre } from './resumen-cierre/resumen-cierre';
import { CajaOperaciones } from './caja-operaciones/caja-operaciones';
import { Modal } from '../shared/modal/modal';
import { PesosPipe } from '../shared/pesos.pipe';
import { TourStep } from '../shared/tour/tour';
import { crearOrdenable } from '../shared/ordenable';
import { ColumnaOrdenable } from '../shared/columna-ordenable/columna-ordenable';

/** "totalRetiros" queda afuera a propósito: no es una columna propia de Caja (se computa con un
 * JOIN + SUM), así que el backend no la admite para ordenar (ver CajaServiceImpl.ordenCajasCerradas). */
type CampoOrdenCajaCerrada =
  | 'usuarioNombre'
  | 'fechaApertura'
  | 'fechaCierre'
  | 'montoInicial'
  | 'montoEsperado'
  | 'montoContado'
  | 'diferencia';

const CAJAS_CERRADAS_POR_PAGINA = 20;

const PASOS_TUTORIAL: TourStep[] = [
  {
    selector: '[data-tour="cajas-abiertas"]',
    titulo: 'Cajas abiertas ahora',
    texto: 'Quién tiene una caja abierta en este momento, y el botón para cerrarla.',
  },
  {
    selector: '[data-tour="filtro-cajas"]',
    titulo: 'Elegí el rango',
    texto: 'Filtra por fecha de cierre. Por defecto muestra los últimos 30 días.',
  },
  {
    selector: '[data-tour="cajas-cerradas"]',
    titulo: 'Cajas cerradas',
    texto: 'Faltantes, sobrantes y ranking de boleteros para el rango elegido. Tocá una fila para ver el detalle completo.',
  },
];

@Component({
  selector: 'app-configuracion-cajas',
  imports: [FormsModule, PesosPipe, DatePipe, CabeceraInterna, FiltroRangoFechas, Spinner, CierreCajaModal, ResumenCierre, Modal, CajaOperaciones, ColumnaOrdenable],
  templateUrl: './cajas.html',
  styleUrls: ['../configuracion/configuracion-shared.css', './cajas.css'],
})
export class ConfiguracionCajas implements OnInit {
  private reporteService = inject(ReporteService);
  private cajaService = inject(CajaService);

  readonly pasosTutorial = PASOS_TUTORIAL;

  // ---------- Cajas abiertas ahora mismo ----------
  cajasAbiertas = signal<CajaAbierta[]>([]);
  cargandoCajasAbiertas = signal(false);

  /** Id de la caja abierta cuya fila está desplegada mostrando app-caja-operaciones; null = ninguna. */
  filaExpandidaAbiertaId = signal<number | null>(null);

  // ---------- Cerrar caja / corregir un cierre ya hecho ----------
  /** Controla sólo la visibilidad del modal (ver [class.oculto]): nunca se destruye, así "Cancelar" no borra lo ya cargado. */
  mostrarCierre = signal(false);
  /** Id de la caja abierta que se está por cerrar. Sólo cambia al apuntar a una caja distinta (no al cancelar). */
  cajaIdCierre = signal<number | null>(null);
  /** Detalle de esa misma caja (vía obtenerDetalle): esperado sigue oculto porque sigue abierta, sólo trae huboVentaDolares/retiros. */
  cajaCierreDetalle = signal<Caja | null>(null);
  /** Si está seteada, el modal corrige esa caja ya cerrada en vez de cerrar una abierta. Sólo cambia al apuntar a una caja distinta. */
  cajaParaCorregir = signal<Caja | null>(null);
  /** Resultado a mostrar justo después de cerrar una caja (no una corrección): el admin necesita ver que el cierre se guardó y con qué números, no volver directo a la lista sin feedback. */
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
  totalesCerradas = signal({ retiros: 0, faltantes: 0, sobrantes: 0 });

  /** Orden de "Cajas cerradas": por defecto la más reciente primero (mismo criterio que ya trae el backend). */
  private ordenCajas = crearOrdenable<CampoOrdenCajaCerrada>('fechaCierre');
  ordenarPorCajas = this.ordenCajas.ordenarPor;
  estadoOrdenCajas = this.ordenCajas.estadoOrden;

  private readonly hoy = new Date();
  desde = signal(aFechaISO(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate() - 29)));
  hasta = signal(aFechaISO(this.hoy));

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

  /** Vacío = todos los boleteros. */
  filtroBoletero = signal<string>('');

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
        this.desde(),
        this.hasta(),
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
          this.totalesCerradas.set({ retiros: r.totalRetiros, faltantes: r.totalFaltantes, sobrantes: r.totalSobrantes });
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
      },
      error: (err) => {
        console.error('Error al cargar las cajas abiertas:', err);
        this.cargandoCajasAbiertas.set(false);
      },
    });
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
    this.cajaParaCorregir.set(null);
    this.cajaIdCierre.set(cajaId);
    this.mostrarCierre.set(true);
    this.cajaService.obtenerDetalle(cajaId).subscribe({
      next: (c) => this.cajaCierreDetalle.set(c),
      error: (err) => console.error('Error al cargar el detalle de la caja a cerrar:', err),
    });
  }

  /** Abre el modal para corregir un cierre ya hecho (precargado con lo que ya tiene esa caja). */
  iniciarCorreccion(caja: Caja): void {
    this.cajaIdCierre.set(null);
    this.cajaCierreDetalle.set(null);
    this.cajaParaCorregir.set(caja);
    this.mostrarCierre.set(true);
  }

  /** Se cerró o corrigió con éxito: refresca las listas afectadas. */
  onCajaCerrada(c: Caja): void {
    const eraCorreccion = this.cajaParaCorregir() !== null;
    this.mostrarCierre.set(false);
    this.cajaIdCierre.set(null);
    this.cajaCierreDetalle.set(null);
    this.cajaParaCorregir.set(null);
    this.cargarCajasAbiertas();
    this.cargar();
    this.cargarCajasCerradas();
    if (eraCorreccion) {
      if (this.filaExpandidaId() === c.id) {
        this.cajaDetalle.set(c);
      }
    } else {
      // Recién se cerró (no una corrección): mostrar el resultado, si no el modal
      // simplemente desaparece y el admin no tiene forma de saber que se guardó.
      this.cajaRecienCerrada.set(c);
    }
  }

  /** Se agregó un retiro/aporte desde dentro del modal de cierre, sin llegar a cerrar: refresca sólo esa caja, sin tocar el resto del formulario. */
  onCajaActualizadaCierre(c: Caja): void {
    if (this.cajaParaCorregir()) {
      this.cajaParaCorregir.set(c);
    } else {
      this.cajaCierreDetalle.set(c);
    }
  }

  /** Refresca el reporte agregado (ranking de boleteros + chips de filtro) y, con el mismo rango
   * de fechas, el listado paginado de Cajas cerradas — son dos pedidos separados a propósito
   * (ver cargarCajasCerradas): el ranking necesita TODAS las cajas del rango para sumar por
   * boletero, pero el listado no necesita traerlas todas para mostrar sólo una página. */
  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.reporteService.getResumen(this.desde(), this.hasta()).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las cajas:', err);
        this.error.set('No se pudo cargar la información de cajas.');
        this.cargando.set(false);
      },
    });
    this.paginaCerradas.set(0);
    this.cargarCajasCerradas();
  }

  /** Para colorear la fila de la caja en la tabla: rojo si faltó plata, verde si sobró o cerró justo. */
  claseDiferencia(caja: CajaCerrada): string {
    if (caja.diferencia < 0) return 'diferencia-faltante';
    if (caja.diferencia > 0) return 'diferencia-sobrante';
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
   * la caja recalculada. Refresca el detalle desplegado y la fila del listado (cambió la diferencia). */
  onCajaAjustada(c: Caja): void {
    if (this.filaExpandidaId() === c.id) {
      this.cajaDetalle.set(c);
    }
    this.cargarCajasCerradas();
  }

  /** El admin deshabilitó la caja: colapsa la fila y recarga todo (el backend ya la sacó de la
   * tabla, los KPIs, el ranking y los chips — cargar() re-pide getResumen + cargarCajasCerradas). */
  onCajaDeshabilitada(_c: Caja): void {
    this.filaExpandidaId.set(null);
    this.cajaDetalle.set(null);
    this.errorDetalle.set(null);
    this.cargar();
  }

  /** Nombres únicos de boleteros con al menos una caja cerrada en el rango, para el filtro — sigue
   * viniendo del reporte agregado (trae todas las cajas del rango, sin paginar) porque necesita
   * verlas todas para no perderse ningún nombre; el listado paginado en sí no sirve para esto. */
  boleterosDisponibles(r: ReporteResumen): string[] {
    return [...new Set(r.cajas.map((c) => c.usuarioNombre))].sort();
  }

  /** Desempeño acumulado por boletero: turnos trabajados, efectivo vendido, retiros y diferencia total. */
  rankingBoleteros(r: ReporteResumen): { nombre: string; turnos: number; efectivoVendido: number; retiros: number; diferencia: number }[] {
    const porBoletero = new Map<string, { turnos: number; efectivoVendido: number; retiros: number; diferencia: number }>();
    for (const c of r.cajas) {
      const acumulado = porBoletero.get(c.usuarioNombre) ?? { turnos: 0, efectivoVendido: 0, retiros: 0, diferencia: 0 };
      acumulado.turnos += 1;
      acumulado.efectivoVendido += c.montoEsperado - c.montoInicial + c.totalRetiros;
      acumulado.retiros += c.totalRetiros;
      acumulado.diferencia += c.diferencia;
      porBoletero.set(c.usuarioNombre, acumulado);
    }
    return [...porBoletero.entries()]
      .map(([nombre, datos]) => ({ nombre, ...datos }))
      .sort((a, b) => b.efectivoVendido - a.efectivoVendido);
  }
}
