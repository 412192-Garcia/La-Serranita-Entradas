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

  validarReserva = output<void>();
  retirarEfectivo = output<void>();
  agregarEntradas = output<void>();
}
