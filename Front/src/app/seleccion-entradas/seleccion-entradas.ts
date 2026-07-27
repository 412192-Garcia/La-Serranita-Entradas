import {ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
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
export class SeleccionEntradas implements OnInit {

  constructor(
    private tipoEntradaService: TipoEntradaService,
    private cdr: ChangeDetectorRef
  ) {}

  tiposEntrada: TipoEntrada[] = [];
  cantidades: { [id: number]: number } = {};
  cargando: boolean = true;

  @Input() fechaSeleccionada: Date | null = null;
  @Input() esRegalo: boolean = false;
  @Input() entradasPrevias: any[] | null = null;

  @Output() pasoSiguiente = new EventEmitter<any>();

  ngOnInit() {
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (data) => {
        this.tiposEntrada = data.filter(t => t.activo);

        this.tiposEntrada.forEach(t => this.cantidades[t.id] = 0);

        if (this.entradasPrevias) {
          this.entradasPrevias.forEach(item => {
            const id = item.id ?? item.tipoEntradaId;
            if (id != null && this.cantidades[id] !== undefined) {
              this.cantidades[id] = item.cantidad;
            }
          });
        }

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
  get subtotal(): number {
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
  get cantidadEntradasSeleccionadas(): number {
    return this.entradas.reduce((sum, tipo) => {
      return sum + (this.cantidades[tipo.id] || 0);
    }, 0);
  }


  get esPasoValido(): boolean {
    const tieneFechaOEsRegalo = this.esRegalo || this.fechaSeleccionada !== null;
    const tieneAlMenosUnaEntrada = this.cantidadEntradasSeleccionadas > 0;
    const tieneMontoValido = this.subtotal > 0;

    return tieneFechaOEsRegalo && tieneAlMenosUnaEntrada && tieneMontoValido;
  }

  onSiguiente(): void {
    const itemsSeleccionados = this.tiposEntrada
      .filter(t => this.cantidades[t.id] > 0)
      .map(t => ({
        id: t.id,
        nombre: t.nombre,
        precioUnitario: t.precio,
        cantidad: this.cantidades[t.id],
        subtotal: this.cantidades[t.id] * t.precio
      }));

    this.pasoSiguiente.emit({
      fechaVisita: this.esRegalo ? null : this.fechaSeleccionada,
      esRegalo: this.esRegalo,
      entradas: itemsSeleccionados,
      subtotal: this.subtotal
    });
  }
}
