import {ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {TipoEntradaService} from '../Services/tipo-entrada.service';
import {TipoEntrada} from '../models/tipo-entrada';
import {CurrencyPipe} from '@angular/common';
import {CuponService} from '../Services/cupon.service';
import {Cupon} from '../models/cupon';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-seleccion-entradas',
  imports: [
    CurrencyPipe,
    FormsModule
  ],
  templateUrl: './seleccion-entradas.html',
  styleUrl: './seleccion-entradas.css',
})
export class SeleccionEntradas implements OnInit {

  constructor(
    private tipoEntradaService: TipoEntradaService,
    private cuponService: CuponService,
    private cdr: ChangeDetectorRef
  ) {}

  tiposEntrada: TipoEntrada[] = [];
  cantidades: { [id: number]: number } = {};
  cargando: boolean = true;

  codigoInput: string = '';
  cuponAplicado: Cupon | null = null;
  errorCupon: string | null = null;

  @Input() fechaSeleccionada: Date | null = null;
  @Input() esRegalo: boolean = false;
  @Input() entradasPrevias: any[] | null = null;
  @Input() cuponCodigoPrevio: string | null | undefined = null;

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

        if (this.cuponCodigoPrevio) {
          this.codigoInput = this.cuponCodigoPrevio;
          this.aplicarCupon();
        }
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
  get descuento(): number {
    if (!this.cuponAplicado || this.subtotal === 0) return 0;

    if (this.cuponAplicado.porcentajeDescuento) {
      return (this.subtotal * this.cuponAplicado.porcentajeDescuento) / 100;
    }
    if (this.cuponAplicado.montoDescuento) {
      return Math.min(this.cuponAplicado.montoDescuento, this.subtotal);
    }
    return 0;
  }
  get total(): number {
    return Math.max(0, this.subtotal - this.descuento);
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
      cuponCodigo: this.cuponAplicado?.codigo || null,
      descuentoMonto: this.descuento,
      subtotal: this.subtotal,
      total: this.total
    });
  }
  aplicarCupon(): void {
    if (!this.codigoInput.trim()) return;

    this.errorCupon = null;
    this.cuponService.validarCupon(this.codigoInput.toUpperCase().trim()).subscribe({
      next: (cupon) => {
        if (cupon && cupon.activo) {
          this.cuponAplicado = cupon;
          this.errorCupon = null;
        } else {
          this.errorCupon = 'El cupón no se encuentra activo.';
          this.cuponAplicado = null;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error(err);
        this.errorCupon = 'Cupón inválido o expirado.';
        this.cuponAplicado = null;
        this.cdr.detectChanges();
      }
    });
  }
  removerCupon(): void {
    this.cuponAplicado = null;
    this.codigoInput = '';
    this.errorCupon = null;
  }
}
