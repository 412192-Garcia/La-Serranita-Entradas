import { Component, input, output } from '@angular/core';
import { Modal } from '../modal/modal';

/**
 * El diálogo de confirmación de la app (título, contenido y dos botones), el único: lo usa
 * ConfirmacionService para las confirmaciones de texto y, directo en la plantilla, cualquier
 * pantalla que necesite algo más que un texto adentro (ej. Cajas > "Cerrar caja sin control",
 * que muestra los cobros con Point sin venta). El contenido va proyectado:
 *
 *   <app-dialogo-confirmacion titulo="Cerrar esta caja" aceptar="Cerrar caja" (confirmado)="..." (cancelado)="...">
 *     <p>...</p>
 *   </app-dialogo-confirmacion>
 */
@Component({
  selector: 'app-dialogo-confirmacion',
  imports: [Modal],
  templateUrl: './dialogo-confirmacion.html',
  styleUrl: './dialogo-confirmacion.css',
})
export class DialogoConfirmacion {
  titulo = input.required<string>();
  /** Texto del botón que confirma. */
  aceptar = input('Confirmar');
  /** Texto del botón que no hace nada; vacío = sin ese botón (un aviso con un solo botón). */
  cancelar = input('Volver');
  /** Acción que no se deshace (cancelar, devolver plata): el botón de confirmar va en rojo. */
  peligro = input(false);
  /** Mientras corre la acción: el botón de confirmar queda deshabilitado. */
  ocupado = input(false);

  confirmado = output<void>();
  /** Volver, tocar afuera o Escape. */
  cancelado = output<void>();
}
