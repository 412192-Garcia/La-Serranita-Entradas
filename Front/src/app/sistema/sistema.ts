import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { TourStep } from '../shared/tour/tour';
import { EstadoSistemaPanel } from './estado-sistema/estado-sistema';
import { HistorialAcciones } from './historial-acciones/historial-acciones';
import { ErroresSistema } from './errores-sistema/errores-sistema';
import { RendimientoSistema } from './rendimiento-sistema/rendimiento-sistema';

type Tab = 'estado' | 'historial' | 'errores' | 'rendimiento';

const PASOS_POR_TAB: Record<Tab, TourStep[]> = {
  estado: [
    {
      selector: '[data-tour="estado-sistema"]',
      titulo: 'Estado del sistema',
      texto: 'Una tarjeta por área: verde si anda bien, roja si hay algo para resolver (también prende el aviso del menú), gris si no aplica ahora. Tocá "Ver" para ir a resolverlo.',
    },
  ],
  historial: [
    {
      selector: '[data-tour="historial-acciones"]',
      titulo: 'Historial de acciones',
      texto: 'Quién hizo qué y cuándo: cancelaciones, ediciones, reembolsos, facturas, cajas, cambios de precios y de configuración, usuarios e inicios de sesión. En lo importante, con el antes y el después.',
    },
  ],
  errores: [
    {
      selector: '[data-tour="errores-sistema"]',
      titulo: 'Errores del sistema',
      texto: 'Los errores del servidor y los de JavaScript de los navegadores (área Navegador), agrupados: los repetidos suman. Si un usuario te pasa un código de error, buscalo acá. Cuando esté resuelto, marcalo: el aviso se apaga.',
    },
  ],
  rendimiento: [
    {
      selector: '[data-tour="rendimiento-sistema"]',
      titulo: 'Rendimiento',
      texto: 'Cuánto tarda cada endpoint de la API desde que arrancó el servidor, y las últimas requests lentas con su id: con ese id se encuentran sus líneas en el log del servidor.',
    },
  ],
};

/**
 * Sistema (SUPERADMIN): monitoreo y trazabilidad. Estado de cada área, historial de acciones,
 * errores y rendimiento. Las otras pantallas pueden mandar directo a una pestaña (?tab=errores&area=PAGOS,
 * ?tab=historial&accion=LOGIN_FALLIDO).
 */
@Component({
  selector: 'app-sistema',
  imports: [CabeceraInterna, EstadoSistemaPanel, HistorialAcciones, ErroresSistema, RendimientoSistema],
  templateUrl: './sistema.html',
  styleUrl: './sistema.css',
})
export class Sistema implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  tab = signal<Tab>('estado');
  pasosTutorial = computed(() => PASOS_POR_TAB[this.tab()]);
  /** Filtros que vinieron en la URL, para la pestaña que corresponda. */
  accionInicial = signal<string | null>(null);
  areaInicial = signal<string | null>(null);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((q) => {
      const tab = q.get('tab');
      if (tab === 'historial' || tab === 'errores' || tab === 'estado' || tab === 'rendimiento') this.tab.set(tab);
      this.accionInicial.set(q.get('accion'));
      this.areaInicial.set(q.get('area'));
    });
  }

  elegir(tab: Tab): void {
    this.router.navigate([], { queryParams: { tab }, replaceUrl: true });
  }
}
