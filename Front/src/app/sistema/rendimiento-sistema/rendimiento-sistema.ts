import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { LucideArrowDown, LucideCircleCheck, LucideRefreshCw } from '@lucide/angular';
import { EndpointRendimiento, Rendimiento } from '../../models/sistema';
import { SistemaService } from '../../services/sistema.service';
import { Spinner } from '../../shared/spinner/spinner';

/** Endpoints con menos requests que esto no se listan por defecto: un promedio de 1 o 2 no dice nada. */
const MIN_REQUESTS = 5;

type Orden = 'cantidad' | 'promedioMs' | 'p95Ms' | 'maxMs' | 'totalMs' | 'lentas' | 'errores4xx' | 'errores';

const COLUMNAS: { clave: Orden; titulo: string; ayuda: string }[] = [
  { clave: 'cantidad', titulo: 'Requests', ayuda: 'Cuántas veces se llamó' },
  { clave: 'promedioMs', titulo: 'Promedio', ayuda: 'Tiempo promedio por request' },
  { clave: 'p95Ms', titulo: 'p95', ayuda: 'El 95 % de las requests tardó esto o menos (aproximado)' },
  { clave: 'maxMs', titulo: 'Máximo', ayuda: 'La más lenta' },
  { clave: 'totalMs', titulo: 'Tiempo total', ayuda: 'Tiempo sumado de todas, y qué parte del total del servidor es' },
  { clave: 'lentas', titulo: 'Lentas', ayuda: 'Requests por encima del umbral de lenta' },
  { clave: 'errores4xx', titulo: '4xx', ayuda: 'Pedidos rechazados: sesión vencida, datos inválidos, no encontrado' },
  { clave: 'errores', titulo: '5xx', ayuda: 'Errores del servidor' },
];

/**
 * Sistema > Rendimiento: cuánto tarda cada endpoint de la API desde que arrancó el servidor (en
 * memoria del backend) y las últimas requests lentas, con el id que las encuentra en el log.
 */
@Component({
  selector: 'app-rendimiento-sistema',
  imports: [DatePipe, Spinner, LucideRefreshCw, LucideCircleCheck, LucideArrowDown],
  templateUrl: './rendimiento-sistema.html',
  styleUrl: './rendimiento-sistema.css',
})
export class RendimientoSistema implements OnInit {
  private sistemaService = inject(SistemaService);

  datos = signal<Rendimiento | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);
  verTodos = signal(false);
  readonly columnas = COLUMNAS;
  orden = signal<Orden>('promedioMs');

  readonly endpoints = computed(() => {
    const d = this.datos();
    if (!d) return [];
    const campo = this.orden();
    const lista = this.verTodos() ? d.endpoints : d.endpoints.filter((e) => e.cantidad >= MIN_REQUESTS);
    return [...lista].sort((a: EndpointRendimiento, b: EndpointRendimiento) => b[campo] - a[campo]);
  });
  /** Tiempo del servidor sumado de todos los endpoints (también los ocultos), para el porcentaje. */
  private readonly tiempoTotal = computed(() => this.datos()?.endpoints.reduce((s, e) => s + e.totalMs, 0) ?? 0);
  readonly ocultos = computed(() => (this.datos()?.endpoints.length ?? 0) - this.endpoints().length);
  readonly totalRequests = computed(() => this.datos()?.endpoints.reduce((s, e) => s + e.cantidad, 0) ?? 0);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.sistemaService.rendimiento().subscribe({
      next: (d) => {
        this.datos.set(d);
        this.cargando.set(false);
        this.error.set(null);
      },
      error: () => {
        this.cargando.set(false);
        this.error.set('No se pudo consultar el rendimiento.');
      },
    });
  }

  /** "1,2 s" / "340 ms". */
  tiempo(ms: number): string {
    return ms >= 1000 ? `${(ms / 1000).toLocaleString('es-AR', { maximumFractionDigits: 1 })} s` : `${ms} ms`;
  }

  /** "2 h 5 min" / "3 min 20 s" / "12 s" / "340 ms": el total de un endpoint puede ser de horas. */
  tiempoLargo(ms: number): string {
    if (ms < 1000) return `${ms} ms`;
    const s = Math.round(ms / 1000);
    if (s < 60) return `${s} s`;
    const min = Math.floor(s / 60);
    if (min < 60) return `${min} min ${s % 60} s`;
    return `${Math.floor(min / 60)} h ${min % 60} min`;
  }

  porcentaje(ms: number): string {
    const total = this.tiempoTotal();
    if (total === 0) return '';
    const p = (ms * 100) / total;
    return p < 0.1 ? '< 0,1 %' : `${p.toLocaleString('es-AR', { maximumFractionDigits: 1 })} %`;
  }

  esLento(ms: number): boolean {
    return ms >= (this.datos()?.umbralMs ?? Infinity);
  }
}
