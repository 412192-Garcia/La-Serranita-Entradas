import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { LucideCircleCheck, LucideRefreshCw } from '@lucide/angular';
import { Rendimiento } from '../../models/sistema';
import { SistemaService } from '../../services/sistema.service';
import { Spinner } from '../../shared/spinner/spinner';

/** Endpoints con menos requests que esto no se listan por defecto: un promedio de 1 o 2 no dice nada. */
const MIN_REQUESTS = 5;

/**
 * Sistema > Rendimiento: cuánto tarda cada endpoint de la API desde que arrancó el servidor (en
 * memoria del backend) y las últimas requests lentas, con el id que las encuentra en el log.
 */
@Component({
  selector: 'app-rendimiento-sistema',
  imports: [DatePipe, Spinner, LucideRefreshCw, LucideCircleCheck],
  templateUrl: './rendimiento-sistema.html',
  styleUrl: './rendimiento-sistema.css',
})
export class RendimientoSistema implements OnInit {
  private sistemaService = inject(SistemaService);

  datos = signal<Rendimiento | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);
  verTodos = signal(false);

  readonly endpoints = computed(() => {
    const d = this.datos();
    if (!d) return [];
    return this.verTodos() ? d.endpoints : d.endpoints.filter((e) => e.cantidad >= MIN_REQUESTS);
  });
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

  esLento(ms: number): boolean {
    return ms >= (this.datos()?.umbralMs ?? Infinity);
  }
}
