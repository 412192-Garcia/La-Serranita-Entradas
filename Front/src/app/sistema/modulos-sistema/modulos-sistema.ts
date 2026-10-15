import { computed, Component, OnInit, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideRefreshCw } from '@lucide/angular';
import { Spinner } from '../../shared/spinner/spinner';
import { CodigoModulo, EstadoModulo, ModulosService } from '../../services/modulos.service';
import { PointService, TerminalPoint } from '../../services/point.service';

/**
 * Sistema > Módulos (SUPERADMIN): prender y apagar partes del sistema (facturación, cobro con
 * Point) y administrar los Points de Mercado Pago: el nombre que ven las tablets y el modo
 * (integrado o manual).
 */
@Component({
  selector: 'app-modulos-sistema',
  imports: [NgTemplateOutlet, FormsModule, Spinner, LucideRefreshCw],
  templateUrl: './modulos-sistema.html',
  styleUrl: './modulos-sistema.css',
})
export class ModulosSistema implements OnInit {
  private modulosService = inject(ModulosService);
  private point = inject(PointService);

  readonly modulos = this.modulosService.modulos;
  /** Módulos principales; sus submódulos se muestran adentro (ver hijos). */
  readonly principales = computed(() => this.modulos().filter((m) => !m.padre));

  hijos(codigo: CodigoModulo): EstadoModulo[] {
    return this.modulos().filter((m) => m.padre === codigo);
  }
  /** Módulo que se está por apagar: se pide confirmar antes (apagar la facturación no es un toque al pasar). */
  confirmandoApagar = signal<CodigoModulo | null>(null);
  guardando = signal<CodigoModulo | null>(null);
  error = signal<string | null>(null);

  terminales = signal<TerminalPoint[] | null>(null);
  cargandoTerminales = signal(false);
  errorTerminales = signal<string | null>(null);
  /** Nombre que se está editando, por id de Point. */
  nombres = signal<Record<string, string>>({});
  operandoPoint = signal<string | null>(null);
  avisoPoint = signal<string | null>(null);

  ngOnInit(): void {
    this.modulosService.actualizar();
    this.cargarTerminales();
  }

  cambiar(m: EstadoModulo): void {
    if (m.activo && this.confirmandoApagar() !== m.codigo) {
      this.confirmandoApagar.set(m.codigo);
      return;
    }
    this.confirmandoApagar.set(null);
    this.guardando.set(m.codigo);
    this.error.set(null);
    this.modulosService.cambiar(m.codigo, !m.activo).subscribe({
      next: (r) => {
        this.guardando.set(null);
        if (r.codigo === 'POINT' && r.activo) this.cargarTerminales();
      },
      error: (err) => {
        this.guardando.set(null);
        this.error.set(this.mensaje(err, 'No se pudo cambiar el módulo.'));
      },
    });
  }

  cargarTerminales(): void {
    this.cargandoTerminales.set(true);
    this.errorTerminales.set(null);
    this.point.terminalesActualizadas().subscribe({
      next: (lista) => {
        this.cargandoTerminales.set(false);
        this.terminales.set(lista);
        this.nombres.set(Object.fromEntries(lista.map((t) => [t.id, t.nombre])));
      },
      error: (err) => {
        this.cargandoTerminales.set(false);
        this.errorTerminales.set(this.mensaje(err, 'No se pudo consultar los Points en Mercado Pago.'));
      },
    });
  }

  setNombre(id: string, nombre: string): void {
    this.nombres.update((n) => ({ ...n, [id]: nombre }));
  }

  nombreCambiado(t: TerminalPoint): boolean {
    return (this.nombres()[t.id] ?? '').trim() !== t.nombre;
  }

  guardarNombre(t: TerminalPoint): void {
    this.operar(t, this.point.renombrar(t.id, this.nombres()[t.id] ?? ''), `Nombre de ${t.serie} guardado.`);
  }

  cambiarModo(t: TerminalPoint): void {
    const modo = t.integrado ? 'STANDALONE' : 'PDV';
    this.operar(
      t,
      this.point.cambiarModo(t.id, modo),
      modo === 'PDV'
        ? `${t.nombre} pasó a integrado: ahora sólo cobra lo que le mandan las tablets.`
        : `${t.nombre} pasó a manual: se cobra en el propio Point y la venta se anota a mano.`
    );
  }

  private operar(t: TerminalPoint, pedido: ReturnType<PointService['renombrar']>, aviso: string): void {
    this.operandoPoint.set(t.id);
    this.avisoPoint.set(null);
    this.errorTerminales.set(null);
    pedido.subscribe({
      next: () => {
        this.operandoPoint.set(null);
        this.avisoPoint.set(aviso);
        this.cargarTerminales();
      },
      error: (err) => {
        this.operandoPoint.set(null);
        this.errorTerminales.set(this.mensaje(err, 'No se pudo hacer el cambio.'));
      },
    });
  }

  private mensaje(err: unknown, porDefecto: string): string {
    if (err instanceof HttpErrorResponse && typeof err.error === 'string' && err.error) return err.error;
    return porDefecto;
  }
}
