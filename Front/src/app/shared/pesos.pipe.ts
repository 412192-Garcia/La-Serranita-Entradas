import { Pipe, PipeTransform, inject, LOCALE_ID } from '@angular/core';
import { CurrencyPipe } from '@angular/common';

@Pipe({
  name: 'pesos',
})
export class PesosPipe implements PipeTransform {
  private currencyPipe = new CurrencyPipe(inject(LOCALE_ID));

  transform(valor: number | null | undefined): string | null {
    return this.currencyPipe.transform(valor, 'ARS', 'symbol', '1.0-0');
  }
}
