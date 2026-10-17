import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ToastService } from '../../shared/toast/toast.service';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, timer } from 'rxjs';
import {
  LucideCircleAlert,
  LucideCircleCheck,
  LucideFileText,
  LucideLoaderCircle,
  LucideMail,
  LucidePlus,
  LucidePrinter,
  LucideTrash2,
} from '@lucide/angular';
import {
  CONDICIONES_IVA_FACTURA_A,
  Factura,
  FacturaManualItem,
  PadronContribuyente,
  ReceptorFacturaA,
  cuitValido,
  formatearCuit,
  nombreComprobante,
  letraComprobante,
  numeroComprobante,
} from '../../models/factura';
import { TipoEntrada } from '../../models/tipo-entrada';
import { ArticuloVario } from '../../models/articulo-vario';
import { FacturaService } from '../../services/factura.service';
import { ImpresorasService } from '../../services/impresoras.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { ArticuloVarioService } from '../../services/articulo-vario.service';
import { PesosPipe } from '../../shared/pesos.pipe';
import { Spinner } from '../../shared/spinner/spinner';
import { SelectorDispositivos } from '../../pos/selector-dispositivos/selector-dispositivos';
import { FacturacionLote } from '../facturacion-lote/facturacion-lote';

/** Un renglón del formulario. `origen` es "libre", "e:<id>" (entrada) o "a:<id>" (artículo). */
interface Linea {
  clave: number;
  origen: string;
  cantidad: number;
  descripcion: string;
  precio: number | null;
}

type Destino = 'NINGUNO' | 'MAIL' | 'IMPRIMIR';

/** B = Consumidor Final; A = responsable inscripto o monotributista identificado por CUIT. */
type Letra = 'A' | 'B';

const INTERVALO_REFRESCO_MS = 2500;
const MAX_REFRESCOS = 24;

/**
 * Acciones > Facturación manual: una Factura B (o A, a un CUIT) sin venta detrás, con los renglones e importes que
 * cargue el admin (del catálogo o a mano). Para lo que no pasa por el POS: un grupo que pagó por
 * transferencia, un servicio especial, corregir algo que se cobró sin factura...
 *
 * En lote (FacturacionLote): las ventas del día que quedaron sin facturar, una factura por venta.
 *
 * Abajo, las últimas facturas manuales con sus acciones: PDF, mail, reimprimir y anular (nota de
 * crédito por el total).
 */
@Component({
  selector: 'app-facturacion-manual',
  imports: [FormsModule, DatePipe, PesosPipe, Spinner, SelectorDispositivos, FacturacionLote, LucidePlus, LucideTrash2, LucideFileText, LucideMail,
    LucidePrinter, LucideCircleAlert, LucideCircleCheck, LucideLoaderCircle],
  templateUrl: './facturacion-manual.html',
  styleUrl: './facturacion-manual.css',
})
export class FacturacionManual implements OnInit {
  private facturaService = inject(FacturaService);
  private toast = inject(ToastService);
  private tipoEntradaService = inject(TipoEntradaService);
  private articuloVarioService = inject(ArticuloVarioService);
  private destroyRef = inject(DestroyRef);
  readonly impresoras = inject(ImpresorasService);

  readonly habilitada = this.facturaService.habilitada;
  readonly numeroComprobante = numeroComprobante;
  readonly nombreComprobante = nombreComprobante;
  readonly letraDe = letraComprobante;
  readonly formatearCuit = formatearCuit;
  readonly condicionesIva = CONDICIONES_IVA_FACTURA_A;

  tipos = signal<TipoEntrada[]>([]);
  articulos = signal<ArticuloVario[]>([]);

  /** Una factura a mano, o un lote con las ventas del día que quedaron sin facturar. */
  modo = signal<'una' | 'lote'>('una');

  private proximaClave = 1;
  lineas = signal<Linea[]>([this.lineaVacia()]);
  destino = signal<Destino>('NINGUNO');
  email = signal('');

  /** Factura A: los datos del receptor (los precios se cargan igual, con IVA incluido). */
  letra = signal<Letra>('B');
  cuit = signal('');
  razonSocial = signal('');
  domicilio = signal('');
  condicionIva = signal<number>(1);

  /** Consulta al padrón de ARCA: se dispara sola cuando el CUIT queda completo y válido. */
  consultandoPadron = signal(false);
  padron = signal<PadronContribuyente | null>(null);
  private consultaPadron: Subscription | null = null;
  /** Lo último que se completó desde ARCA: si el CUIT cambia y nadie lo tocó, se borra (era de otro CUIT). */
  private autocompletado: { razonSocial: string; domicilio: string } | null = null;

  /** Paso de confirmación antes de emitir: es un comprobante fiscal, no se deshace. */
  confirmando = signal(false);
  emitiendo = signal(false);
  errorForm = signal<string | null>(null);

  manuales = signal<Factura[]>([]);
  cargandoLista = signal(true);
  errorLista = signal<string | null>(null);
  /** Acción abierta en una fila: email para mandarla, o confirmar la anulación. */
  accion = signal<{ id: number; tipo: 'mail' | 'anular' } | null>(null);
  emailFila = signal('');
  enviandoId = signal<number | null>(null);

  private refresco: Subscription | null = null;
  private refrescosHechos = 0;

  total = computed(() => this.lineas().reduce((suma, l) => suma + this.subtotal(l), 0));

  /** Neto e IVA 21% del total (lo que discrimina la Factura A), redondeado como el backend. */
  neto = computed(() => Math.round((this.total() / 1.21) * 100) / 100);
  iva = computed(() => Math.round((this.total() - this.neto()) * 100) / 100);

  /** El CUIT sin guiones ni espacios. */
  cuitLimpio = computed(() => this.cuit().replace(/[\s.-]/g, ''));
  cuitOk = computed(() => cuitValido(this.cuitLimpio()));

  /** Según ARCA, a este CUIT no le corresponde Factura A (exento, no inscripto): no se deja emitir. */
  padronRechazaA = computed(() => {
    const p = this.padron();
    return p !== null && p.cuit === this.cuitLimpio() && p.estado === 'ENCONTRADO' && !p.admiteFacturaA;
  });

  receptorValido = computed(() =>
    this.letra() === 'B' ||
    (this.cuitOk() && !this.padronRechazaA() && !this.consultandoPadron() &&
      this.razonSocial().trim() !== '' && this.domicilio().trim() !== ''));

  nombreCondicion = computed(() => this.condicionesIva.find((c) => c.codigo === this.condicionIva())?.nombre ?? '');

  formularioValido = computed(() =>
    this.receptorValido() &&
    this.lineas().length > 0 &&
    this.lineas().every((l) => l.cantidad >= 1 && l.descripcion.trim() !== '' && this.subtotal(l) > 0) &&
    (this.destino() !== 'MAIL' || this.emailValido(this.email())) &&
    (this.destino() !== 'IMPRIMIR' || this.impresoras.lista()));

  ngOnInit(): void {
    this.facturaService.actualizarEstadoServicio();
    this.impresoras.seguir(this.destroyRef);
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (t) => this.tipos.set(t.filter((x) => x.activo)),
      error: () => {},
    });
    this.articuloVarioService.getArticulos().subscribe({
      next: (a) => this.articulos.set(a.filter((x) => x.activo)),
      error: () => {},
    });
    this.cargarLista();
  }

  // ---------- formulario ----------

  private lineaVacia(): Linea {
    return { clave: this.proximaClave++, origen: 'libre', cantidad: 1, descripcion: '', precio: null };
  }

  subtotal(l: Linea): number {
    return l.precio != null && l.precio > 0 && l.cantidad > 0 ? Math.round(l.cantidad * l.precio * 100) / 100 : 0;
  }

  agregarLinea(): void {
    this.lineas.update((ls) => [...ls, this.lineaVacia()]);
  }

  quitarLinea(clave: number): void {
    this.lineas.update((ls) => (ls.length > 1 ? ls.filter((l) => l.clave !== clave) : ls));
  }

  actualizar(clave: number, cambios: Partial<Linea>): void {
    this.lineas.update((ls) => ls.map((l) => (l.clave === clave ? { ...l, ...cambios } : l)));
    this.confirmando.set(false);
  }

  /** Al elegir del catálogo se completan nombre y precio (se pueden cambiar igual). */
  elegirOrigen(clave: number, origen: string): void {
    const [tipo, id] = origen.split(':');
    if (tipo === 'e') {
      const t = this.tipos().find((x) => x.id === Number(id));
      if (t) this.actualizar(clave, { origen, descripcion: t.nombre, precio: t.precio });
    } else if (tipo === 'a') {
      const a = this.articulos().find((x) => x.id === Number(id));
      if (a) this.actualizar(clave, { origen, descripcion: a.nombre, precio: a.precioSugerido });
    } else {
      this.actualizar(clave, { origen: 'libre' });
    }
  }

  private tipoItem(l: Linea): FacturaManualItem['tipo'] {
    if (l.origen.startsWith('e:')) return 'ENTRADA';
    if (l.origen.startsWith('a:')) return 'ARTICULO';
    return 'LIBRE';
  }

  elegirLetra(letra: Letra): void {
    this.letra.set(letra);
    this.confirmando.set(false);
  }

  /** Cualquier cambio en el receptor saca el paso de confirmación (lo confirmado era otra cosa). */
  cambiarReceptor(campo: 'cuit' | 'razonSocial' | 'domicilio', valor: string): void {
    this[campo].set(valor ?? '');
    this.confirmando.set(false);
    if (campo === 'cuit') this.alCambiarCuit();
  }

  /** Con el CUIT completo y válido se consulta el padrón (una vez por CUIT); si cambia, se descarta lo anterior. */
  private alCambiarCuit(): void {
    const cuit = this.cuitLimpio();
    if (this.padron()?.cuit === cuit) return;
    this.consultaPadron?.unsubscribe();
    this.consultandoPadron.set(false);
    this.padron.set(null);
    if (this.autocompletado) {
      if (this.razonSocial() === this.autocompletado.razonSocial) this.razonSocial.set('');
      if (this.domicilio() === this.autocompletado.domicilio) this.domicilio.set('');
      this.autocompletado = null;
    }
    if (this.cuitOk()) this.consultarPadron();
  }

  consultarPadron(): void {
    const cuit = this.cuitLimpio();
    if (!cuitValido(cuit)) return;
    this.consultaPadron?.unsubscribe();
    this.consultandoPadron.set(true);
    this.consultaPadron = this.facturaService
      .padron(cuit)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (p) => {
          this.consultandoPadron.set(false);
          this.padron.set(p);
          if (p.estado !== 'ENCONTRADO') return;
          // Lo de ARCA pisa lo que hubiera: es el dato oficial. Se puede corregir a mano igual.
          if (p.razonSocial) this.razonSocial.set(p.razonSocial);
          if (p.domicilio) this.domicilio.set(p.domicilio);
          if (p.admiteFacturaA && p.condicionIva != null) this.condicionIva.set(p.condicionIva);
          this.autocompletado = { razonSocial: this.razonSocial(), domicilio: this.domicilio() };
          this.confirmando.set(false);
        },
        error: (err) => {
          this.consultandoPadron.set(false);
          this.padron.set({
            cuit, estado: 'NO_DISPONIBLE', razonSocial: null, domicilio: null, condicionIva: null,
            condicionIvaTexto: null, admiteFacturaA: false,
            mensaje: this.mensaje(err, 'No se pudo consultar el padrón de ARCA. Cargá los datos a mano.'),
          });
        },
      });
  }

  private receptor(): ReceptorFacturaA | null {
    if (this.letra() !== 'A') return null;
    return {
      cuit: this.cuitLimpio(),
      razonSocial: this.razonSocial().trim(),
      domicilio: this.domicilio().trim(),
      condicionIva: this.condicionIva(),
    };
  }

  pedirConfirmacion(): void {
    this.errorForm.set(null);
    if (this.formularioValido()) this.confirmando.set(true);
  }

  emitir(): void {
    if (!this.formularioValido() || this.emitiendo()) return;
    this.emitiendo.set(true);
    this.errorForm.set(null);
    const destino = this.destino();
    this.facturaService
      .emitirManual({
        items: this.lineas().map((l) => ({
          tipo: this.tipoItem(l),
          cantidad: l.cantidad,
          descripcion: l.descripcion.trim(),
          subtotal: this.subtotal(l),
        })),
        destino,
        email: destino === 'MAIL' ? this.email().trim() : null,
        impresora: destino === 'IMPRIMIR' ? this.impresoras.efectiva()?.nombre ?? null : null,
        receptor: this.receptor(),
      })
      .subscribe({
        next: () => {
          this.emitiendo.set(false);
          this.confirmando.set(false);
          this.lineas.set([this.lineaVacia()]);
          this.email.set('');
          // El receptor se limpia: la próxima factura A suele ser a otro cliente.
          this.cuit.set('');
          this.razonSocial.set('');
          this.domicilio.set('');
          this.padron.set(null);
          this.autocompletado = null;
          this.toast.exito(
            destino === 'IMPRIMIR' ? 'Factura pedida: sale por la ticketera en unos segundos.'
              : destino === 'MAIL' ? 'Factura pedida: se manda por mail en unos segundos.'
                : 'Factura pedida: en unos segundos está el PDF en la lista de abajo.');
          this.refrescosHechos = 0;
          this.cargarLista(false);
        },
        error: (err) => {
          this.emitiendo.set(false);
          this.errorForm.set(this.mensaje(err, 'No se pudo emitir la factura.'));
        },
      });
  }

  alEmitirLote(): void {
    this.refrescosHechos = 0;
    this.cargarLista(false);
  }

  // ---------- lista ----------

  cargarLista(conSpinner = true): void {
    if (conSpinner) this.cargandoLista.set(true);
    this.errorLista.set(null);
    this.facturaService
      .manuales()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (fs) => {
          this.manuales.set(fs);
          this.cargandoLista.set(false);
          this.programarRefresco();
        },
        error: (err) => {
          this.cargandoLista.set(false);
          if (conSpinner) this.errorLista.set(this.mensaje(err, 'No se pudieron cargar las facturas manuales.'));
          this.programarRefresco();
        },
      });
  }

  private enCurso(f: Factura): boolean {
    return f.estado === 'PENDIENTE' || f.impresionEstado === 'PENDIENTE' || f.impresionEstado === 'ENVIADO';
  }

  /** Mientras alguna se está emitiendo o imprimiendo, la lista se refresca sola (con tope). */
  private programarRefresco(): void {
    this.refresco?.unsubscribe();
    if (this.refrescosHechos >= MAX_REFRESCOS || !this.manuales().some((f) => this.enCurso(f))) return;
    this.refrescosHechos++;
    this.refresco = timer(INTERVALO_REFRESCO_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.cargarLista(false));
  }

  /** "2× General, 1× Quincho" a partir del detalle guardado. */
  resumen(f: Factura): string {
    if (!f.detalle) return '';
    return f.detalle
      .split('\n')
      .map((linea) => linea.split('\t'))
      .filter((p) => p.length >= 2)
      .map(([cant, desc]) => `${cant}× ${desc}`)
      .join(', ');
  }

  vigente(f: Factura): boolean {
    return f.estado === 'EMITIDA' && !f.anulacionPedida;
  }

  abrirAccion(f: Factura, tipo: 'mail' | 'anular'): void {
    const a = this.accion();
    this.accion.set(a?.id === f.id && a.tipo === tipo ? null : { id: f.id, tipo });
    this.emailFila.set(f.email ?? '');
  }

  accionAbierta(f: Factura, tipo: 'mail' | 'anular'): boolean {
    const a = this.accion();
    return a !== null && a.id === f.id && a.tipo === tipo;
  }

  reimprimir(f: Factura): void {
    this.pedir(f, this.facturaService.imprimir(f.id, this.impresoras.efectiva()?.nombre ?? null), 'Se mandó a la ticketera.');
  }

  mandarPorMail(f: Factura): void {
    const email = this.emailFila().trim();
    if (!this.emailValido(email)) return;
    this.pedir(f, this.facturaService.enviarPorMail(f.id, email), `Factura enviada a ${email}.`);
  }

  anular(f: Factura): void {
    this.pedir(f, this.facturaService.anularFactura(f.id), 'Anulada: se emite la nota de crédito.');
  }

  /** La pestaña se abre ya, con el clic: si se abre cuando llega el PDF, el navegador la bloquea. */
  verPdf(f: Factura): void {
    const pestana = window.open('', '_blank');
    this.facturaService.pdf(f.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        if (pestana) pestana.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => {
        pestana?.close();
        this.toast.error(this.mensaje(err, 'No se pudo abrir el PDF.'));
      },
    });
  }

  private pedir(f: Factura, pedido: Observable<unknown>, ok: string): void {
    this.enviandoId.set(f.id);
    pedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.enviandoId.set(null);
        this.accion.set(null);
        this.toast.exito(ok);
        this.refrescosHechos = 0;
        this.cargarLista(false);
      },
      error: (err) => {
        this.enviandoId.set(null);
        this.toast.error(this.mensaje(err, 'No se pudo completar.'));
      },
    });
  }

  emailValido(email: string): boolean {
    return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim());
  }

  private mensaje(err: unknown, porDefecto: string): string {
    const e = err as { error?: unknown; status?: number };
    if (typeof e?.error === 'string' && e.error.trim()) return e.error;
    if (e?.status === 0) return 'No se pudo contactar con el servidor.';
    return porDefecto;
  }
}
