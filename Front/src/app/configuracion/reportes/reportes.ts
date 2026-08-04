import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { ReporteService } from '../../Services/reporte.service';
import { CajaResumen, ComprasPorEstado, RecaudacionPorFormaPago, ReporteResumen, VentasPorOrigen } from '../../models/reporte';

Chart.register(...registerables);

function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

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

@Component({
  selector: 'app-configuracion-reportes',
  imports: [FormsModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './reportes.html',
  styleUrls: ['../configuracion-shared.css', './reportes.css'],
})
export class ConfiguracionReportes implements OnInit, OnDestroy {
  private reporteService = inject(ReporteService);

  @ViewChild('afluenciaCanvas', { static: true }) private afluenciaCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('desgloseCanvas', { static: true }) private desgloseCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('formaPagoCanvas', { static: true }) private formaPagoCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('estadoCanvas', { static: true }) private estadoCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('extrasCanvas', { static: true }) private extrasCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('horaCanvas', { static: true }) private horaCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('origenCanvas', { static: true }) private origenCanvas!: ElementRef<HTMLCanvasElement>;
  private afluenciaChart: Chart | null = null;
  private desgloseChart: Chart | null = null;
  private formaPagoChart: Chart | null = null;
  private estadoChart: Chart | null = null;
  private extrasChart: Chart | null = null;
  private horaChart: Chart | null = null;
  private origenChart: Chart | null = null;

  private readonly hoy = new Date();
  desde = signal(aFechaISO(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate() - 29)));
  hasta = signal(aFechaISO(this.hoy));

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

  /** Vacío = todos los boleteros. */
  filtroBoletero = signal<string>('');

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
  }

  /** Plata cobrada para el origen dado (BOLETERIA = venta en puerta, ANTICIPADA = reservas); null si no hay datos. */
  recaudacionPorOrigen(r: ReporteResumen, origen: VentasPorOrigen['origen']): number | null {
    return r.ventasPorOrigen.find((o) => o.origen === origen)?.monto ?? null;
  }

  etiquetaEstado(estado: ComprasPorEstado['estado']): string {
    return ETIQUETA_POR_ESTADO[estado];
  }

  /** Para colorear la fila de la caja en la tabla: rojo si faltó plata, verde si sobró o cerró justo. */
  claseDiferencia(caja: CajaResumen): string {
    if (caja.diferencia < 0) return 'diferencia-faltante';
    if (caja.diferencia > 0) return 'diferencia-sobrante';
    return 'diferencia-exacta';
  }

  /** Nombres únicos de boleteros con al menos una caja cerrada en el rango, para el filtro. */
  boleterosDisponibles(r: ReporteResumen): string[] {
    return [...new Set(r.cajas.map((c) => c.usuarioNombre))].sort();
  }

  cajasFiltradas(r: ReporteResumen): CajaResumen[] {
    const filtro = this.filtroBoletero();
    return filtro ? r.cajas.filter((c) => c.usuarioNombre === filtro) : r.cajas;
  }

  /** Los KPI de retiros/faltantes/sobrantes se recalculan sobre el subconjunto filtrado, no sobre el total del backend. */
  totalesCajasFiltradas(r: ReporteResumen): { retiros: number; faltantes: number; sobrantes: number } {
    let retiros = 0;
    let faltantes = 0;
    let sobrantes = 0;
    for (const c of this.cajasFiltradas(r)) {
      retiros += c.totalRetiros;
      if (c.diferencia < 0) faltantes += Math.abs(c.diferencia);
      else sobrantes += c.diferencia;
    }
    return { retiros, faltantes, sobrantes };
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

  cargar(): void {
    if (!this.desde() || !this.hasta() || this.hasta() < this.desde()) {
      this.error.set('El rango de fechas es inválido.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.reporteService.getResumen(this.desde(), this.hasta()).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
        this.renderGraficos(r);
      },
      error: (err) => {
        console.error('Error al cargar el reporte:', err);
        this.error.set('No se pudo cargar el reporte.');
        this.cargando.set(false);
      },
    });
  }

  private renderGraficos(r: ReporteResumen): void {
    this.afluenciaChart?.destroy();
    this.afluenciaChart = new Chart(this.afluenciaCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: r.afluenciaDiaria.map((d) => d.fecha.slice(5)),
        datasets: [
          { label: 'Anticipada vendida', data: r.afluenciaDiaria.map((d) => d.pasesVendidosAnticipada), backgroundColor: '#39a935' },
          { label: 'Anticipada validada (DNI en boletería)', data: r.afluenciaDiaria.map((d) => d.pasesValidadosAnticipada), backgroundColor: '#1f6b1c' },
          { label: 'Boletería (venta de puerta)', data: r.afluenciaDiaria.map((d) => d.pasesVendidosBoleteria), backgroundColor: '#4a7fc9' },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
      },
    });

    this.desgloseChart?.destroy();
    this.desgloseChart = new Chart(this.desgloseCanvas.nativeElement, {
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

    this.formaPagoChart?.destroy();
    this.formaPagoChart = new Chart(this.formaPagoCanvas.nativeElement, {
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

    this.estadoChart?.destroy();
    this.estadoChart = new Chart(this.estadoCanvas.nativeElement, {
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

    this.extrasChart?.destroy();
    this.extrasChart = new Chart(this.extrasCanvas.nativeElement, {
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

    this.horaChart?.destroy();
    this.horaChart = new Chart(this.horaCanvas.nativeElement, {
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

    this.origenChart?.destroy();
    this.origenChart = new Chart(this.origenCanvas.nativeElement, {
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
}
