import { Component, OnInit, inject, signal } from '@angular/core';
import { BoleteriaService } from '../services/boleteria.service';
import { TipoEntradaService } from '../services/tipo-entrada.service';
import { CajaService, Caja } from '../services/caja.service';
import { PromocionService } from '../services/promocion.service';
import { ArticuloVarioService } from '../services/articulo-vario.service';
import { ConfiguracionService, DescuentoEfectivo } from '../services/configuracion.service';
import { PosCacheService } from '../services/pos-cache.service';
import { PayloadRetiroAporte } from '../services/operaciones-pendientes.service';
import { TipoEntrada } from '../models/tipo-entrada';
import { Promocion } from '../models/promocion';
import { ArticuloVario } from '../models/articulo-vario';
import { FilaArticuloCarrito } from '../models/venta-pos';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { Spinner } from '../shared/spinner/spinner';
import { AperturaCaja } from './apertura-caja/apertura-caja';
import { BarraCaja } from './barra-caja/barra-caja';
import { ValidarReservaModal } from './validar-reserva-modal/validar-reserva-modal';
import { RetiroEfectivoModal } from '../shared/retiro-efectivo-modal/retiro-efectivo-modal';
import { IngresoEntradasModal } from './ingreso-entradas-modal/ingreso-entradas-modal';
import { CatalogoEntradas } from './catalogo-entradas/catalogo-entradas';
import { AgregarArticulo } from './agregar-articulo/agregar-articulo';
import { CarritoVenta, VentaPosConfirmada } from './carrito-venta/carrito-venta';
import { ComprobanteVenta } from './comprobante-venta/comprobante-venta';

@Component({
  selector: 'app-pos',
  imports: [
    CabeceraInterna,
    Spinner,
    AperturaCaja,
    BarraCaja,
    ValidarReservaModal,
    RetiroEfectivoModal,
    IngresoEntradasModal,
    CatalogoEntradas,
    AgregarArticulo,
    CarritoVenta,
    ComprobanteVenta,
  ],
  templateUrl: './pos.html',
  styleUrl: './pos.css',
})
export class Pos implements OnInit {
  private boleteriaService = inject(BoleteriaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private cajaService = inject(CajaService);
  private promocionService = inject(PromocionService);
  private articuloVarioService = inject(ArticuloVarioService);
  private configuracionService = inject(ConfiguracionService);
  private cache = inject(PosCacheService);

  // ---------- Caja: sin una abierta no se puede vender ----------
  cargandoCaja = signal(true);
  /** Caja abierta del boletero; null = no tiene ninguna en curso (hay que abrir una — o pedirle a un admin que cierre la que ya está abierta). */
  caja = signal<Caja | null>(null);

  mostrarBusquedaReserva = signal(false);
  mostrarRetiro = signal(false);
  mostrarIngresoEntradas = signal(false);

  tiposEntrada = signal<TipoEntrada[]>([]);
  cargando = signal(true);
  errorCarga = signal<string | null>(null);
  cantidades = signal<Record<number, number>>({});

  promociones = signal<Promocion[]>([]);
  articulosCatalogo = signal<ArticuloVario[]>([]);
  articulosCarrito = signal<FilaArticuloCarrito[]>([]);
  /** Escalones de precio por grupo: se cachean para poder cotizar sin conexión (ver calculo-precio.util). */
  descuentosEfectivo = signal<DescuentoEfectivo[]>([]);

  /** Venta recién cerrada: mientras esté seteada se muestra el comprobante en pantalla. */
  ultimaVenta = signal<VentaPosConfirmada | null>(null);

  ngOnInit(): void {
    // Cada carga se cachea al salir bien, y al fallar cae al último snapshot conocido: sin
    // esto un corte de señal deja el POS con el catálogo vacío y sin poder vender nada.
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (ts) => {
        // Los extras (ej. menú almuerzo) no se venden en boletería, sólo en la compra online:
        // el catálogo del POS ya se carga filtrado a ENTRADA.
        const soloEntradas = ts.filter((t) => t.activo && t.tipo === 'ENTRADA');
        this.tiposEntrada.set(soloEntradas);
        this.cache.guardar('tiposEntrada', soloEntradas);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los tipos de entrada:', err);
        const cacheados = this.cache.leer<TipoEntrada[]>('tiposEntrada');
        if (cacheados?.length) {
          this.tiposEntrada.set(cacheados);
        } else {
          this.errorCarga.set('No se pudieron cargar las entradas. Recargá la página.');
        }
        this.cargando.set(false);
      },
    });

    this.cajaService.getActual().subscribe({
      next: (c) => {
        this.actualizarCaja(c);
        this.cargandoCaja.set(false);
      },
      error: (err) => {
        console.error('Error al consultar la caja:', err);
        // Sólo sirve si la caja cacheada seguía abierta: si el último snapshot ya estaba
        // cerrada, mostrar la pantalla de apertura (que igual va a pedir conexión) es correcto.
        const cacheada = this.cache.leer<Caja>('caja');
        if (cacheada && !cacheada.fechaCierre) {
          this.caja.set(cacheada);
        }
        this.cargandoCaja.set(false);
      },
    });

    this.promocionService.getPromociones().subscribe({
      next: (ps) => {
        this.promociones.set(ps);
        this.cache.guardar('promociones', ps);
      },
      error: (err) => {
        console.error('Error al cargar las promociones:', err);
        this.promociones.set(this.cache.leer<Promocion[]>('promociones') ?? []);
      },
    });

    this.articuloVarioService.getArticulos().subscribe({
      next: (as) => {
        this.articulosCatalogo.set(as);
        this.cache.guardar('articulos', as);
      },
      error: (err) => {
        console.error('Error al cargar los artículos varios:', err);
        this.articulosCatalogo.set(this.cache.leer<ArticuloVario[]>('articulos') ?? []);
      },
    });

    this.configuracionService.getDescuentosEfectivo().subscribe({
      next: (ds) => {
        this.descuentosEfectivo.set(ds);
        this.cache.guardar('descuentosEfectivo', ds);
      },
      error: (err) => {
        console.error('Error al cargar los precios por grupo:', err);
        this.descuentosEfectivo.set(this.cache.leer<DescuentoEfectivo[]>('descuentosEfectivo') ?? []);
      },
    });
  }

  /** Ver ngOnInit: mismo criterio, cualquier cambio a la caja tiene que quedar cacheado, no
   *  sólo la carga inicial — si no, un reload offline a mitad de turno muestra "Abrir caja" o
   *  totales viejos aunque la caja siga abierta de verdad del lado del servidor. */
  private actualizarCaja(c: Caja | null): void {
    this.caja.set(c);
    this.cache.guardar('caja', c);
  }

  onCajaAbierta(c: Caja): void {
    this.actualizarCaja(c);
  }

  onRetiroRegistrado(c: Caja): void {
    this.actualizarCaja(c);
    this.mostrarRetiro.set(false);
  }

  /** Se encoló sin conexión: se refleja en la caja local para que el boletero vea el movimiento igual. */
  onRetiroEncolado(mov: PayloadRetiroAporte): void {
    const actual = this.caja();
    if (actual) {
      const signo = mov.tipo === 'APORTE' ? -1 : 1;
      this.actualizarCaja({
        ...actual,
        totalRetiros: actual.totalRetiros + signo * mov.monto,
        retiros: [
          ...actual.retiros,
          { id: 0, monto: mov.monto, motivo: mov.motivo, tipo: mov.tipo, fecha: new Date().toISOString() },
        ],
      });
    }
    this.mostrarRetiro.set(false);
  }

  onIngresoEntradasRegistrado(c: Caja): void {
    this.actualizarCaja(c);
    this.mostrarIngresoEntradas.set(false);
  }

  /** Ver onRetiroEncolado. */
  onIngresoEntradasEncolado(cantidad: number): void {
    const actual = this.caja();
    if (actual) {
      this.actualizarCaja({
        ...actual,
        totalIngresosEntradas: actual.totalIngresosEntradas + cantidad,
        ingresosEntradas: [...actual.ingresosEntradas, { id: 0, cantidad, fecha: new Date().toISOString() }],
      });
    }
    this.mostrarIngresoEntradas.set(false);
  }

  onArticuloAgregado(fila: FilaArticuloCarrito): void {
    this.articulosCarrito.update((c) => [...c, fila]);
  }

  onQuitarArticulo(index: number): void {
    this.articulosCarrito.update((c) => c.filter((_, i) => i !== index));
  }

  onLimpiar(): void {
    this.cantidades.set({});
    this.articulosCarrito.set([]);
  }

  onVentaRegistrada(venta: VentaPosConfirmada): void {
    this.ultimaVenta.set(venta);
    // huboVentaDolares es lo único de la caja que el modal de cierre necesita en vivo (para
    // decidir si mostrar el campo de dólares contados): al ser un booleano, no un monto, es
    // seguro parchearlo localmente sin pisar el resto de los totales (que siguen ocultos
    // mientras la caja sigue abierta, por diseño anti-trampa).
    if (venta.pagoEnDolares) {
      const actual = this.caja();
      if (actual) this.actualizarCaja({ ...actual, huboVentaDolares: true });
    }
  }

  /** Cierra el comprobante y deja la pantalla lista para el próximo cliente. */
  nuevaVenta(): void {
    this.ultimaVenta.set(null);
    this.onLimpiar();
  }
}
