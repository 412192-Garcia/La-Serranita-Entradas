import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { CrearReserva } from '../crear-reserva/crear-reserva';
import { FacturacionManual } from './facturacion-manual/facturacion-manual';
import { VouchersManuales } from './vouchers-manuales/vouchers-manuales';
import { NotificacionService } from '../services/notificacion.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { TourStep } from '../shared/tour/tour';
import { LucideArrowLeft, LucideCalendarPlus, LucideFileText, LucideTicket } from '@lucide/angular';
import { crearSeccionCelular } from '../shared/modal/atras.service';

type Tab = 'reserva' | 'factura' | 'vouchers';

/** Cada pestaña muestra un componente hijo distinto (el otro ni existe en el DOM), así que el
 * tutorial cambia de pasos según la pestaña activa — mismo criterio que Configuración. */
const PASOS_POR_TAB: Record<Exclude<Tab, 'factura'>, TourStep[]> = {
  reserva: [
    {
      selector: '[data-tour="calendario"]',
      titulo: 'Elegí la fecha',
      texto: 'Seleccioná el día de la visita antes de cargar las entradas.',
    },
    {
      selector: '[data-tour="panel-paso"]',
      titulo: 'Tres pasos',
      texto: 'Cargá las entradas y los datos del cliente, y confirmá. La reserva queda aprobada, sin cobro.',
    },
  ],
  vouchers: [
    {
      selector: '[data-tour="voucher-manual"]',
      titulo: 'Voucher para la cantina',
      texto: 'Elegí artículos o un monto, y a nombre de quién va. Sale por la ticketera elegida y vale sólo por hoy.',
    },
  ],
};

@Component({
  selector: 'app-acciones-admin',
  imports: [CabeceraInterna, CrearReserva, FacturacionManual, VouchersManuales, LucideArrowLeft, LucideCalendarPlus, LucideFileText, LucideTicket],
  templateUrl: './acciones-admin.html',
  styleUrl: './acciones-admin.css',
})
export class AccionesAdmin implements OnInit {
  tab = signal<Tab>('reserva');
  private facturacion = viewChild(FacturacionManual);
  pasosTutorial = computed(() => {
    const tab = this.tab();
    return tab === 'factura' ? this.pasosFacturacion : PASOS_POR_TAB[tab];
  });

  /** Cada paso abre el modo que explica. */
  private readonly pasosFacturacion: TourStep[] = [
    {
      selector: '[data-tour="modos-factura"]',
      titulo: 'Tres modos',
      texto: '"Una factura" para un caso puntual, "En lote" para las ventas que quedaron sin factura y "Control" para revisar la facturación.',
    },
    {
      selector: '[data-tour="factura-manual"]',
      titulo: 'Factura manual',
      texto: 'Una factura sin venta: elegí los ítems, ajustá los precios y elegí cómo entregarla. Abajo están las últimas, por si hay que anular alguna.',
      antes: () => this.facturacion()?.modo.set('una'),
    },
    {
      selector: '[data-tour="control-facturas"]',
      titulo: 'Control',
      texto: 'Cuándo vence el certificado de ARCA y qué facturas no se pudieron emitir. Abajo, el listado del período para el contador.',
      antes: () => this.facturacion()?.modo.set('control'),
    },
  ];
  seccionAbierta = crearSeccionCelular();

  /** Punto de aviso en "Facturación" (y en su modo Control): facturas trabadas, certificado por vencer o numeración desfasada. */
  hayAvisoFacturacion = signal(false);
  private notificacionService = inject(NotificacionService);

  ngOnInit(): void {
    this.notificacionService.obtenerResumen().subscribe({
      next: (r) => this.hayAvisoFacturacion.set(!!r.FACTURACION),
      error: () => {},
    });
  }
}
