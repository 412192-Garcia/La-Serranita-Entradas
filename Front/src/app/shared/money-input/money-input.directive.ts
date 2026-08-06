import { Directive, ElementRef, Input, Renderer2, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

const FORMATO_PESOS = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

/**
 * Formatea pesos enteros con punto de miles mientras se tipea (ej. "10.000"),
 * igual que el CurrencyPipe (1.0-0) ya muestra en toda la app. Se aplica junto
 * al [ngModel] existente: el input debe ser type="text" (type="number" no
 * puede mostrar el punto de miles). El valor que sube por ngModelChange sigue
 * siendo el número limpio (sin puntos), no el texto formateado.
 *
 * Algunos campos sirven tanto para pesos como para porcentaje (ej. cupones,
 * promos: un mismo input cambia de significado según un selector "% / $" al
 * lado). En esos casos se puede pasar [appMoneyInput]="esModoMonto()" para
 * desactivar el formateo cuando no corresponde.
 */
@Directive({
  selector: '[appMoneyInput]',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MoneyInputDirective),
      multi: true,
    },
  ],
  host: {
    '(input)': 'onInput($event)',
    '(blur)': 'onTouched()',
  },
})
export class MoneyInputDirective implements ControlValueAccessor {
  @Input('appMoneyInput') habilitado: boolean | '' = true;

  private onChange: (valor: number | null) => void = () => {};
  onTouched: () => void = () => {};

  constructor(private el: ElementRef<HTMLInputElement>, private renderer: Renderer2) {}

  private get activo(): boolean {
    return this.habilitado !== false;
  }

  writeValue(valor: number | null): void {
    if (!this.activo) {
      this.renderer.setProperty(this.el.nativeElement, 'value', valor ?? '');
      return;
    }
    this.renderer.setProperty(this.el.nativeElement, 'value', this.formatear(valor));
  }

  registerOnChange(fn: (valor: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.renderer.setProperty(this.el.nativeElement, 'disabled', deshabilitado);
  }

  onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!this.activo) {
      const valorCrudo = input.value === '' ? null : Number(input.value);
      this.onChange(Number.isNaN(valorCrudo as number) ? null : valorCrudo);
      return;
    }
    const soloDigitos = input.value.replace(/\D/g, '');
    const valor = soloDigitos ? Number(soloDigitos) : null;
    input.value = this.formatear(valor);
    this.onChange(valor);
  }

  private formatear(valor: number | null): string {
    return valor === null || valor === undefined || Number.isNaN(valor) ? '' : FORMATO_PESOS.format(valor);
  }
}
