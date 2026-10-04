import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CrearReserva } from '../crear-reserva/crear-reserva';
import { RechazosOperaciones } from '../cajas/rechazos-operaciones/rechazos-operaciones';
import { FacturacionManual } from './facturacion-manual/facturacion-manual';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { RechazoService } from '../services/rechazo.service';
import { TourStep } from '../shared/tour/tour';

type Tab = 'reserva' | 'factura' | 'rechazos';

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
      texto: 'Acá se completa la reserva: primero las entradas, después los datos del cliente y por último confirmar. Se genera aprobada, sin cobro.',
    },
  ],
  factura: [
    {
      selector: '[data-tour="factura-manual"]',
      titulo: 'Factura manual',
      texto: 'Factura B sin venta: elegí ítems del catálogo o escribilos a mano, ajustá precios y elegí si se descarga en PDF, se manda por mail o se imprime. Antes de emitir pide confirmación. Abajo quedan las últimas, con la opción de anularlas (nota de crédito).',
    },
  ],
  rechazos: [
    {
      selector: '[data-tour="rechazos-operaciones"]',
      titulo: 'Operaciones rechazadas',
      texto: 'Ventas, retiros/aportes e ingresos o retiros de entradas que el servidor rechazó (dato inválido, caja ya cerrada, etc.). Desde acá se resuelven o reintentan.',
    },
  ],
};

@Component({
  selector: 'app-acciones-admin',
  imports: [CabeceraInterna, CrearReserva, FacturacionManual, RechazosOperaciones],
  templateUrl: './acciones-admin.html',
  styleUrl: './acciones-admin.css',
})
export class AccionesAdmin implements OnInit {
  private rechazoService = inject(RechazoService);

  tab = signal<Tab>('reserva');
  pasosTutorial = computed(() => PASOS_POR_TAB[this.tab()]);

  /** Punto de aviso en la pestaña "Operaciones rechazadas": mismo dato que ya consulta la
   * cabecera para el menú, pero acá puntual a esta pestaña — RechazosOperaciones filtra por
   * defecto en "Pendientes" al entrar, así que sin esto el aviso sólo se nota abriendo la
   * pestaña, justo lo que este punto está para evitar. */
  hayRechazosPendientes = signal(false);

  ngOnInit(): void {
    this.rechazoService.listar(false).subscribe({
      next: (rs) => this.hayRechazosPendientes.set(rs.length > 0),
      error: (err) => console.error('Error al consultar operaciones rechazadas pendientes:', err),
    });
  }
}
