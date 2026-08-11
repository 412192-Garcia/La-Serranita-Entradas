import { Component, computed, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/** Filtro Desde/Hasta reutilizado por Reportes y Cajas: mismo estado, misma validación,
 *  mismo botón Aplicar — antes duplicado byte a byte entre los dos componentes. */
@Component({
  selector: 'app-filtro-rango-fechas',
  imports: [FormsModule],
  templateUrl: './filtro-rango-fechas.html',
  styleUrl: './filtro-rango-fechas.css',
})
export class FiltroRangoFechas {
  desde = model.required<string>();
  hasta = model.required<string>();
  subtitulo = input('Por defecto muestra los últimos 30 días.');
  ancho = input(false);
  cargando = input(false);

  /** Sólo se emite si el rango es válido: el consumidor no necesita repetir esta validación. */
  aplicar = output<void>();

  errorRango = computed<string | null>(() => {
    if (!this.desde() || !this.hasta()) return 'El rango de fechas es inválido.';
    return this.hasta() < this.desde() ? 'El rango de fechas es inválido.' : null;
  });

  onAplicar(): void {
    if (this.errorRango()) return;
    this.aplicar.emit();
  }
}
