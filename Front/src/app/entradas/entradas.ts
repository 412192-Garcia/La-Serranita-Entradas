import {ChangeDetectorRef, Component, NgZone, OnDestroy, OnInit} from '@angular/core';
import { Calendario } from '../calendario/calendario';
import { SeleccionEntradas } from '../seleccion-entradas/seleccion-entradas';
import { FormCliente } from '../form-cliente/form-cliente';
import { Resumen } from '../resumen/resumen';
import { CompraService } from '../Services/compra.service';
import { PagoExitoso } from '../resultado-pago/pago-exitoso/pago-exitoso';
import {FormaPagoType, ResumenCompraData} from "../models/compra";

enum etapaCompra {
  SELECCION,
  DATOS,
  RESUMEN
}

declare var MercadoPago: any;

@Component({
  selector: 'app-entradas',
  imports: [
    Calendario,
    SeleccionEntradas,
    FormCliente,
    Resumen,
    PagoExitoso
  ],
  templateUrl: './entradas.html',
  styleUrl: './entradas.css',
})
export class Entradas implements OnInit, OnDestroy {
  etapa: etapaCompra = etapaCompra.SELECCION;

  // Inyectamos únicamente CompraService
  constructor(private compraService: CompraService,
  private cdr: ChangeDetectorRef,
              private ngZone: NgZone) {}

  ngOnInit(): void {
    this.mp = new MercadoPago('APP_USR-97241536-2c5b-48fc-945f-e44720698927', {
      locale: 'es-AR'
    });
  }

  ngOnDestroy(): void {
    this.detenerVerificacion();
  }

  mp: any;
  procesandoPago: boolean = false;
  compraIdActual: number | null = null;
  pagoConfirmado: boolean = false;

  private checkInterval: any = null;

  compraAcumulada: ResumenCompraData = {
    fechaVisita: null,
    esRegalo: false,
    entradas: [],
    cuponCodigo: null,
    descuentoMonto: 0,
    subtotal: 0,
    subtotalLista: 0,
    descuentoGrupo: 0,
    total: 0,
    formaPago: 'MERCADO_PAGO',
    cliente: null
  };

  onFechaSeleccionada(fecha: Date | null): void {
    this.compraAcumulada.fechaVisita = fecha;
  }

  onEsRegaloCambio(esRegalo: boolean): void {
    this.compraAcumulada.esRegalo = esRegalo;
    if (esRegalo) {
      this.compraAcumulada.fechaVisita = null;
    }
  }
  onFormaPagoCambio(metodo: FormaPagoType): void {
    this.compraAcumulada.formaPago = metodo;
    this.actualizarCotizacion();
  }

  private actualizarCotizacion(): void {
    const entradasPayload = this.compraAcumulada.entradas
      .filter(e => e.cantidad > 0)
      .map(e => ({
        tipoEntradaId: e.id || e.tipoEntradaId,
        cantidad: e.cantidad
      }));

    if (entradasPayload.length === 0) return;

    this.compraService.cotizar({
      formaPago: this.compraAcumulada.formaPago,
      entradas: entradasPayload
    }).subscribe({
      next: (res) => {
        this.ngZone.run(() => {
          const descuentoCupon = Math.min(this.compraAcumulada.descuentoMonto, res.subtotal);
          this.compraAcumulada.subtotal = res.subtotal;
          this.compraAcumulada.descuentoGrupo = res.ahorro > 0 ? res.ahorro : 0;
          this.compraAcumulada.total = Math.max(0, res.subtotal - descuentoCupon);
          this.cdr.detectChanges();
        });
      },
      error: (err) => console.error('Error al cotizar el precio:', err)
    });
  }

  onPasoSiguiente(datosPaso: any): void {
    if (this.etapa === etapaCompra.SELECCION) {
      this.compraAcumulada = {
        ...this.compraAcumulada,
        ...datosPaso,
        subtotalLista: datosPaso.subtotal,
        descuentoGrupo: 0,
        formaPago: datosPaso.formaPago
      };
      this.etapa = etapaCompra.DATOS;
    } else if (this.etapa === etapaCompra.DATOS) {
      this.compraAcumulada.cliente = datosPaso;
      this.etapa = etapaCompra.RESUMEN;
    }
  }

  volverPasoAnterior(): void {
    if (this.etapa === etapaCompra.RESUMEN) {
      this.etapa = etapaCompra.DATOS;
    } else if (this.etapa === etapaCompra.DATOS) {
      this.etapa = etapaCompra.SELECCION;
    }
  }

  iniciarPagoMercadoPago(): void {
    this.procesandoPago = true;

    // 1. Convertimos la fecha al formato YYYY-MM-DD que espera Spring Boot
    let fechaFormateada = null;
    if (this.compraAcumulada.fechaVisita) {
      const d = new Date(this.compraAcumulada.fechaVisita);
      fechaFormateada = d.toISOString().split('T')[0];
    }

    // 2. Mapeamos exactamente a la estructura del CompraRequestDTO del Backend
    const payloadBackend = {
      cliente: {
        dni: this.compraAcumulada.cliente?.dni,
        nombre: this.compraAcumulada.cliente?.nombre,
        apellido: this.compraAcumulada.cliente?.apellido,
        email: this.compraAcumulada.cliente?.email,
        telefono: this.compraAcumulada.cliente?.telefono
      },
      fecha: fechaFormateada,
      formaPago: this.compraAcumulada.formaPago,
      cuponCodigo: this.compraAcumulada.cuponCodigo || null,
      entradas: this.compraAcumulada.entradas.map((item: any) => ({
        tipoEntradaId: item.id || item.tipoEntradaId,
        cantidad: item.cantidad
      }))
    };

    console.log('📦 Estado de compraAcumulada:', this.compraAcumulada);
    console.log('💳 formaPago específica:', this.compraAcumulada.formaPago);

    // 3. Invocamos al backend a través del CompraService
    this.compraService.iniciarCompraConPago(payloadBackend).subscribe({
      next: (res) => {
        this.procesandoPago = false;
        this.compraIdActual = res.id;

        // EVALUAMOS LA ESTRATEGIA DEVUELTA POR EL BACKEND
        if (res.formaPago === 'MERCADO_PAGO' && res.initPoint) {
          const ancho = 500;
          const alto = 650;
          const izquierda = (window.screen.width - ancho) / 2;
          const arriba = (window.screen.height - alto) / 2;

          window.open(
              res.initPoint,
              'MercadoPagoCheckout',
              `width=${ancho},height=${alto},top=${arriba},left=${izquierda},scrollbars=yes,status=yes`
          );

          this.iniciarMonitoreoPago();
        } else if (res.formaPago === 'EFECTIVO_BOLETERIA') {
          // Para efectivo no abre ventana popup; congela la reserva y muestra el comprobante
          this.ngZone.run(() => {
            this.pagoConfirmado = true;
            this.cdr.detectChanges();
          });
        }
      },
      error: (err) => {
        console.error('Error al procesar la reserva:', err);
        this.procesandoPago = false;
        alert(err.error || 'Ocurrió un error al procesar la reserva.');
      }
    });
  }

  private iniciarMonitoreoPago(): void {
    this.detenerVerificacion();

    this.checkInterval = setInterval(() => {
      if (!this.compraIdActual) {
        console.warn('⚠️ No hay compraIdActual configurado');
        return;
      }

      console.log(`📡 Consultando estado para compra ID: ${this.compraIdActual}...`);

      this.compraService.obtenerEstadoCompra(this.compraIdActual).subscribe({
        next: (res) => {
          console.log('📩 Respuesta del servidor recibida:', res);
          if (res.estado === 'APROBADO') {
            this.pagoConfirmado = true;
            this.detenerVerificacion();
            console.log('¡Pago verificado con éxito!');

            this.ngZone.run(() => {
              this.pagoConfirmado = true;
              this.procesandoPago = false;
              this.detenerVerificacion();
              this.cdr.detectChanges(); // Re-evalúa la plantilla HTML al instante
            });

          }
        },
        error: (err) => {
          console.error('Error al consultar el estado de la compra:', err);
        }
      });
    }, 3000);
  }

  private detenerVerificacion(): void {
    if (this.checkInterval) {
      clearInterval(this.checkInterval);
      this.checkInterval = null;
    }
  }

  verificarEstadoCompra(): void {
    if (!this.compraIdActual) return;

    this.compraService.obtenerEstadoCompra(this.compraIdActual).subscribe({
      next: (res) => {
        if (res.estado === 'APROBADO') {
          this.pagoConfirmado = true;
          this.detenerVerificacion();
          this.cdr.detectChanges();
          console.log('¡Compra confirmada correctamente!');
        } else {
          alert('El pago está siendo procesado. Te informaremos apenas se acredite.');
        }
      },
      error: (err) => {
        console.error('Error al consultar el estado de la compra:', err);
      }
    });
  }

  protected readonly etapaCompra = etapaCompra;
}
