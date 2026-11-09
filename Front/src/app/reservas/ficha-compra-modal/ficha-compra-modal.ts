import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { LucideGift, LucideLoaderCircle, LucideX } from '@lucide/angular';
import { Modal } from '../../shared/modal/modal';
import { PesosPipe } from '../../shared/pesos.pipe';
import { BoleteriaService, DetalleReserva, FichaCompra, Reserva } from '../../services/boleteria.service';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { aVista, esRegalo } from '../../shared/reserva-vista.util';
import { aFechaISO } from '../../shared/fecha.util';
import { descripcionTarjeta } from '../../shared/tarjeta-point.util';

interface LineaFicha {
  nombre: string;
  cantidad: number;
  ausentes: number;
  precio: number | null;
  esExtra: boolean;
}

const CONFIRMACION_PAGO: Record<string, string> = {
  WEBHOOK: 'Aviso de Mercado Pago',
  VERIFICACION: 'Al volver de Mercado Pago',
  BARRIDO: 'Revisión automática',
};

const MOTIVOS_CANCELACION: Record<string, string> = {
  CHECKOUT_ABANDONADO: 'No terminó de pagar',
  PAGO_RECHAZADO: 'Pago rechazado',
  PAGO_NO_INICIADO: 'No se pudo iniciar el pago',
  ANULADA_EN_CAJA: 'Anulada en caja',
  RESERVA_CANCELADA: 'Reserva cancelada',
};

const ESTADOS_FACTURA: Record<string, string> = {
  PENDIENTE: 'en proceso',
  ERROR: 'con error',
  ANULADA: 'anulada',
};

function aLinea(d: DetalleReserva): LineaFicha {
  return {
    nombre: d.tipoEntrada?.nombre ?? d.articuloVario?.nombre ?? d.descripcionLibre ?? 'Artículo',
    cantidad: d.cantidad,
    ausentes: d.cantidadAusente ?? 0,
    precio: d.precioUnitario ?? d.tipoEntrada?.precio ?? null,
    esExtra: d.tipoEntrada?.tipo !== 'ENTRADA',
  };
}

/** "Ver detalle" de una compra: Control de Accesos, Anticipadas y Ventas y facturas. */
@Component({
  selector: 'app-ficha-compra-modal',
  imports: [Modal, DatePipe, PesosPipe, LucideX, LucideGift, LucideLoaderCircle],
  templateUrl: './ficha-compra-modal.html',
  styleUrl: './ficha-compra-modal.css',
})
export class FichaCompraModal implements OnInit {
  private boleteriaService = inject(BoleteriaService);

  compraId = input.required<number>();
  /** Lo que ya se tiene de la compra, para mostrar algo mientras carga. */
  reserva = input<Reserva | null>(null);
  /** Muestra el botón de la factura (ver, mandar o facturar); quien lo usa abre la ventana. */
  conFactura = input(false);
  cerrar = output<void>();
  verFactura = output<Reserva>();

  ficha = signal<FichaCompra | null>(null);
  error = signal<string | null>(null);

  readonly etiquetaFormaPago = etiquetaFormaPago;

  compra = computed(() => this.ficha()?.compra ?? this.reserva());
  vista = computed(() => { const c = this.compra(); return c ? aVista(c, aFechaISO(new Date())) : null; });
  regalo = computed(() => { const c = this.compra(); return c ? esRegalo(c) : false; });
  lineas = computed(() => (this.compra()?.detalles ?? []).map(aLinea));
  subtotal = computed(() => { const c = this.compra(); return c ? c.montoTotal + (c.descuentoAplicado ?? 0) : 0; });
  vouchers = computed(() => (this.compra()?.vouchers ?? []).filter((v) => !v.anulado));

  ngOnInit(): void {
    this.boleteriaService.obtenerFicha(this.compraId()).subscribe({
      next: (f) => this.ficha.set(f),
      error: (err) => {
        console.error('No se pudo traer el detalle de la compra:', err);
        this.error.set('No se pudieron traer todos los datos. Se muestra lo básico.');
      },
    });
  }

  tarjetaPoint = computed(() => {
    const f = this.ficha();
    return f ? descripcionTarjeta(f.pointMedioPago, f.pointTipoMedio, f.pointCuotas) : null;
  });

  /** Emitida no lleva aclaración. */
  estadoFactura(estado: string | null): string | null {
    return estado ? (ESTADOS_FACTURA[estado] ?? null) : null;
  }

  confirmacionPago(valor: string | null): string | null {
    return valor ? (CONFIRMACION_PAGO[valor] ?? valor) : null;
  }

  motivoCancelacion(valor: string | null): string | null {
    return valor ? (MOTIVOS_CANCELACION[valor] ?? valor) : null;
  }

  /** SISTEMA = la canceló el sistema solo. */
  autor(usuario: string | null | undefined): string {
    return !usuario || usuario === 'SISTEMA' ? 'Sistema' : usuario;
  }
}
