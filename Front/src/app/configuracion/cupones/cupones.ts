import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfiguracionService, Cupon, FamiliaCupon } from '../../services/configuracion.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { Spinner } from '../../shared/spinner/spinner';

@Component({
  selector: 'app-configuracion-cupones',
  imports: [FormsModule, DatePipe, MoneyInputDirective, Spinner],
  templateUrl: './cupones.html',
  styleUrls: ['../configuracion-shared.css', './cupones.css'],
})
export class ConfiguracionCupones implements OnInit {
  private configuracionService = inject(ConfiguracionService);

  cupones = signal<Cupon[]>([]);
  cargandoCupones = signal(false);
  codigoCupon = signal('');
  tipoDescuentoCupon = signal<'porcentaje' | 'monto'>('porcentaje');
  valorDescuentoCupon = signal<number | null>(null);
  usosMaximosCupon = signal<number | null>(1);
  fechaExpiracionCupon = signal('');
  creandoCupon = signal(false);
  errorCupon = signal<string | null>(null);

  lotes = signal<FamiliaCupon[]>([]);
  cargandoLotes = signal(false);
  loteExpandidoId = signal<number | null>(null);

  nombreLote = signal('');
  prefijoLote = signal('');
  descripcionLote = signal('');
  cantidadLote = signal<number | null>(10);
  usosMaximosLote = signal<number | null>(1);
  tipoDescuentoLote = signal<'porcentaje' | 'monto'>('porcentaje');
  valorDescuentoLote = signal<number | null>(null);
  fechaExpiracionLote = signal('');
  generandoLote = signal(false);
  errorLote = signal<string | null>(null);

  totalCuponesGenerados = computed(() =>
    this.lotes().reduce((acc, f) => acc + (f.cupones?.length ?? 0), 0)
  );

  ngOnInit(): void {
    this.cargarCupones();
    this.cargarLotes();
  }

  cargarCupones(): void {
    this.cargandoCupones.set(true);
    this.configuracionService.getCupones().subscribe({
      next: (cs) => {
        this.cupones.set(cs);
        this.cargandoCupones.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los cupones:', err);
        this.cargandoCupones.set(false);
      },
    });
  }

  crearCupon(): void {
    if (this.creandoCupon()) return;
    this.creandoCupon.set(true);
    this.errorCupon.set(null);

    const esPorcentaje = this.tipoDescuentoCupon() === 'porcentaje';
    this.configuracionService
      .crearCupon({
        codigo: this.codigoCupon().trim() || null,
        usosMaximos: this.usosMaximosCupon(),
        fechaExpiracion: this.fechaExpiracionCupon() || null,
        porcentajeDescuento: esPorcentaje ? this.valorDescuentoCupon() : null,
        montoDescuento: esPorcentaje ? null : this.valorDescuentoCupon(),
      })
      .subscribe({
        next: (nuevo) => {
          this.cupones.update((cs) => [nuevo, ...cs]);
          this.codigoCupon.set('');
          this.valorDescuentoCupon.set(null);
          this.usosMaximosCupon.set(1);
          this.fechaExpiracionCupon.set('');
          this.creandoCupon.set(false);
        },
        error: (err) => {
          console.error('Error al crear el cupón:', err);
          this.errorCupon.set(
            typeof err?.error === 'string' ? err.error : 'No se pudo crear el cupón.'
          );
          this.creandoCupon.set(false);
        },
      });
  }

  cargarLotes(): void {
    this.cargandoLotes.set(true);
    this.configuracionService.getFamilias().subscribe({
      next: (ls) => {
        this.lotes.set(ls);
        this.cargandoLotes.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los lotes de cupones:', err);
        this.cargandoLotes.set(false);
      },
    });
  }

  generarLote(): void {
    if (this.generandoLote()) return;
    if (!this.nombreLote().trim() || !this.prefijoLote().trim() || !this.cantidadLote()) {
      this.errorLote.set('Completá nombre, prefijo y cantidad.');
      return;
    }
    this.generandoLote.set(true);
    this.errorLote.set(null);

    const esPorcentaje = this.tipoDescuentoLote() === 'porcentaje';
    this.configuracionService
      .generarFamilia({
        nombre: this.nombreLote().trim(),
        prefijo: this.prefijoLote().trim().toUpperCase(),
        descripcion: this.descripcionLote().trim() || null,
        cantidad: this.cantidadLote()!,
        usosMaximos: this.usosMaximosLote(),
        fechaExpiracion: this.fechaExpiracionLote() || null,
        porcentajeDescuento: esPorcentaje ? this.valorDescuentoLote() : null,
        montoDescuento: esPorcentaje ? null : this.valorDescuentoLote(),
      })
      .subscribe({
        next: (nuevo) => {
          this.lotes.update((ls) => [nuevo, ...ls]);
          this.loteExpandidoId.set(nuevo.id);
          this.nombreLote.set('');
          this.prefijoLote.set('');
          this.descripcionLote.set('');
          this.cantidadLote.set(10);
          this.valorDescuentoLote.set(null);
          this.fechaExpiracionLote.set('');
          this.generandoLote.set(false);
        },
        error: (err) => {
          console.error('Error al generar el lote de cupones:', err);
          this.errorLote.set(
            typeof err?.error === 'string' ? err.error : 'No se pudo generar el lote de cupones.'
          );
          this.generandoLote.set(false);
        },
      });
  }

  toggleLoteExpandido(id: number): void {
    this.loteExpandidoId.update((actual) => (actual === id ? null : id));
  }

  descuentoLegible(item: { porcentajeDescuento: number | null; montoDescuento: number | null }): string {
    if (item.porcentajeDescuento) return `${item.porcentajeDescuento}%`;
    if (item.montoDescuento) return `$${item.montoDescuento}`;
    return '—';
  }
}
