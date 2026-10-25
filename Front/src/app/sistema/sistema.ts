import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { TourStep } from '../shared/tour/tour';
import { EstadoSistemaPanel } from './estado-sistema/estado-sistema';
import { HistorialAcciones } from './historial-acciones/historial-acciones';
import { ErroresSistema } from './errores-sistema/errores-sistema';
import { RendimientoSistema } from './rendimiento-sistema/rendimiento-sistema';
import { RechazosOperaciones } from './rechazos-operaciones/rechazos-operaciones';
import { ModulosSistema } from './modulos-sistema/modulos-sistema';
import { RechazoService } from '../services/rechazo.service';

type Tab = 'estado' | 'historial' | 'errores' | 'rendimiento' | 'rechazos' | 'modulos';

const PASOS_POR_TAB: Record<Tab, TourStep[]> = {
  estado: [
    {
      selector: '[data-tour="estado-sistema"]',
      titulo: 'Estado del sistema',
      texto: 'Una tarjeta por área: verde si anda bien, roja si hay algo para resolver. Tocá "Ver" para ir.',
    },
  ],
  historial: [
    {
      selector: '[data-tour="historial-acciones"]',
      titulo: 'Historial de acciones',
      texto: 'Quién hizo qué y cuándo, con el antes y el después en los cambios importantes.',
    },
  ],
  errores: [
    {
      selector: '[data-tour="errores-sistema"]',
      titulo: 'Errores del sistema',
      texto: 'Errores del servidor y de JavaScript, agrupados. Buscá acá el código que te pasen y marcalo como resuelto para apagar el aviso.',
    },
  ],
  rendimiento: [
    {
      selector: '[data-tour="rendimiento-sistema"]',
      titulo: 'Rendimiento',
      texto: 'Tiempos por endpoint desde que arrancó el servidor (promedio, p95, máximo, 4xx/5xx) y las últimas requests lentas, con su id para buscarlas en el log.',
    },
  ],
  rechazos: [
    {
      selector: '[data-tour="rechazos-operaciones"]',
      titulo: 'Operaciones rechazadas',
      texto: 'Operaciones y mails que el servidor rechazó. Desde acá se resuelven o reintentan.',
    },
  ],
  modulos: [
    {
      selector: '[data-tour="modulos-sistema"]',
      titulo: 'Módulos',
      texto: 'Prendé o apagá la facturación, las reservas a pagar en el parque y el cobro con Point.',
    },
  ],
};

/**
 * Sistema (SUPERADMIN): monitoreo y trazabilidad. Estado de cada área, historial de acciones,
 * errores, rendimiento, operaciones rechazadas y los módulos que se pueden apagar. Las otras pantallas pueden mandar directo a una pestaña (?tab=errores&area=PAGOS,
 * ?tab=historial&accion=LOGIN_FALLIDO).
 */
@Component({
  selector: 'app-sistema',
  imports: [CabeceraInterna, EstadoSistemaPanel, HistorialAcciones, ErroresSistema, RendimientoSistema, RechazosOperaciones, ModulosSistema],
  templateUrl: './sistema.html',
  styleUrl: './sistema.css',
})
export class Sistema implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private rechazoService = inject(RechazoService);

  tab = signal<Tab>('estado');
  pasosTutorial = computed(() => PASOS_POR_TAB[this.tab()]);
  /** Filtros que vinieron en la URL, para la pestaña que corresponda. */
  accionInicial = signal<string | null>(null);
  areaInicial = signal<string | null>(null);
  /** Punto de aviso en "Operaciones rechazadas": la pestaña filtra en "Pendientes" al entrar, así
   * que sin esto sólo se nota abriéndola. */
  hayRechazosPendientes = signal(false);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((q) => {
      const tab = q.get('tab');
      if (tab === 'historial' || tab === 'errores' || tab === 'estado' || tab === 'rendimiento' || tab === 'rechazos' || tab === 'modulos') this.tab.set(tab);
      this.accionInicial.set(q.get('accion'));
      this.areaInicial.set(q.get('area'));
    });
    this.rechazoService.listar(false).subscribe({
      next: (rs) => this.hayRechazosPendientes.set(rs.length > 0),
      error: (err) => console.error('Error al consultar operaciones rechazadas pendientes:', err),
    });
  }

  elegir(tab: Tab): void {
    this.router.navigate([], { queryParams: { tab }, replaceUrl: true });
  }
}
