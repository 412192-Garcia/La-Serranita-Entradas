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
    texto: 'Cuánto se vendió hoy, cuánta gente entró al parque y cuánto se recaudó (parque vs. anticipadas web), actualizado en vivo. Para el análisis fino de ventas está Reportes.',
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

  /** Cómo se compone "Personas ingresadas" de hoy: gente que compró y entró en la puerta vs
   * gente que entró validando una anticipada (comprada hoy o cualquier otro día). */
  desglosePersonasHoy(r: ReporteResumen): { puerta: number; anticipada: number } {
    return r.afluenciaDiaria.reduce(
      (acc, d) => ({
        puerta: acc.puerta + d.pasesVendidosBoleteria,
        anticipada: acc.anticipada + d.pasesValidadosAnticipada,
      }),
      { puerta: 0, anticipada: 0 },
    );
  }

  /** KPI principal: entradas vendidas hoy = venta directa en puerta + anticipadas compradas
   * hoy (para cualquier fecha de visita). No confundir con "personas que entraron": esto mide
   * venta, no ingreso real al parque. */
  entradasVendidasHoy(r: ReporteResumen): { total: number; puerta: number; anticipada: number } {
    const { puerta, anticipada } = r.afluenciaDiaria.reduce(
      (acc, d) => ({
        puerta: acc.puerta + d.pasesVendidosBoleteria,
        anticipada: acc.anticipada + d.pasesCompradosAnticipada,
      }),
      { puerta: 0, anticipada: 0 },
    );
    return { total: puerta + anticipada, puerta, anticipada };
  }

  /** Anticipadas reservadas para VENIR hoy (por fecha de visita), estén ya validadas o no. */
  anticipadasParaHoy(r: ReporteResumen): number {
    return r.afluenciaDiaria.reduce((acc, d) => acc + d.pasesVendidosAnticipada, 0);
  }

  /** Recaudación de hoy vendida/cobrada en el parque: todo lo cobrado en boletería salvo lo
   * pagado online por Mercado Pago (eso es la recaudación anticipada web, aparte). */
  recaudacionParque(r: ReporteResumen): { monto: number; cantidad: number } {
    return r.recaudacionPorFormaPago
      .filter((f) => f.formaPago !== 'MERCADO_PAGO')
      .reduce((acc, f) => ({ monto: acc.monto + f.monto, cantidad: acc.cantidad + f.cantidad }), { monto: 0, cantidad: 0 });
  }

  /** Recaudación de hoy por anticipadas pagadas online (Mercado Pago), sin importar para qué
   * día de visita sean. */
  recaudacionAnticipadaWeb(r: ReporteResumen): { monto: number; cantidad: number } {
    return r.recaudacionPorFormaPago
      .filter((f) => f.formaPago === 'MERCADO_PAGO')
      .reduce((acc, f) => ({ monto: acc.monto + f.monto, cantidad: acc.cantidad + f.cantidad }), { monto: 0, cantidad: 0 });
  }
}
