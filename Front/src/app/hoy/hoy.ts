import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
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
    texto: 'La gente que entró hoy y lo recaudado, en vivo.',
  },
  {
    selector: '[data-tour="cajas-abiertas"]',
    titulo: 'Quién está trabajando',
    texto: 'Qué boleteros tienen caja abierta y cuánto llevan vendido.',
  },
];

function hoyISO(): string {
  return aFechaISO(new Date());
}

const MINIMO_ENTRE_RECARGAS_MS = 60_000;

@Component({
  selector: 'app-dashboard-hoy',
  imports: [PesosPipe, DatePipe, CabeceraInterna, Spinner],
  templateUrl: './hoy.html',
  styleUrls: ['../configuracion/configuracion-shared.css', './hoy.css'],
})
export class DashboardHoy implements OnInit, OnDestroy {
  private router = inject(Router);
  private reporteService = inject(ReporteService);
  private cajaService = inject(CajaService);

  readonly pasosTutorial = PASOS_TUTORIAL;

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

  cargandoCajas = signal(false);
  cajasAbiertas = signal<CajaAbierta[]>([]);

  private ultimaCarga = 0;

  /** Al volver a la app (el celular la deja en segundo plano) los números pueden estar viejos. */
  private alVolverALaApp = (): void => {
    if (document.visibilityState === 'visible' && Date.now() - this.ultimaCarga > MINIMO_ENTRE_RECARGAS_MS) {
      this.cargar();
    }
  };

  abrirCaja(id: number): void {
    this.router.navigate(['/cajas'], { queryParams: { caja: id } });
  }

  ngOnInit(): void {
    this.cargar();
    document.addEventListener('visibilitychange', this.alVolverALaApp);
  }

  ngOnDestroy(): void {
    document.removeEventListener('visibilitychange', this.alVolverALaApp);
  }

  cargar(): void {
    const hoy = hoyISO();
    this.ultimaCarga = Date.now();

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

  /** Anticipadas reservadas para VENIR hoy (por fecha de visita), estén ya validadas o no. */
  anticipadasParaHoy(r: ReporteResumen): number {
    return r.afluenciaDiaria.reduce((acc, d) => acc + d.pasesVendidosAnticipada, 0);
  }

  /** Recaudación de hoy vendida/cobrada en el parque: todo lo cobrado en boletería salvo lo
   * pagado online por Mercado Pago (eso es la recaudación anticipada web, aparte). */
  recaudacionParque(r: ReporteResumen): { monto: number; cantidad: number; entradas: number } {
    const recaudado = r.recaudacionPorFormaPago
      .filter((f) => f.formaPago !== 'MERCADO_PAGO')
      .reduce((acc, f) => ({ monto: acc.monto + f.monto, cantidad: acc.cantidad + f.cantidad }), { monto: 0, cantidad: 0 });
    return { ...recaudado, entradas: r.entradasCobradasParque };
  }

  /** Recaudación de hoy por anticipadas pagadas online (Mercado Pago), sin importar para qué
   * día de visita sean. */
  recaudacionAnticipadaWeb(r: ReporteResumen): { monto: number; cantidad: number; entradas: number } {
    const recaudado = r.recaudacionPorFormaPago
      .filter((f) => f.formaPago === 'MERCADO_PAGO')
      .reduce((acc, f) => ({ monto: acc.monto + f.monto, cantidad: acc.cantidad + f.cantidad }), { monto: 0, cantidad: 0 });
    return { ...recaudado, entradas: r.entradasCobradasWeb };
  }
}
