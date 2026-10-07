import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { LucideCircleAlert, LucideCircleCheck, LucideInfo, LucideRefreshCw } from '@lucide/angular';
import { EstadoSistema, TarjetaEstado } from '../../models/sistema';
import { SistemaService } from '../../services/sistema.service';
import { Spinner } from '../../shared/spinner/spinner';

/** Se refresca solo mientras la pantalla está abierta. */
const REFRESCO_MS = 60_000;

/** Sistema > Estado: una tarjeta por área (verde / rojo / gris), con el detalle y a dónde ir a resolverlo. */
@Component({
  selector: 'app-estado-sistema',
  imports: [DatePipe, Spinner, LucideCircleCheck, LucideCircleAlert, LucideInfo, LucideRefreshCw],
  templateUrl: './estado-sistema.html',
  styleUrl: './estado-sistema.css',
})
export class EstadoSistemaPanel implements OnInit {
  private sistemaService = inject(SistemaService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  estado = signal<EstadoSistema | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
    interval(REFRESCO_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.cargar(false));
  }

  cargar(conSpinner = true): void {
    if (conSpinner) this.cargando.set(true);
    this.sistemaService
      .estado()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (e) => {
          this.estado.set(e);
          this.cargando.set(false);
          this.error.set(null);
        },
        error: () => {
          this.cargando.set(false);
          if (conSpinner) this.error.set('No se pudo consultar el estado del sistema.');
        },
      });
  }

  alertas(e: EstadoSistema): number {
    return e.tarjetas.filter((t) => t.estado === 'ALERTA').length;
  }

  /** El enlace puede traer query params (/sistema?tab=errores&area=PAGOS). */
  ir(t: TarjetaEstado): void {
    if (!t.enlace) return;
    this.router.navigateByUrl(t.enlace);
  }
}
