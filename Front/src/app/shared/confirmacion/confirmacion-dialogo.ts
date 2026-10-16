import { Component, inject } from '@angular/core';
import { DialogoConfirmacion } from './dialogo-confirmacion';
import { ConfirmacionService } from './confirmacion.service';

/** Muestra los pedidos de ConfirmacionService con el diálogo de la app. Va una sola vez, en App. */
@Component({
  selector: 'app-confirmacion-dialogo',
  imports: [DialogoConfirmacion],
  templateUrl: './confirmacion-dialogo.html',
})
export class ConfirmacionDialogo {
  readonly confirmacion = inject(ConfirmacionService);
}
