import { Component, inject } from '@angular/core';
import { LucideCloudOff, LucideTriangleAlert } from '@lucide/angular';
import { OperacionesPendientesService } from '../../services/operaciones-pendientes.service';

/**
 * Aviso del estado de la cola de operaciones offline del POS (operaciones sin sincronizar o
 * rechazadas). Retiro/Aporte y Reponer talonario están en la cabecera; los totales de la caja
 * no se muestran hasta el cierre. Si no hay nada que avisar, no renderiza nada.
 */
@Component({
  selector: 'app-barra-caja',
  imports: [LucideCloudOff, LucideTriangleAlert],
  templateUrl: './barra-caja.html',
  styleUrl: './barra-caja.css',
})
export class BarraCaja {
  private pendientesService = inject(OperacionesPendientesService);

  /** Operaciones cobradas/registradas que todavía no confirmó el servidor. */
  readonly pendientes = this.pendientesService.pendientes;
  /** Las que el servidor rechazó: hay que resolverlas con un admin, no se reintentan solas. */
  readonly conError = this.pendientesService.conError;

  /** El rechazo ya quedó guardado del lado del servidor (ver "Operaciones rechazadas" en Acciones):
   * descartarlo acá sólo saca el cartel de este navegador, no borra el registro que puede ver
   * un admin desde cualquier otra máquina. */
  descartarErrores(): void {
    for (const e of this.conError()) {
      this.pendientesService.descartar(e.idempotencyKey);
    }
  }
}
