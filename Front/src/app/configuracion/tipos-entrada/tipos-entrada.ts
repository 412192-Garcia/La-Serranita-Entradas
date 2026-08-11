import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfiguracionService, DescuentoEfectivo } from '../../services/configuracion.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { TipoEntrada } from '../../models/tipo-entrada';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { compararTexto, compararNumero, compararBooleano } from '../../shared/orden.util';
import { Spinner } from '../../shared/spinner/spinner';
import { crearOrdenable } from '../../shared/ordenable';
import { crearEstadoEdicion } from '../../shared/edicion.util';
import { ColumnaOrdenable } from '../../shared/columna-ordenable/columna-ordenable';
import { PesosPipe } from '../../shared/pesos.pipe';

type CampoOrdenTipoEntrada = 'nombre' | 'tipo' | 'precio' | 'maximoPorDia' | 'estado';

@Component({
  selector: 'app-configuracion-tipos-entrada',
  imports: [FormsModule, PesosPipe, MoneyInputDirective, Spinner, ColumnaOrdenable],
  templateUrl: './tipos-entrada.html',
  styleUrls: ['../configuracion-shared.css', './tipos-entrada.css'],
})
export class ConfiguracionTiposEntrada implements OnInit {
  private configuracionService = inject(ConfiguracionService);
  private tipoEntradaService = inject(TipoEntradaService);

  tiposEntrada = signal<TipoEntrada[]>([]);
  cargandoTipos = signal(false);
  errorTipos = signal<string | null>(null);

  mostrarInactivos = signal(false);
  private orden = crearOrdenable<CampoOrdenTipoEntrada>('nombre');
  ordenarColumna = this.orden.ordenarColumna;
  estadoOrden = this.orden.estadoOrden;

  tiposEntradaVisibles = computed(() => {
    const base = this.mostrarInactivos() ? this.tiposEntrada() : this.tiposEntrada().filter((t) => t.activo);
    const dir = this.orden.direccionOrden() === 'ASC' ? 1 : -1;
    const campo = this.orden.ordenarPor();
    return [...base].sort((a, b) => {
      switch (campo) {
        case 'nombre':
          return compararTexto(a.nombre, b.nombre) * dir;
        case 'tipo':
          return compararTexto(a.tipo, b.tipo) * dir;
        case 'precio':
          return compararNumero(a.precio, b.precio) * dir;
        case 'maximoPorDia':
          return compararNumero(a.maximoPorDia ?? null, b.maximoPorDia ?? null) * dir;
        case 'estado':
          return compararBooleano(a.activo, b.activo) * dir;
      }
    });
  });

  private edicionTipo = crearEstadoEdicion<number>();
  tipoEditandoId = this.edicionTipo.editandoId;
  nombreTipo = signal('');
  descripcionTipo = signal('');
  precioTipo = signal<number | null>(null);
  categoriaTipo = signal<'ENTRADA' | 'EXTRA'>('ENTRADA');
  obligatorioTipo = signal(false);
  entregaEntradaTipo = signal(false);
  maximoPorDiaTipo = signal<number | null>(null);
  guardandoTipo = signal(false);

  descuentosGrupo = signal<DescuentoEfectivo[]>([]);
  cargandoDescuentos = signal(false);
  errorDescuentos = signal<string | null>(null);

  private edicionDescuento = crearEstadoEdicion<number>();
  descuentoEditandoId = this.edicionDescuento.editandoId;
  cantidadPasesDescuento = signal<number | null>(null);
  precioTotalDescuento = signal<number | null>(null);
  guardandoDescuento = signal(false);

  /** Precios de grupo del tipo de entrada que se está editando ahora mismo (se gestionan dentro de su propio formulario). */
  descuentosDelTipoActual = computed(() =>
    this.descuentosGrupo().filter((d) => d.tipoEntradaId === this.tipoEditandoId())
  );

  ngOnInit(): void {
    this.cargarTipos();
    this.cargarDescuentosGrupo();
  }

  cargarTipos(): void {
    this.cargandoTipos.set(true);
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (ts) => {
        this.tiposEntrada.set(ts);
        this.cargandoTipos.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los tipos de entrada:', err);
        this.cargandoTipos.set(false);
      },
    });
  }

  editarTipo(t: TipoEntrada): void {
    this.edicionTipo.editar(t.id);
    this.nombreTipo.set(t.nombre);
    this.descripcionTipo.set(t.descripcion ?? '');
    this.precioTipo.set(t.precio);
    this.categoriaTipo.set(t.tipo);
    this.obligatorioTipo.set(t.obligatorio);
    this.entregaEntradaTipo.set(t.entregaEntrada);
    this.maximoPorDiaTipo.set(t.maximoPorDia ?? null);
    this.errorTipos.set(null);
    this.cancelarEdicionDescuento();
  }

  cancelarEdicionTipo(): void {
    this.edicionTipo.cancelarEdicion();
    this.nombreTipo.set('');
    this.descripcionTipo.set('');
    this.precioTipo.set(null);
    this.categoriaTipo.set('ENTRADA');
    this.obligatorioTipo.set(false);
    this.entregaEntradaTipo.set(false);
    this.maximoPorDiaTipo.set(null);
    this.errorTipos.set(null);
    this.cancelarEdicionDescuento();
  }

  guardarTipo(): void {
    if (this.guardandoTipo()) return;
    if (!this.nombreTipo().trim() || this.precioTipo() === null) {
      this.errorTipos.set('Completá nombre y precio.');
      return;
    }
    this.guardandoTipo.set(true);
    this.errorTipos.set(null);

    const payload = {
      nombre: this.nombreTipo().trim(),
      descripcion: this.descripcionTipo().trim(),
      precio: this.precioTipo()!,
      tipo: this.categoriaTipo(),
      obligatorio: this.categoriaTipo() === 'ENTRADA' ? this.obligatorioTipo() : false,
      entregaEntrada: this.categoriaTipo() === 'ENTRADA' ? this.entregaEntradaTipo() : false,
      maximoPorDia: this.maximoPorDiaTipo(),
      activo: true,
    };

    const idEditando = this.tipoEditandoId();
    const request = idEditando
      ? this.tipoEntradaService.actualizar(idEditando, payload)
      : this.tipoEntradaService.crear(payload);

    request.subscribe({
      next: (guardado) => {
        this.tiposEntrada.update((ts) => {
          const existe = ts.some((t) => t.id === guardado.id);
          return existe ? ts.map((t) => (t.id === guardado.id ? guardado : t)) : [guardado, ...ts];
        });
        this.guardandoTipo.set(false);
        // Las entradas (no los extras) admiten precios de grupo: se queda en modo edición
        // para que el admin pueda cargarlos sin tener que volver a buscar el tipo recién creado.
        if (guardado.tipo === 'ENTRADA') {
          this.editarTipo(guardado);
        } else {
          this.cancelarEdicionTipo();
        }
      },
      error: (err) => {
        console.error('Error al guardar el tipo de entrada:', err);
        this.errorTipos.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo guardar el tipo de entrada.'
        );
        this.guardandoTipo.set(false);
      },
    });
  }

  toggleActivoTipo(t: TipoEntrada): void {
    if (t.activo) {
      this.tipoEntradaService.eliminar(t.id).subscribe({
        next: () => {
          this.tiposEntrada.update((ts) =>
            ts.map((x) => (x.id === t.id ? { ...x, activo: false } : x))
          );
        },
        error: (err) => {
          console.error('Error al desactivar el tipo de entrada:', err);
          this.errorTipos.set('No se pudo desactivar el tipo de entrada.');
        },
      });
    } else {
      this.tipoEntradaService.actualizar(t.id, { ...t, activo: true }).subscribe({
        next: (actualizado) => {
          this.tiposEntrada.update((ts) => ts.map((x) => (x.id === t.id ? actualizado : x)));
        },
        error: (err) => {
          console.error('Error al reactivar el tipo de entrada:', err);
          this.errorTipos.set('No se pudo reactivar el tipo de entrada.');
        },
      });
    }
  }

  // ---------- Precios por grupo (efectivo) ----------

  cargarDescuentosGrupo(): void {
    this.cargandoDescuentos.set(true);
    this.configuracionService.getDescuentosEfectivo().subscribe({
      next: (ds) => {
        this.descuentosGrupo.set(ds);
        this.cargandoDescuentos.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los precios por grupo:', err);
        this.cargandoDescuentos.set(false);
      },
    });
  }

  editarDescuento(d: DescuentoEfectivo): void {
    this.edicionDescuento.editar(d.id);
    this.cantidadPasesDescuento.set(d.cantidadPases);
    this.precioTotalDescuento.set(d.precioPromocionalTotal);
    this.errorDescuentos.set(null);
  }

  cancelarEdicionDescuento(): void {
    this.edicionDescuento.cancelarEdicion();
    this.cantidadPasesDescuento.set(null);
    this.precioTotalDescuento.set(null);
    this.errorDescuentos.set(null);
  }

  guardarDescuento(): void {
    if (this.guardandoDescuento()) return;
    const tipoEntradaId = this.tipoEditandoId();
    if (!tipoEntradaId || !this.cantidadPasesDescuento() || this.precioTotalDescuento() === null) {
      this.errorDescuentos.set('Completá cantidad de pases y precio total.');
      return;
    }
    this.guardandoDescuento.set(true);
    this.errorDescuentos.set(null);

    const payload = {
      tipoEntradaId,
      cantidadPases: this.cantidadPasesDescuento()!,
      precioPromocionalTotal: this.precioTotalDescuento()!,
    };

    const idEditando = this.descuentoEditandoId();
    const request = idEditando
      ? this.configuracionService.actualizarDescuentoEfectivo(idEditando, payload)
      : this.configuracionService.crearDescuentoEfectivo(payload);

    request.subscribe({
      next: (guardado) => {
        this.descuentosGrupo.update((ds) => {
          const existe = ds.some((d) => d.id === guardado.id);
          return existe ? ds.map((d) => (d.id === guardado.id ? guardado : d)) : [guardado, ...ds];
        });
        this.cancelarEdicionDescuento();
        this.guardandoDescuento.set(false);
      },
      error: (err) => {
        console.error('Error al guardar el precio de grupo:', err);
        this.errorDescuentos.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo guardar el precio de grupo.'
        );
        this.guardandoDescuento.set(false);
      },
    });
  }

  eliminarDescuento(d: DescuentoEfectivo): void {
    this.configuracionService.eliminarDescuentoEfectivo(d.id).subscribe({
      next: () => {
        this.descuentosGrupo.update((ds) => ds.filter((x) => x.id !== d.id));
        if (this.descuentoEditandoId() === d.id) {
          this.cancelarEdicionDescuento();
        }
      },
      error: (err) => {
        console.error('Error al eliminar el precio de grupo:', err);
        this.errorDescuentos.set('No se pudo eliminar el precio de grupo.');
      },
    });
  }

  /** Cuánto ahorra el grupo respecto de pagar cada pase por separado a precio de lista. */
  ahorroDescuento(d: DescuentoEfectivo): number {
    const tipo = this.tiposEntrada().find((t) => t.id === d.tipoEntradaId);
    if (!tipo) return 0;
    return Math.max(0, tipo.precio * d.cantidadPases - d.precioPromocionalTotal);
  }

  /** Precio total dividido por la cantidad de pases: es lo que se extrapola para grupos aún más grandes. */
  precioPorPersona(d: DescuentoEfectivo): number {
    return d.cantidadPases > 0 ? d.precioPromocionalTotal / d.cantidadPases : 0;
  }
}
