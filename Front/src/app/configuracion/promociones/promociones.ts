import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PromocionService } from '../../services/promocion.service';
import { Promocion } from '../../models/promocion';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { compararTexto, compararBooleano } from '../../shared/orden.util';
import { Spinner } from '../../shared/spinner/spinner';
import { crearOrdenable } from '../../shared/ordenable';
import { crearEstadoEdicion } from '../../shared/edicion.util';
import { ColumnaOrdenable } from '../../shared/columna-ordenable/columna-ordenable';
import { PesosPipe } from '../../shared/pesos.pipe';

type CampoOrdenPromocion = 'nombre' | 'estado';

@Component({
  selector: 'app-configuracion-promociones',
  imports: [FormsModule, PesosPipe, MoneyInputDirective, Spinner, ColumnaOrdenable],
  templateUrl: './promociones.html',
  styleUrls: ['../configuracion-shared.css', './promociones.css'],
})
export class ConfiguracionPromociones implements OnInit {
  private promocionService = inject(PromocionService);

  promociones = signal<Promocion[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  mostrarInactivas = signal(false);
  private orden = crearOrdenable<CampoOrdenPromocion>('nombre');
  ordenarColumna = this.orden.ordenarColumna;
  estadoOrden = this.orden.estadoOrden;

  promocionesVisibles = computed(() => {
    const base = this.mostrarInactivas() ? this.promociones() : this.promociones().filter((p) => p.activo);
    const dir = this.orden.direccionOrden() === 'ASC' ? 1 : -1;
    const campo = this.orden.ordenarPor();
    return [...base].sort((a, b) => {
      switch (campo) {
        case 'nombre':
          return compararTexto(a.nombre, b.nombre) * dir;
        case 'estado':
          return compararBooleano(a.activo, b.activo) * dir;
      }
    });
  });

  private edicion = crearEstadoEdicion<number>();
  editandoId = this.edicion.editandoId;
  nombre = signal('');
  tipoDescuento = signal<'porcentaje' | 'monto'>('porcentaje');
  valor = signal<number | null>(null);
  guardando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.promocionService.getPromociones().subscribe({
      next: (ps) => {
        this.promociones.set(ps);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las promociones:', err);
        this.cargando.set(false);
      },
    });
  }

  editar(p: Promocion): void {
    this.edicion.editar(p.id);
    this.nombre.set(p.nombre);
    this.tipoDescuento.set(p.montoDescuento !== null ? 'monto' : 'porcentaje');
    this.valor.set(p.montoDescuento !== null ? p.montoDescuento : p.porcentajeDescuento);
    this.error.set(null);
  }

  cancelarEdicion(): void {
    this.edicion.cancelarEdicion();
    this.nombre.set('');
    this.tipoDescuento.set('porcentaje');
    this.valor.set(null);
    this.error.set(null);
  }

  guardar(): void {
    if (this.guardando()) return;
    if (!this.nombre().trim() || this.valor() === null) {
      this.error.set('Completá nombre y valor del descuento.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);

    const payload = {
      nombre: this.nombre().trim(),
      porcentajeDescuento: this.tipoDescuento() === 'porcentaje' ? this.valor() : null,
      montoDescuento: this.tipoDescuento() === 'monto' ? this.valor() : null,
    };

    const idEditando = this.editandoId();
    const request = idEditando
      ? this.promocionService.actualizar(idEditando, payload)
      : this.promocionService.crear(payload);

    request.subscribe({
      next: (guardada) => {
        this.promociones.update((ps) => {
          const existe = ps.some((p) => p.id === guardada.id);
          return existe ? ps.map((p) => (p.id === guardada.id ? guardada : p)) : [guardada, ...ps];
        });
        this.cancelarEdicion();
        this.guardando.set(false);
      },
      error: (err) => {
        console.error('Error al guardar la promoción:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar la promoción.');
        this.guardando.set(false);
      },
    });
  }

  toggleActivo(p: Promocion): void {
    if (p.activo) {
      this.promocionService.eliminar(p.id).subscribe({
        next: () => this.promociones.update((ps) => ps.map((x) => (x.id === p.id ? { ...x, activo: false } : x))),
        error: (err) => {
          console.error('Error al desactivar la promoción:', err);
          this.error.set('No se pudo desactivar la promoción.');
        },
      });
    } else {
      this.promocionService.actualizar(p.id, { ...p, activo: true }).subscribe({
        next: (actualizada) => this.promociones.update((ps) => ps.map((x) => (x.id === p.id ? actualizada : x))),
        error: (err) => {
          console.error('Error al reactivar la promoción:', err);
          this.error.set('No se pudo reactivar la promoción.');
        },
      });
    }
  }
}
