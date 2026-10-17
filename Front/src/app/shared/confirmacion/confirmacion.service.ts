import { Injectable, signal } from '@angular/core';

export interface PedidoConfirmacion {
  titulo: string;
  mensaje: string;
  /** Texto del botón que confirma ("Cancelar la venta", "Devolver"). Por defecto "Confirmar". */
  aceptar?: string;
  /** Texto del botón que no hace nada. Por defecto "Volver". */
  cancelar?: string;
  /** Acción que no se deshace (cancelar, devolver plata): el botón de confirmar va en rojo. */
  peligro?: boolean;
}

/**
 * Diálogo de confirmación de la app, en lugar de window.confirm: ése es
 * una ventana del navegador, sin el estilo ni el tema de la app, y en la tablet del POS (PWA) se
 * ve como un error del sistema. Para avisar algo que ya pasó (lo que era window.alert) está
 * ToastService. Lo muestra ConfirmacionDialogo, montado una sola vez en App.
 *
 *   if (!(await this.confirmacion.pedir({ titulo: 'Cancelar venta', mensaje: '...', peligro: true }))) return;
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  readonly pedido = signal<PedidoConfirmacion | null>(null);
  private resolver: ((ok: boolean) => void) | null = null;

  /** true si confirmó; false si eligió volver o cerró el diálogo. */
  pedir(pedido: PedidoConfirmacion): Promise<boolean> {
    // Uno a la vez: si quedaba otro abierto, cuenta como "no".
    this.resolver?.(false);
    this.pedido.set(pedido);
    return new Promise<boolean>((resolve) => (this.resolver = resolve));
  }

  responder(ok: boolean): void {
    const resolver = this.resolver;
    this.resolver = null;
    this.pedido.set(null);
    resolver?.(ok);
  }
}
