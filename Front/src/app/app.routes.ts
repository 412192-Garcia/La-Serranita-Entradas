import { Routes } from '@angular/router';
import {Calendario} from './calendario/calendario';
import {SeleccionEntradas} from './seleccion-entradas/seleccion-entradas';
import {Entradas} from './entradas/entradas';
import {PagoExitoso} from './resultado-pago/pago-exitoso/pago-exitoso';
import {PagoFallido} from './resultado-pago/pago-fallido/pago-fallido';
import {rolGuard} from './guards/rol-guard';

export const routes: Routes = [
  // ---------- Módulo web (público) ----------
  // Va en el bundle inicial: es lo que se embebe en el sitio del parque.
  { path: 'entradas', component: Entradas, pathMatch: 'full' },
  { path: 'pago-exitoso', component: PagoExitoso },
  { path: 'pago-fallido', component: PagoFallido },

  // ---------- Módulo interno (boletería/gestión) ----------
  // Se carga bajo demanda: sólo lo usa el staff, así que no tiene sentido que
  // cada visitante que entra a comprar una entrada se lo baje también.
  {
    path: 'login',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'boleteria',
    loadComponent: () => import('./boleteria/boleteria').then((m) => m.Boleteria),
    canActivate: [rolGuard(['BOLETERO', 'ADMIN'])],
  },
  {
    path: 'pos',
    loadComponent: () => import('./pos/pos').then((m) => m.Pos),
    canActivate: [rolGuard(['BOLETERO', 'ADMIN'])],
  },
  {
    path: 'crear-reserva',
    loadComponent: () => import('./crear-reserva/crear-reserva').then((m) => m.CrearReserva),
    canActivate: [rolGuard(['ADMIN'])],
  },
  {
    path: 'configuracion',
    loadComponent: () => import('./configuracion/configuracion').then((m) => m.Configuracion),
    canActivate: [rolGuard(['ADMIN'])],
  },
  {
    path: 'reportes',
    loadComponent: () => import('./configuracion/reportes/reportes').then((m) => m.ConfiguracionReportes),
    canActivate: [rolGuard(['ADMIN'])],
  },
  {
    path: 'cajas',
    loadComponent: () => import('./configuracion/cajas/cajas').then((m) => m.ConfiguracionCajas),
    canActivate: [rolGuard(['ADMIN'])],
  },
];
