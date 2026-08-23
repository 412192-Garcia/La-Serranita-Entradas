import { Component, inject, input, output } from '@angular/core';
import { LucideCloudOff, LucideTriangleAlert } from '@lucide/angular';
import { Caja } from '../../services/caja.service';
import { OperacionesPendientesService } from '../../services/operaciones-pendientes.service';
import { PesosPipe } from '../../shared/pesos.pipe';

@Component({
  selector: 'app-barra-caja',
  imports: [PesosPipe, LucideCloudOff, LucideTriangleAlert],
  templateUrl: './barra-caja.html',
  styleUrl: './barra-caja.css',
})
export class BarraCaja {
  private pendientesService = inject(OperacionesPendientesService);

  /** Operaciones cobradas/registradas que todavía no confirmó el servidor. */
  readonly pendientes = this.pendientesService.pendientes;
  /** Las que el servidor rechazó: hay que resolverlas con un admin, no se reintentan solas. */
  readonly conError = this.pendientesService.conError;

  caja = input.required<Caja>();

  retirarEfectivo = output<void>();
  agregarEntradas = output<void>();

  /** Cuántas entradas físicas tiene el boletero ahora mismo (inicial + ingresos netos): el
   * desglose de "cuánto entró/salió" ya está en el historial del modal de Reponer talonario,
   * acá alcanza con el número final, no hace falta repetir la cuenta cada vez. */
  stockEntradas(): number {
    return (this.caja().entradasFisicasInicial ?? 0) + this.caja().totalIngresosEntradas;
  }

  /** El rechazo ya quedó guardado del lado del servidor (ver "Operaciones rechazadas" en Cajas):
   * descartarlo acá sólo saca el cartel de este navegador, no borra el registro que puede ver
   * un admin desde cualquier otra máquina. */
  descartarErrores(): void {
    for (const e of this.conError()) {
      this.pendientesService.descartar(e.idempotencyKey);
    }
  }
}
