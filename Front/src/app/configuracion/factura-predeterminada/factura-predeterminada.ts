import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../shared/toast/toast.service';
import { Spinner } from '../../shared/spinner/spinner';
import { FacturaService } from '../../services/factura.service';
import { FacturaPredeterminada } from '../../models/factura';

/**
 * Configuración > Cobros (ADMIN): qué factura arranca marcada en el POS al elegir cada forma de
 * pago. El boletero la puede cambiar en cada venta; esto es sólo el punto de partida. Se guarda
 * al elegir, sin botón.
 */
@Component({
  selector: 'app-configuracion-factura-predeterminada',
  imports: [FormsModule, Spinner],
  templateUrl: './factura-predeterminada.html',
  styleUrls: ['../configuracion-shared.css', './factura-predeterminada.css'],
})
export class ConfiguracionFacturaPredeterminada implements OnInit {
  private facturaService = inject(FacturaService);
  private toast = inject(ToastService);

  /** Con el submódulo Facturación > Boletería apagado el POS no factura: se avisa, pero se deja configurar igual. */
  readonly facturacionBoleteria = this.facturaService.habilitadaBoleteria;

  readonly formas: { clave: keyof FacturaPredeterminada; etiqueta: string }[] = [
    { clave: 'efectivo', etiqueta: 'Efectivo' },
    { clave: 'tarjeta', etiqueta: 'Tarjeta' },
    { clave: 'qr', etiqueta: 'QR' },
  ];

  actual = signal<FacturaPredeterminada | null>(null);
  error = signal<string | null>(null);
  guardando = signal(false);

  ngOnInit(): void {
    this.facturaService.actualizarEstadoServicio();
    this.facturaService.obtenerPredeterminada().subscribe({
      next: (p) => this.actual.set(p),
      error: (err) =>
        this.error.set(this.mensaje(err, 'No se pudo cargar la factura por defecto.')),
    });
  }

  cambiar(clave: keyof FacturaPredeterminada, destino: 'IMPRIMIR' | 'MAIL'): void {
    const anterior = this.actual();
    if (!anterior || anterior[clave] === destino) return;
    const nueva = { ...anterior, [clave]: destino };
    this.actual.set(nueva);
    this.guardando.set(true);
    this.facturaService.guardarPredeterminada(nueva).subscribe({
      next: (guardada) => {
        this.guardando.set(false);
        this.actual.set(guardada);
        this.toast.exito('Factura por defecto guardada.');
      },
      error: (err) => {
        this.guardando.set(false);
        this.actual.set(anterior);
        this.toast.error(this.mensaje(err, 'No se pudo guardar la factura por defecto.'));
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
