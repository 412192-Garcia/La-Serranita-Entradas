import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CrearReserva } from '../crear-reserva/crear-reserva';
import { FacturacionManual } from './facturacion-manual/facturacion-manual';
import { ControlFacturas } from './control-facturas/control-facturas';
import { NotificacionService } from '../services/notificacion.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { TourStep } from '../shared/tour/tour';

type Tab = 'reserva' | 'factura' | 'control';

/** Cada pestaña muestra un componente hijo distinto (el otro ni existe en el DOM), así que el
 * tutorial cambia de pasos según la pestaña activa — mismo criterio que Configuración. */
const PASOS_POR_TAB: Record<Tab, TourStep[]> = {
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
  factura: [
    {
      selector: '[data-tour="modos-factura"]',
      titulo: 'Una o en lote',
      texto: '"Una factura" para un caso puntual. "En lote" para facturar juntas varias ventas que quedaron sin factura.',
    },
    {
      selector: '[data-tour="factura-manual"]',
      titulo: 'Factura manual',
      texto: 'Una factura sin venta: elegí los ítems, ajustá los precios y elegí cómo entregarla. Abajo están las últimas, por si hay que anular alguna.',
    },
  ],
  control: [
    {
      selector: '[data-tour="control-facturas"]',
      titulo: 'Control de facturas',
      texto: 'Cuándo vence el certificado de ARCA y qué facturas no se pudieron emitir. Abajo, el listado del período para el contador.',
    },
  ],
};

@Component({
  selector: 'app-acciones-admin',
  imports: [CabeceraInterna, CrearReserva, FacturacionManual, ControlFacturas],
  templateUrl: './acciones-admin.html',
  styleUrl: './acciones-admin.css',
})
export class AccionesAdmin implements OnInit {
  tab = signal<Tab>('reserva');
  pasosTutorial = computed(() => PASOS_POR_TAB[this.tab()]);

  /** Punto de aviso en "Control de facturas": facturas trabadas, certificado por vencer o numeración desfasada. */
  hayAvisoFacturacion = signal(false);
  private notificacionService = inject(NotificacionService);

  ngOnInit(): void {
    this.notificacionService.obtenerResumen().subscribe({
      next: (r) => this.hayAvisoFacturacion.set(!!r.FACTURACION),
      error: () => {},
    });
  }
}
