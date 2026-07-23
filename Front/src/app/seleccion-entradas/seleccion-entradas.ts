import {ChangeDetectorRef, Component, EventEmitter, Output} from '@angular/core';
import {DiaAperturaService} from '../Services/dia-apertura.service';
import {TipoEntradaService} from '../Services/tipo-entrada.service';
import {TipoEntrada} from '../models/tipo-entrada';
import {CurrencyPipe} from '@angular/common';

@Component({
  selector: 'app-seleccion-entradas',
  imports: [
    CurrencyPipe
  ],
  templateUrl: './seleccion-entradas.html',
  styleUrl: './seleccion-entradas.css',
})
export class SeleccionEntradas {

  constructor(
    private tipoEntradaService: TipoEntradaService,
    private cdr: ChangeDetectorRef
  ) {}

  tiposEntrada: TipoEntrada[] = [];
  cantidades: { [id: number]: number } = {};
  cargando: boolean = true;

  @Output() pasoSiguiente = new EventEmitter<any>();

  ngOnInit() {
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (data) => {
        this.tiposEntrada = data.filter(t => t.activo);

        this.tiposEntrada.forEach(t => this.cantidades[t.id] = 0);

        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al traer los tipos de entrada:', err);
        this.cargando = false;
        this.cdr.detectChanges();
      }
    })
  }

  cambiarCantidad(id: number, incremento: number): void {
    const cantidadActual = this.cantidades[id] || 0;
    this.cantidades[id] = Math.max(0, cantidadActual + incremento);
  }
  getCantidad(id: number): number {
    return this.cantidades[id] || 0;
  }
  get total(): number {
    return this.tiposEntrada.reduce((sum, tipo) => {
      const cant = this.cantidades[tipo.id] || 0;
      return sum + (cant * tipo.precio);
    }, 0);
  }
  get entradas(): TipoEntrada[] {
    return this.tiposEntrada.filter(t => t.tipo === 'ENTRADA');
  }
  get extras(): TipoEntrada[] {
    return this.tiposEntrada.filter(t => t.tipo === 'EXTRA');
  }

  onSiguiente(): void {
    const itemsSeleccionados = this.tiposEntrada
      .filter(t => this.cantidades[t.id] > 0)
      .map(t => ({
        id: t.id,
        nombre: t.nombre,
        precio: t.precio,
        cantidad: this.cantidades[t.id],
        subtotal: this.cantidades[t.id] * t.precio
      }));

    this.pasoSiguiente.emit({
      entradas: itemsSeleccionados,
      totalFinal: this.total
    });
  }
}
