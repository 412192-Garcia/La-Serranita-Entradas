import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfiguracionService, Cupon, FamiliaCupon } from '../../services/configuracion.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { TipoEntrada } from '../../models/tipo-entrada';
import { TutorialTarjeta } from '../../shared/tutorial-tarjeta/tutorial-tarjeta';
import { pasosCuponIndividual, pasosLote } from './cupones-tutorial';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { Spinner } from '../../shared/spinner/spinner';

/** Cómo se descuenta: un porcentaje, un monto restado a la compra o a cada entrada, o un precio fijo por entrada. */
type TipoDescuento = 'porcentaje' | 'monto' | 'monto-entrada' | 'precio-entrada';

@Component({
  selector: 'app-configuracion-cupones',
  imports: [FormsModule, DatePipe, MoneyInputDirective, Spinner, TutorialTarjeta],
  templateUrl: './cupones.html',
  styleUrls: ['../configuracion-shared.css', './cupones.css'],
})
export class ConfiguracionCupones implements OnInit {
  private configuracionService = inject(ConfiguracionService);
  private tipoEntradaService = inject(TipoEntradaService);

  /** Para elegir a qué tipos de entrada alcanza un cupón. */
  tiposEntrada = signal<TipoEntrada[]>([]);

  cupones = signal<Cupon[]>([]);
  cargandoCupones = signal(false);
  codigoCupon = signal('');
  tipoDescuentoCupon = signal<TipoDescuento>('porcentaje');
  valorDescuentoCupon = signal<number | null>(null);
  usosMaximosCupon = signal<number | null>(null);
  fechaExpiracionCupon = signal('');
  fechaDesdeCupon = signal('');
  minEntradasCupon = signal<number | null>(null);
  maxEntradasCupon = signal<number | null>(null);
  topeCupon = signal<number | null>(null);
  tiposCupon = signal<number[]>([]);
  creandoCupon = signal(false);
  errorCupon = signal<string | null>(null);

  lotes = signal<FamiliaCupon[]>([]);
  cargandoLotes = signal(false);
  loteExpandidoId = signal<number | null>(null);

  nombreLote = signal('');
  prefijoLote = signal('');
  descripcionLote = signal('');
  cantidadLote = signal<number | null>(10);
  usosMaximosLote = signal<number | null>(null);
  tipoDescuentoLote = signal<TipoDescuento>('porcentaje');
  valorDescuentoLote = signal<number | null>(null);
  fechaExpiracionLote = signal('');
  fechaDesdeLote = signal('');
  minEntradasLote = signal<number | null>(null);
  maxEntradasLote = signal<number | null>(null);
  topeLote = signal<number | null>(null);
  tiposLote = signal<number[]>([]);
  generandoLote = signal(false);
  errorLote = signal<string | null>(null);

  /** Edición de un cupón individual en la tabla (usos y vencimiento). */
  editandoId = signal<number | null>(null);
  editUsos = signal<number | null>(null);
  editVence = signal('');
  guardandoCupon = signal(false);
  errorTabla = signal<string | null>(null);

  /** Acciones sobre el lote expandido (activar/desactivar, vencimiento). */
  vencimientoLote = signal('');
  accionandoLote = signal(false);
  errorAccionLote = signal<string | null>(null);

  /** El paso de "Tipos de entrada" sólo existe si el formulario muestra esa sección (más de un tipo con valor). */
  pasosTutorialCupon = computed(() => pasosCuponIndividual(this.tiposEntrada().length > 1));
  pasosTutorialLote = computed(() => pasosLote(this.tiposEntrada().length > 1));

  totalCuponesGenerados = computed(() =>
    this.lotes().reduce((acc, f) => acc + (f.cupones?.length ?? 0), 0)
  );

  ngOnInit(): void {
    this.cargarCupones();
    this.cargarLotes();
    this.tipoEntradaService.getTiposEntrada().subscribe({
      // Sólo las entradas con valor: las gratis no tienen nada que descontar.
      next: (ts) => {
        this.tiposEntrada.set(ts.filter((t) => t.tipo === 'ENTRADA' && t.precio > 0));
        this.tildarTodosLosTipos();
      },
      error: (err) => console.error('Error al cargar los tipos de entrada:', err),
    });
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
    if (this.faltanTipos(this.tiposCupon())) {
      this.errorCupon.set('Tildá al menos un tipo de entrada.');
      return;
    }
    this.creandoCupon.set(true);
    this.errorCupon.set(null);

    const esPorcentaje = this.tipoDescuentoCupon() === 'porcentaje';
    this.configuracionService
      .crearCupon({
        codigo: this.codigoCupon().trim() || null,
        usosMaximos: this.usosMaximosCupon() || null,
        fechaExpiracion: this.fechaExpiracionCupon() || null,
        porcentajeDescuento: esPorcentaje ? this.valorDescuentoCupon() : null,
        montoDescuento: esPorcentaje ? null : this.valorDescuentoCupon(),
        fechaDesde: this.fechaDesdeCupon() || null,
        aplicaPor: this.aplicaPorDe(this.tipoDescuentoCupon()),
        tiposEntradaIds: this.tiposParaEnviar(this.tiposCupon()),
        minEntradas: this.minEntradasCupon() || null,
        maxEntradasAfectadas: this.maxEntradasCupon() || null,
        topeDescuento: this.topeCupon() || null,
      })
      .subscribe({
        next: (nuevo) => {
          this.cupones.update((cs) => [nuevo, ...cs]);
          this.codigoCupon.set('');
          this.valorDescuentoCupon.set(null);
          this.usosMaximosCupon.set(null);
          this.fechaExpiracionCupon.set('');
          this.fechaDesdeCupon.set('');
          this.minEntradasCupon.set(null);
          this.maxEntradasCupon.set(null);
          this.topeCupon.set(null);
          this.tiposCupon.set(this.idsDeTipos());
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
    if (this.faltanTipos(this.tiposLote())) {
      this.errorLote.set('Tildá al menos un tipo de entrada.');
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
        usosMaximos: this.usosMaximosLote() || null,
        fechaExpiracion: this.fechaExpiracionLote() || null,
        porcentajeDescuento: esPorcentaje ? this.valorDescuentoLote() : null,
        montoDescuento: esPorcentaje ? null : this.valorDescuentoLote(),
        fechaDesde: this.fechaDesdeLote() || null,
        aplicaPor: this.aplicaPorDe(this.tipoDescuentoLote()),
        tiposEntradaIds: this.tiposParaEnviar(this.tiposLote()),
        minEntradas: this.minEntradasLote() || null,
        maxEntradasAfectadas: this.maxEntradasLote() || null,
        topeDescuento: this.topeLote() || null,
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
          this.fechaDesdeLote.set('');
          this.minEntradasLote.set(null);
          this.maxEntradasLote.set(null);
          this.topeLote.set(null);
          this.tiposLote.set(this.idsDeTipos());
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
    this.errorAccionLote.set(null);
    this.loteExpandidoId.update((actual) => (actual === id ? null : id));
    const lote = this.lotes().find((l) => l.id === id);
    this.vencimientoLote.set(lote?.cupones[0]?.fechaExpiracion ?? '');
  }

  // ---------- Cupones individuales: activar/desactivar y editar ----------

  empezarEdicion(c: Cupon): void {
    this.errorTabla.set(null);
    this.editandoId.set(c.id);
    this.editUsos.set(c.usosMaximos);
    this.editVence.set(c.fechaExpiracion ?? '');
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
  }

  guardarEdicion(c: Cupon): void {
    this.guardarCambios(c, { usosMaximos: this.editUsos() || null, fechaExpiracion: this.editVence() || null, activo: c.activo });
  }

  alternarActivo(c: Cupon): void {
    this.guardarCambios(c, { usosMaximos: c.usosMaximos, fechaExpiracion: c.fechaExpiracion, activo: !c.activo });
  }

  private guardarCambios(
    c: Cupon,
    cambios: { usosMaximos: number | null; fechaExpiracion: string | null; activo: boolean },
  ): void {
    if (this.guardandoCupon()) return;
    this.guardandoCupon.set(true);
    this.errorTabla.set(null);
    this.configuracionService.actualizarCupon(c.id, cambios).subscribe({
      next: (actualizado) => {
        this.cupones.update((cs) => cs.map((x) => (x.id === actualizado.id ? actualizado : x)));
        this.editandoId.set(null);
        this.guardandoCupon.set(false);
      },
      error: (err) => {
        console.error('Error al actualizar el cupón:', err);
        this.errorTabla.set(typeof err?.error === 'string' ? err.error : 'No se pudo actualizar el cupón.');
        this.guardandoCupon.set(false);
      },
    });
  }

  // ---------- Lotes: activar/desactivar y vencimiento en bloque ----------

  cuponesActivosDelLote(l: FamiliaCupon): number {
    return l.cupones.filter((c) => c.activo).length;
  }

  alternarActivoLote(l: FamiliaCupon): void {
    const activar = this.cuponesActivosDelLote(l) === 0;
    this.accionarLote(this.configuracionService.cambiarActivoFamilia(l.id, activar));
  }

  guardarVencimientoLote(l: FamiliaCupon): void {
    this.accionarLote(this.configuracionService.cambiarVencimientoFamilia(l.id, this.vencimientoLote() || null));
  }

  private accionarLote(llamada: ReturnType<ConfiguracionService['cambiarActivoFamilia']>): void {
    if (this.accionandoLote()) return;
    this.accionandoLote.set(true);
    this.errorAccionLote.set(null);
    llamada.subscribe({
      next: (actualizado) => {
        this.lotes.update((ls) => ls.map((x) => (x.id === actualizado.id ? actualizado : x)));
        this.accionandoLote.set(false);
      },
      error: (err) => {
        console.error('Error al actualizar el lote:', err);
        this.errorAccionLote.set(typeof err?.error === 'string' ? err.error : 'No se pudo actualizar el lote.');
        this.accionandoLote.set(false);
      },
    });
  }

  private aplicaPorDe(tipo: TipoDescuento): 'COMPRA' | 'ENTRADA' | 'PRECIO_ENTRADA' {
    if (tipo === 'monto-entrada') return 'ENTRADA';
    if (tipo === 'precio-entrada') return 'PRECIO_ENTRADA';
    return 'COMPRA';
  }

  private idsDeTipos(): number[] {
    return this.tiposEntrada().map((t) => t.id);
  }

  /** Los formularios arrancan con todos los tipos tildados: el cupón alcanza a todos salvo que se destilde alguno. */
  private tildarTodosLosTipos(): void {
    this.tiposCupon.set(this.idsDeTipos());
    this.tiposLote.set(this.idsDeTipos());
  }

  /**
   * Todos tildados se manda como lista vacía (= todos): así un tipo de entrada que se cree
   * más adelante también queda incluido. Sólo un subconjunto se manda explícito.
   */
  private tiposParaEnviar(tildados: number[]): number[] {
    return tildados.length >= this.tiposEntrada().length ? [] : tildados;
  }

  /** Con más de un tipo para elegir, al menos uno tiene que quedar tildado. */
  private faltanTipos(tildados: number[]): boolean {
    return this.tiposEntrada().length > 1 && tildados.length === 0;
  }

  /** Tilda/destilda un tipo de entrada en la lista elegida de un formulario. */
  alternarTipo(destino: 'cupon' | 'lote', id: number): void {
    const senal = destino === 'cupon' ? this.tiposCupon : this.tiposLote;
    senal.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  /** Las reglas del cupón que no se ven en "Descuento" (por entrada, mínimo, máximo, tope, tipos, desde). */
  reglasLegibles(c: Cupon): string {
    const reglas: string[] = [];
    if (c.minEntradas) reglas.push(`mín. ${c.minEntradas} entradas`);
    if (c.maxEntradasAfectadas) reglas.push(`máx. ${c.maxEntradasAfectadas} con descuento`);
    if (c.topeDescuento) reglas.push(`tope $${c.topeDescuento}`);
    if (c.tiposEntradaIds?.length) {
      const nombres = c.tiposEntradaIds.map((id) => this.tiposEntrada().find((t) => t.id === id)?.nombre ?? `#${id}`);
      reglas.push(`sólo ${nombres.join(', ')}`);
    }
    if (c.fechaDesde) reglas.push(`desde ${c.fechaDesde.split('-').reverse().join('/')}`);
    return reglas.join(' · ');
  }

  descuentoLegible(item: { porcentajeDescuento: number | null; montoDescuento: number | null; aplicaPor?: string }): string {
    if (item.porcentajeDescuento) return `${item.porcentajeDescuento}%`;
    if (item.montoDescuento) {
      if (item.aplicaPor === 'PRECIO_ENTRADA') return `Entrada a $${item.montoDescuento}`;
      if (item.aplicaPor === 'ENTRADA') return `-$${item.montoDescuento} por entrada`;
      return `-$${item.montoDescuento}`;
    }
    return '—';
  }
}
