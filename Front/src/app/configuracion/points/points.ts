import { Component, OnInit, inject, signal } from '@angular/core';
import { ToastService } from '../../shared/toast/toast.service';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideRefreshCw } from '@lucide/angular';
import { Spinner } from '../../shared/spinner/spinner';
import { ModulosService } from '../../services/modulos.service';
import { PointService, TerminalPoint, ordenarTerminales } from '../../services/point.service';

/**
 * Configuración > Points (ADMIN): los Points de Mercado Pago de la cuenta, el nombre que ven las
 * tablets y el modo (integrado o manual). Prender o apagar el cobro con Point en sí es de
 * Sistema > Módulos.
 */
@Component({
  selector: 'app-configuracion-points',
  imports: [FormsModule, Spinner, LucideRefreshCw],
  templateUrl: './points.html',
  styleUrls: ['../configuracion-shared.css', './points.css'],
})
export class ConfiguracionPoints implements OnInit {
  private point = inject(PointService);
  private modulos = inject(ModulosService);
  private toast = inject(ToastService);

  /** Cobro con Point prendido en Sistema > Módulos (y con token en el servidor). */
  readonly moduloActivo = this.modulos.pointActivo;

  terminales = signal<TerminalPoint[] | null>(null);
  cargando = signal(false);
  error = signal<string | null>(null);
  /** Nombre que se está editando, por id de Point. */
  nombres = signal<Record<string, string>>({});
  operando = signal<string | null>(null);

  ngOnInit(): void {
    this.modulos.actualizar();
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.point.terminalesActualizadas().subscribe({
      next: (lista) => {
        this.cargando.set(false);
        // Se ordena al cargar y no en cada cambio: si no, el Point se movería de lugar al tocarlo.
        this.terminales.set(ordenarTerminales(lista));
        this.nombres.set(Object.fromEntries(lista.map((t) => [t.id, t.nombre])));
      },
      error: (err) => {
        this.cargando.set(false);
        this.error.set(this.mensaje(err, 'No se pudo consultar los Points en Mercado Pago.'));
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
    const nombre = (this.nombres()[t.id] ?? '').trim();
    this.operar(t, this.point.renombrar(t.id, nombre), `Nombre de ${t.serie} guardado.`, () => {
      // Vacío = vuelve al nombre por defecto, que arma el servidor: ahí sí hace falta recargar.
      if (!nombre) this.cargar();
      else this.reemplazar({ ...t, nombre });
    });
  }

  /** Que aparezca (o no) en el selector de dispositivos de las tablets. */
  cambiarHabilitado(t: TerminalPoint): void {
    const habilitado = !t.habilitado;
    this.operar(
      t,
      this.point.habilitar(t.id, habilitado),
      habilitado
        ? `${t.nombre} ya se puede elegir desde las tablets.`
        : `${t.nombre} dejó de aparecer en las tablets.`,
      () => this.reemplazar({ ...t, habilitado })
    );
  }

  cambiarModo(t: TerminalPoint): void {
    const modo = t.integrado ? 'STANDALONE' : 'PDV';
    this.operar(
      t,
      this.point.cambiarModo(t.id, modo),
      modo === 'PDV'
        ? `${t.nombre} pasó a integrado: ahora sólo cobra lo que le mandan las tablets.`
        : `${t.nombre} pasó a manual: se cobra en el propio Point y la venta se anota a mano.`,
      // Se actualiza en el lugar: recargar enseguida le preguntaba a MP, que tarda en reflejar el
      // cambio (el Point volvía con el modo viejo o directamente no aparecía).
      () => this.reemplazar({ ...t, modo, integrado: modo === 'PDV' })
    );
  }

  private reemplazar(t: TerminalPoint): void {
    this.terminales.update((lista) => lista?.map((x) => (x.id === t.id ? t : x)) ?? null);
    this.nombres.update((n) => ({ ...n, [t.id]: t.nombre }));
  }

  private operar(t: TerminalPoint, pedido: ReturnType<PointService['renombrar']>, aviso: string, alTerminar: () => void): void {
    this.operando.set(t.id);
    this.error.set(null);
    pedido.subscribe({
      next: () => {
        this.operando.set(null);
        this.toast.exito(aviso);
        alTerminar();
      },
      error: (err) => {
        this.operando.set(null);
        this.toast.error(this.mensaje(err, 'No se pudo hacer el cambio.'));
      },
    });
  }

  private mensaje(err: unknown, porDefecto: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) return 'No se pudo contactar con el servidor.';
      if (typeof err.error === 'string' && err.error) return err.error;
    }
    return porDefecto;
  }
}
