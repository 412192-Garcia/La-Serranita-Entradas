import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ReporteService } from '../services/reporte.service';
import { CajaService, CajaAbierta } from '../services/caja.service';
import { ReporteResumen } from '../models/reporte';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { Spinner } from '../shared/spinner/spinner';
import { PesosPipe } from '../shared/pesos.pipe';
import { TourStep } from '../shared/tour/tour';
import { aFechaISO } from '../shared/fecha.util';

const PASOS_TUTORIAL: TourStep[] = [
  {
    selector: '[data-tour="resumen-hoy"]',
    titulo: 'Cómo viene el día',
    texto: 'Recaudación, personas que entraron y entradas vendidas hoy, actualizado en vivo.',
  },
  {
    selector: '[data-tour="cajas-abiertas"]',
    titulo: 'Quién está trabajando',
    texto: 'Vés qué boleteros tienen caja abierta ahora mismo y cuánto llevan vendido.',
  },
];

function hoyISO(): string {
  return aFechaISO(new Date());
}

@Component({
  selector: 'app-dashboard-hoy',
  imports: [PesosPipe, DatePipe, CabeceraInterna, Spinner],
  templateUrl: './hoy.html',
  styleUrls: ['../configuracion/configuracion-shared.css', './hoy.css'],
})
export class DashboardHoy implements OnInit {
  private reporteService = inject(ReporteService);
  private cajaService = inject(CajaService);

  readonly pasosTutorial = PASOS_TUTORIAL;

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

  cargandoCajas = signal(false);
  cajasAbiertas = signal<CajaAbierta[]>([]);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    const hoy = hoyISO();

    this.cargando.set(true);
    this.error.set(null);
    this.reporteService.getResumen(hoy, hoy).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar el resumen de hoy:', err);
        this.error.set('No se pudo cargar el resumen de hoy.');
        this.cargando.set(false);
      },
    });

    this.cargandoCajas.set(true);
    this.cajaService.obtenerCajasAbiertas().subscribe({
      next: (cs) => {
        this.cajasAbiertas.set(cs);
        this.cargandoCajas.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las cajas abiertas:', err);
        this.cargandoCajas.set(false);
      },
    });
  }

  /** Entradas (no extras) vendidas hoy, sumando anticipada + venta en puerta. */
  totalEntradasHoy(r: ReporteResumen): number {
    return r.desglosePorTipo.reduce((acc, t) => acc + t.cantidadAnticipada + t.cantidadBoleteria, 0);
  }
}
