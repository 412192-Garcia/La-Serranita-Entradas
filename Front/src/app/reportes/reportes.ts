import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { ReporteService } from '../services/reporte.service';
import { ComprasPorEstado, RecaudacionPorFormaPago, ReporteResumen, VentasPorOrigen } from '../models/reporte';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { FiltroRangoFechas } from '../shared/filtro-rango-fechas/filtro-rango-fechas';
import { aFechaISO, restarUnAnio } from '../shared/fecha.util';
import { PesosPipe } from '../shared/pesos.pipe';
import { TourStep } from '../shared/tour/tour';

/** Cada pestaña (Resumen/Comparación) tiene su propio recorrido, igual que en Configuración:
 * son bloques de contenido distintos y no tiene sentido un único tutorial para las dos. */
const PASOS_RESUMEN: TourStep[] = [
  {
    selector: '[data-tour="filtro-fechas-reportes"]',
    titulo: 'Elegí el rango',
    texto: 'Elegí el rango de fechas y tocá Aplicar para traer los números de ese período.',
  },
  {
    selector: '[data-tour="kpis-reportes"]',
    titulo: 'Números principales',
    texto: 'Recaudación total, anticipada, en puerta y personas que entraron, para el rango elegido.',
  },
  {
    selector: '[data-tour="graficos-reportes"]',
    titulo: 'Gráficos y desgloses',
    texto: 'Más abajo hay gráficos con el detalle: afluencia diaria, desglose por tipo, forma de pago, horarios y más.',
  },
];

const PASOS_COMPARACION: TourStep[] = [
  {
    selector: '[data-tour="comparar-periodos"]',
    titulo: 'Comparar períodos',
    texto: 'Agregá los rangos que quieras comparar (por ejemplo años o temporadas distintas), ponele una etiqueta a cada uno y tocá Comparar. Los resultados y gráficos aparecen debajo.',
  },
];

/** Un período elegido a mano para la pestaña "Comparación": no se asume "un año antes" a
 * ciegas porque fechas móviles (Semana Santa, feriados largos) no caen el mismo día todos los
 * años — el admin define desde/hasta libremente, y puede agregar tantos como quiera. */
interface PeriodoComparacion {
  etiqueta: string;
  desde: string;
  hasta: string;
  resumen: ReporteResumen | null;
}

Chart.register(...registerables);

/** Colores por estado, coherentes con los badges de Boletería (verde = ok, ámbar = pendiente, gris = inactivo). */
const COLOR_POR_ESTADO: Record<ComprasPorEstado['estado'], string> = {
  APROBADO: '#39a935',
  USADO: '#1f6b1c',
  VENDIDO_EN_PUERTA: '#4a7fc9',
  RESERVADO_EFECTIVO: '#e0a72e',
  PENDIENTE_PAGO: '#9aa0a6',
  CANCELADO: '#c94f4f',
  REEMBOLSADA: '#8a3a3a',
};

/** Un color por forma de pago: si no fueran fijos, agregar una nueva correría toda la paleta. */
const COLOR_POR_FORMA_PAGO: Record<RecaudacionPorFormaPago['formaPago'], string> = {
  MERCADO_PAGO: '#39a935',
  EFECTIVO_BOLETERIA: '#e0a72e',
  TARJETA: '#4a7fc9',
  MERCADO_PAGO_QR: '#7a5bc9',
  RESERVA_ADMIN: '#c96bb0',
};

const ETIQUETA_POR_ESTADO: Record<ComprasPorEstado['estado'], string> = {
  APROBADO: 'Pagada online',
  USADO: 'Ya utilizada',
  VENDIDO_EN_PUERTA: 'Vendida en puerta',
  RESERVADO_EFECTIVO: 'A cobrar en caja',
  PENDIENTE_PAGO: 'Pago pendiente',
  CANCELADO: 'Cancelada',
  REEMBOLSADA: 'Reembolsada',
};

/** Boletería (venta de puerta) en azul, para que combine con su badge en Boletería/POS. */
const COLOR_POR_ORIGEN: Record<VentasPorOrigen['origen'], string> = {
  BOLETERIA: '#4a7fc9',
  ANTICIPADA: '#39a935',
};

/** Los tipos de entrada no son un set fijo (el admin los crea en Configuración → Catálogo), así
 * que el gráfico de comparación por tipo los colorea ciclando esta paleta en vez de un mapeo
 * fijo por nombre. */
const PALETA_TIPOS = ['#39a935', '#4a7fc9', '#e0a72e', '#7a5bc9', '#c94f4f', '#1f6b1c', '#8a3a3a', '#9aa0a6'];

@Component({
  selector: 'app-configuracion-reportes',
  imports: [FormsModule, PesosPipe, DecimalPipe, CabeceraInterna, FiltroRangoFechas],
  templateUrl: './reportes.html',
  styleUrls: ['../configuracion/configuracion-shared.css', './reportes.css'],
})
export class ConfiguracionReportes implements OnInit, OnDestroy {
  private reporteService = inject(ReporteService);

  /** Queries de señal (no `@ViewChild` de decorador): todos estos canvases viven dentro de
   * bloques `@if` (pestañas Resumen / Comparación) que Angular destruye y recrea al cambiar de
   * pestaña. Leer una `viewChild()` dentro de un `effect()` la registra como dependencia
   * reactiva, así que el efecto se vuelve a ejecutar solo cuando el canvas reaparece — con
   * `@ViewChild` clásico eso no pasaba (la query no forma parte del grafo reactivo) y volver a
   * una pestaña ya cargada dejaba los gráficos en blanco. */
  private afluenciaCanvas = viewChild<ElementRef<HTMLCanvasElement>>('afluenciaCanvas');
  private desgloseCanvas = viewChild<ElementRef<HTMLCanvasElement>>('desgloseCanvas');
  private formaPagoCanvas = viewChild<ElementRef<HTMLCanvasElement>>('formaPagoCanvas');
  private estadoCanvas = viewChild<ElementRef<HTMLCanvasElement>>('estadoCanvas');
  private extrasCanvas = viewChild<ElementRef<HTMLCanvasElement>>('extrasCanvas');
  private horaCanvas = viewChild<ElementRef<HTMLCanvasElement>>('horaCanvas');
  private origenCanvas = viewChild<ElementRef<HTMLCanvasElement>>('origenCanvas');
  private articulosVariosCanvas = viewChild<ElementRef<HTMLCanvasElement>>('articulosVariosCanvas');
  private comparacionRecaudacionCanvas = viewChild<ElementRef<HTMLCanvasElement>>('comparacionRecaudacionCanvas');
  private comparacionPersonasCanvas = viewChild<ElementRef<HTMLCanvasElement>>('comparacionPersonasCanvas');
  private comparacionTiposCanvas = viewChild<ElementRef<HTMLCanvasElement>>('comparacionTiposCanvas');
  private afluenciaChart: Chart | null = null;
  private desgloseChart: Chart | null = null;
  private formaPagoChart: Chart | null = null;
  private estadoChart: Chart | null = null;
  private extrasChart: Chart | null = null;
  private horaChart: Chart | null = null;
  private origenChart: Chart | null = null;
  private articulosVariosChart: Chart | null = null;
  private comparacionRecaudacionChart: Chart | null = null;
  private comparacionPersonasChart: Chart | null = null;
  private comparacionTiposChart: Chart | null = null;

  private readonly hoy = new Date();
  desde = signal(aFechaISO(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate() - 29)));
  hasta = signal(aFechaISO(this.hoy));

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

  vista = signal<'resumen' | 'comparacion'>('resumen');
  pasosTutorial = computed(() => (this.vista() === 'resumen' ? PASOS_RESUMEN : PASOS_COMPARACION));

  /** Arranca con el rango principal y ese mismo rango un año antes, como punto de partida
   * cómodo: el admin corrige las fechas de cualquier fila (o agrega más) antes de comparar. */
  periodos = signal<PeriodoComparacion[]>([
    { etiqueta: 'Período actual', desde: this.desde(), hasta: this.hasta(), resumen: null },
    { etiqueta: 'Período anterior', desde: restarUnAnio(this.desde()), hasta: restarUnAnio(this.hasta()), resumen: null },
  ]);
  comparando = signal(false);
  huboComparacion = computed(() => this.periodos().some((p) => p.resumen !== null));

  constructor() {
    // effect (no el callback del http.subscribe) porque los canvases de ambas pestañas se
    // destruyen y recrean cada vez que @if (vista() === ...) cambia: si sólo se renderizara una
    // vez al llegar la respuesta HTTP, volver a una pestaña ya cargada mostraría el canvas vacío.
    effect(() => {
      if (this.vista() === 'resumen') {
        const r = this.resumen();
        if (r) this.renderGraficos(r);
      }
    });

    effect(() => {
      if (this.huboComparacion()) {
        this.renderGraficoComparacion(this.periodos());
      }
    });
  }

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.afluenciaChart?.destroy();
    this.desgloseChart?.destroy();
    this.formaPagoChart?.destroy();
    this.estadoChart?.destroy();
    this.extrasChart?.destroy();
    this.horaChart?.destroy();
    this.origenChart?.destroy();
    this.articulosVariosChart?.destroy();
    this.comparacionRecaudacionChart?.destroy();
    this.comparacionPersonasChart?.destroy();
    this.comparacionTiposChart?.destroy();
  }

  /** Plata cobrada para el origen dado (BOLETERIA = venta en puerta, ANTICIPADA = reservas); null si no hay datos. */
  recaudacionPorOrigen(r: ReporteResumen, origen: VentasPorOrigen['origen']): number | null {
    return r.ventasPorOrigen.find((o) => o.origen === origen)?.monto ?? null;
  }

  etiquetaEstado(estado: ComprasPorEstado['estado']): string {
    return ETIQUETA_POR_ESTADO[estado];
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.reporteService.getResumen(this.desde(), this.hasta()).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar el reporte:', err);
        this.error.set('No se pudo cargar el reporte.');
        this.cargando.set(false);
      },
    });
  }

  agregarPeriodo(): void {
    this.periodos.update((ps) => [...ps, { etiqueta: '', desde: '', hasta: '', resumen: null }]);
  }

  quitarPeriodo(index: number): void {
    this.periodos.update((ps) => ps.filter((_, i) => i !== index));
  }

  actualizarPeriodo(index: number, campo: 'etiqueta' | 'desde' | 'hasta', valor: string): void {
    this.periodos.update((ps) => ps.map((p, i) => (i === index ? { ...p, [campo]: valor } : p)));
  }

  /** Pide el resumen de cada período por separado (no todo-o-nada): si uno falla, los demás
   * igual se muestran en la tabla. */
  compararPeriodos(): void {
    const filas = this.periodos();
    const conFechas = filas.filter((p) => p.desde && p.hasta);
    if (conFechas.length === 0) return;

    this.comparando.set(true);
    let pendientes = conFechas.length;
    const terminoUno = () => {
      pendientes--;
      if (pendientes === 0) this.comparando.set(false);
    };

    filas.forEach((p, i) => {
      if (!p.desde || !p.hasta) return;
      this.reporteService.getResumen(p.desde, p.hasta).subscribe({
        next: (r) => {
          this.periodos.update((ps) => ps.map((x, xi) => (xi === i ? { ...x, resumen: r } : x)));
          terminoUno();
        },
        error: (err) => {
          console.error('Error al cargar el período de comparación:', err);
          terminoUno();
        },
      });
    });
  }

  /** Dos gráficos chicos (plata y gente no comparten escala, no tiene sentido un solo eje):
   * uno agrupa recaudación total/anticipada/en puerta por período, el otro personas ingresadas. */
  private renderGraficoComparacion(periodos: PeriodoComparacion[]): void {
    const conDatos = periodos.filter((p): p is PeriodoComparacion & { resumen: ReporteResumen } => p.resumen !== null);
    const etiquetas = conDatos.map((p) => p.etiqueta || `${p.desde} → ${p.hasta}`);

    if (this.comparacionRecaudacionCanvas()) {
      this.comparacionRecaudacionChart?.destroy();
      this.comparacionRecaudacionChart = new Chart(this.comparacionRecaudacionCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: etiquetas,
          datasets: [
            { label: 'Recaudación total', data: conDatos.map((p) => p.resumen.recaudacionTotal), backgroundColor: '#39a935' },
            { label: 'Anticipada', data: conDatos.map((p) => this.recaudacionPorOrigen(p.resumen, 'ANTICIPADA') ?? 0), backgroundColor: '#4a7fc9' },
            { label: 'En puerta', data: conDatos.map((p) => this.recaudacionPorOrigen(p.resumen, 'BOLETERIA') ?? 0), backgroundColor: '#e0a72e' },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }

    if (this.comparacionPersonasCanvas()) {
      this.comparacionPersonasChart?.destroy();
      this.comparacionPersonasChart = new Chart(this.comparacionPersonasCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: etiquetas,
          datasets: [{ label: 'Personas que entraron', data: conDatos.map((p) => p.resumen.personasIngresadas), backgroundColor: '#7a5bc9' }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }

    if (this.comparacionTiposCanvas()) {
      // Todos los nombres de tipo que aparecen en CUALQUIERA de los períodos comparados (no
      // sólo el primero): si un tipo se dio de baja o se creó entre medio, igual aparece.
      const nombresTipo = [...new Set(conDatos.flatMap((p) => p.resumen.desglosePorTipo.map((t) => t.nombre)))];
      this.comparacionTiposChart?.destroy();
      this.comparacionTiposChart = new Chart(this.comparacionTiposCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: etiquetas,
          datasets: nombresTipo.map((nombre, i) => ({
            label: nombre,
            data: conDatos.map((p) => {
              const t = p.resumen.desglosePorTipo.find((x) => x.nombre === nombre);
              return t ? t.cantidadAnticipada + t.cantidadBoleteria : 0;
            }),
            backgroundColor: PALETA_TIPOS[i % PALETA_TIPOS.length],
          })),
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }
  }

  private renderGraficos(r: ReporteResumen): void {
    if (this.afluenciaCanvas()) {
      this.afluenciaChart?.destroy();
      this.afluenciaChart = new Chart(this.afluenciaCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: r.afluenciaDiaria.map((d) => d.fecha.slice(5)),
          datasets: [
            { label: 'Reservado para ese día', data: r.afluenciaDiaria.map((d) => d.pasesVendidosAnticipada), backgroundColor: '#39a935' },
            { label: 'Ingresos de anticipada/regalo ese día', data: r.afluenciaDiaria.map((d) => d.pasesValidadosAnticipada), backgroundColor: '#1f6b1c' },
            { label: 'Venta de puerta ese día', data: r.afluenciaDiaria.map((d) => d.pasesVendidosBoleteria), backgroundColor: '#4a7fc9' },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }

    if (this.desgloseCanvas()) {
      this.desgloseChart?.destroy();
      this.desgloseChart = new Chart(this.desgloseCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: r.desglosePorTipo.map((t) => t.nombre),
          datasets: [
            { label: 'Anticipada', data: r.desglosePorTipo.map((t) => t.cantidadAnticipada), backgroundColor: '#39a935' },
            { label: 'Boletería', data: r.desglosePorTipo.map((t) => t.cantidadBoleteria), backgroundColor: '#4a7fc9' },
          ],
        },
        options: {
          responsive: true,
          indexAxis: 'y',
          scales: { x: { stacked: true, beginAtZero: true, ticks: { precision: 0 } }, y: { stacked: true } },
        },
      });
    }

    if (this.formaPagoCanvas()) {
      this.formaPagoChart?.destroy();
      this.formaPagoChart = new Chart(this.formaPagoCanvas()!.nativeElement, {
        type: 'doughnut',
        data: {
          labels: r.recaudacionPorFormaPago.map((f) => f.etiqueta),
          datasets: [{
            data: r.recaudacionPorFormaPago.map((f) => f.monto),
            backgroundColor: r.recaudacionPorFormaPago.map((f) => COLOR_POR_FORMA_PAGO[f.formaPago] ?? '#9aa0a6'),
          }],
        },
        options: { responsive: true },
      });
    }

    if (this.estadoCanvas()) {
      this.estadoChart?.destroy();
      this.estadoChart = new Chart(this.estadoCanvas()!.nativeElement, {
        type: 'doughnut',
        data: {
          labels: r.comprasPorEstado.map((e) => this.etiquetaEstado(e.estado)),
          datasets: [{
            data: r.comprasPorEstado.map((e) => e.cantidad),
            backgroundColor: r.comprasPorEstado.map((e) => COLOR_POR_ESTADO[e.estado]),
          }],
        },
        options: {
          responsive: true,
          plugins: { legend: { position: 'bottom' } },
        },
      });
    }

    if (this.extrasCanvas()) {
      this.extrasChart?.destroy();
      this.extrasChart = new Chart(this.extrasCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: r.desgloseExtras.map((t) => t.nombre),
          // Los extras (ej. almuerzo) sólo se venden en la compra anticipada: en boletería/POS no se ofrecen.
          datasets: [{ label: 'Unidades vendidas', data: r.desgloseExtras.map((t) => t.cantidadAnticipada), backgroundColor: '#39a935' }],
        },
        options: {
          responsive: true,
          indexAxis: 'y',
          scales: { x: { beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }

    if (this.horaCanvas()) {
      this.horaChart?.destroy();
      this.horaChart = new Chart(this.horaCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: r.ventasPorHora.map((h) => `${String(h.hora).padStart(2, '0')}h`),
          datasets: [
            { label: 'Anticipada', data: r.ventasPorHora.map((h) => h.cantidadComprasAnticipada), backgroundColor: '#39a935' },
            { label: 'Boletería', data: r.ventasPorHora.map((h) => h.cantidadComprasBoleteria), backgroundColor: '#4a7fc9' },
          ],
        },
        options: {
          responsive: true,
          scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }

    if (this.origenCanvas()) {
      this.origenChart?.destroy();
      this.origenChart = new Chart(this.origenCanvas()!.nativeElement, {
        type: 'doughnut',
        data: {
          labels: r.ventasPorOrigen.map((o) => o.etiqueta),
          datasets: [{
            data: r.ventasPorOrigen.map((o) => o.monto),
            backgroundColor: r.ventasPorOrigen.map((o) => COLOR_POR_ORIGEN[o.origen]),
          }],
        },
        options: { responsive: true },
      });
    }

    if (this.articulosVariosCanvas()) {
      this.articulosVariosChart?.destroy();
      this.articulosVariosChart = new Chart(this.articulosVariosCanvas()!.nativeElement, {
        type: 'bar',
        data: {
          labels: r.ventasArticulosVarios.map((a) => a.nombre),
          datasets: [{ label: 'Unidades vendidas', data: r.ventasArticulosVarios.map((a) => a.cantidad), backgroundColor: '#e0a72e' }],
        },
        options: {
          responsive: true,
          indexAxis: 'y',
          scales: { x: { beginAtZero: true, ticks: { precision: 0 } } },
        },
      });
    }
  }
}
