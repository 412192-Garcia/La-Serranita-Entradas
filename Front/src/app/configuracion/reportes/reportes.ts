import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { ReporteService } from '../../Services/reporte.service';
import { ComprasPorEstado, ReporteResumen } from '../../models/reporte';

Chart.register(...registerables);

function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Colores por estado, coherentes con los badges de Boletería (verde = ok, ámbar = pendiente, gris = inactivo). */
const COLOR_POR_ESTADO: Record<ComprasPorEstado['estado'], string> = {
  APROBADO: '#39a935',
  USADO: '#1f6b1c',
  RESERVADO_EFECTIVO: '#e0a72e',
  PENDIENTE_PAGO: '#9aa0a6',
  CANCELADO: '#c94f4f',
};

const ETIQUETA_POR_ESTADO: Record<ComprasPorEstado['estado'], string> = {
  APROBADO: 'Pagada online',
  USADO: 'Ya utilizada',
  RESERVADO_EFECTIVO: 'A cobrar en caja',
  PENDIENTE_PAGO: 'Pago pendiente',
  CANCELADO: 'Cancelada',
};

@Component({
  selector: 'app-configuracion-reportes',
  imports: [FormsModule, CurrencyPipe],
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
  private afluenciaChart: Chart | null = null;
  private desgloseChart: Chart | null = null;
  private formaPagoChart: Chart | null = null;
  private estadoChart: Chart | null = null;
  private extrasChart: Chart | null = null;
  private horaChart: Chart | null = null;

  private readonly hoy = new Date();
  desde = signal(aFechaISO(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate() - 29)));
  hasta = signal(aFechaISO(this.hoy));

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

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
  }

  /** Recaudación total dividida entre las compras que la generaron; null si no hay ninguna. */
  ticketPromedio(r: ReporteResumen): number | null {
    return r.cantidadCompras > 0 ? r.recaudacionTotal / r.cantidadCompras : null;
  }

  etiquetaEstado(estado: ComprasPorEstado['estado']): string {
    return ETIQUETA_POR_ESTADO[estado];
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
          { label: 'Vendidas', data: r.afluenciaDiaria.map((d) => d.pasesVendidos), backgroundColor: '#39a935' },
          { label: 'Validadas (DNI en boletería)', data: r.afluenciaDiaria.map((d) => d.pasesValidados), backgroundColor: '#1f6b1c' },
        ],
      },
      options: {
        responsive: true,
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
      },
    });

    this.desgloseChart?.destroy();
    this.desgloseChart = new Chart(this.desgloseCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: r.desglosePorTipo.map((t) => t.nombre),
        datasets: [{ label: 'Pases vendidos', data: r.desglosePorTipo.map((t) => t.cantidad), backgroundColor: '#39a935' }],
      },
      options: {
        responsive: true,
        indexAxis: 'y',
        scales: { x: { beginAtZero: true, ticks: { precision: 0 } } },
      },
    });

    this.formaPagoChart?.destroy();
    this.formaPagoChart = new Chart(this.formaPagoCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: r.recaudacionPorFormaPago.map((f) => f.etiqueta),
        datasets: [{ data: r.recaudacionPorFormaPago.map((f) => f.monto), backgroundColor: ['#39a935', '#e0a72e'] }],
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
      options: { responsive: true },
    });

    this.extrasChart?.destroy();
    this.extrasChart = new Chart(this.extrasCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: r.desgloseExtras.map((t) => t.nombre),
        datasets: [{ label: 'Unidades vendidas', data: r.desgloseExtras.map((t) => t.cantidad), backgroundColor: '#4a7fc9' }],
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
        datasets: [{ label: 'Compras', data: r.ventasPorHora.map((h) => h.cantidadCompras), backgroundColor: '#39a935' }],
      },
      options: {
        responsive: true,
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
      },
    });
  }
}
