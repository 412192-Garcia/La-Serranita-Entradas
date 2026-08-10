import { Component, OnInit, inject, signal } from '@angular/core';
import { BoleteriaService } from '../Services/boleteria.service';
import { TipoEntradaService } from '../Services/tipo-entrada.service';
import { CajaService, Caja } from '../Services/caja.service';
import { PromocionService } from '../Services/promocion.service';
import { ArticuloVarioService } from '../Services/articulo-vario.service';
import { TipoEntrada } from '../models/tipo-entrada';
import { Promocion } from '../models/promocion';
import { ArticuloVario } from '../models/articulo-vario';
import { FilaArticuloCarrito } from '../models/venta-pos';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
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

  /** Venta recién cerrada: mientras esté seteada se muestra el comprobante en pantalla. */
  ultimaVenta = signal<VentaPosConfirmada | null>(null);

  ngOnInit(): void {
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (ts) => {
        // Los extras (ej. menú almuerzo) no se venden en boletería, sólo en la compra online:
        // el catálogo del POS ya se carga filtrado a ENTRADA.
        this.tiposEntrada.set(ts.filter((t) => t.activo && t.tipo === 'ENTRADA'));
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los tipos de entrada:', err);
        this.errorCarga.set('No se pudieron cargar las entradas. Recargá la página.');
        this.cargando.set(false);
      },
    });

    this.cajaService.getActual().subscribe({
      next: (c) => {
        this.caja.set(c);
        this.cargandoCaja.set(false);
      },
      error: (err) => {
        console.error('Error al consultar la caja:', err);
        this.cargandoCaja.set(false);
      },
    });

    this.promocionService.getPromociones().subscribe({
      next: (ps) => this.promociones.set(ps),
      error: (err) => console.error('Error al cargar las promociones:', err),
    });

    this.articuloVarioService.getArticulos().subscribe({
      next: (as) => this.articulosCatalogo.set(as),
      error: (err) => console.error('Error al cargar los artículos varios:', err),
    });
  }

  onRetiroRegistrado(c: Caja): void {
    this.caja.set(c);
    this.mostrarRetiro.set(false);
  }

  onIngresoEntradasRegistrado(c: Caja): void {
    this.caja.set(c);
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
      this.caja.update((c) => (c ? { ...c, huboVentaDolares: true } : c));
    }
  }

  /** Cierra el comprobante y deja la pantalla lista para el próximo cliente. */
  nuevaVenta(): void {
    this.ultimaVenta.set(null);
    this.onLimpiar();
  }
}
