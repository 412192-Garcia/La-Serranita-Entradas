import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideCircleAlert, LucideCircleCheck } from '@lucide/angular';
import { Incidente, Pagina } from '../../models/sistema';
import { SistemaService } from '../../services/sistema.service';
import { Spinner } from '../../shared/spinner/spinner';

type Filtro = 'pendientes' | 'resueltos' | 'todos';

const AREAS: { valor: string; etiqueta: string }[] = [
  { valor: 'PAGOS', etiqueta: 'Pagos' },
  { valor: 'FACTURACION', etiqueta: 'Facturación' },
  { valor: 'MAILS', etiqueta: 'Mails' },
  { valor: 'IMPRESION', etiqueta: 'Impresión' },
  { valor: 'SISTEMA', etiqueta: 'Sistema' },
  { valor: 'FRONT', etiqueta: 'Navegador' },
];

/** Sistema > Errores: errores del servidor agrupados, para revisarlos y marcarlos resueltos. */
@Component({
  selector: 'app-errores-sistema',
  imports: [DatePipe, Spinner, LucideCircleAlert, LucideCircleCheck],
  templateUrl: './errores-sistema.html',
  styleUrl: './errores-sistema.css',
})
export class ErroresSistema implements OnInit {
  private sistemaService = inject(SistemaService);
  private destroyRef = inject(DestroyRef);

  areaInicial = input<string | null>(null);

  readonly areas = AREAS;
  filtro = signal<Filtro>('pendientes');
  area = signal<string | null>(null);
  pagina = signal<Pagina<Incidente> | null>(null);
  numeroPagina = signal(0);
  cargando = signal(true);
  error = signal<string | null>(null);
  abiertoId = signal<number | null>(null);
  resolviendoId = signal<number | null>(null);

  ngOnInit(): void {
    if (this.areaInicial()) this.area.set(this.areaInicial());
    this.cargar();
  }

  etiquetaArea(valor: string): string {
    return AREAS.find((a) => a.valor === valor)?.etiqueta ?? valor;
  }

  elegirFiltro(f: Filtro): void {
    this.filtro.set(f);
    this.cargar();
  }

  elegirArea(a: string | null): void {
    this.area.set(this.area() === a ? null : a);
    this.cargar();
  }

  cargar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(null);
    this.numeroPagina.set(pagina);
    this.sistemaService
      .incidentes(this.filtro(), this.area(), pagina)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (p) => {
          this.pagina.set(p);
          this.cargando.set(false);
        },
        error: () => {
          this.cargando.set(false);
          this.error.set('No se pudieron consultar los errores.');
        },
      });
  }

  alternar(i: Incidente): void {
    this.abiertoId.set(this.abiertoId() === i.id ? null : i.id);
  }

  resolver(i: Incidente): void {
    this.resolviendoId.set(i.id);
    this.sistemaService.resolverIncidente(i.id).subscribe({
      next: () => {
        this.resolviendoId.set(null);
        this.cargar(this.numeroPagina());
      },
      error: () => {
        this.resolviendoId.set(null);
        this.error.set('No se pudo marcar como resuelto.');
      },
    });
  }
}
