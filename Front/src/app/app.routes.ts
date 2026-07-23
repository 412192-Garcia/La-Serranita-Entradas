import { Routes } from '@angular/router';
import {Calendario} from './calendario/calendario';
import {SeleccionEntradas} from './seleccion-entradas/seleccion-entradas';
import {Entradas} from './entradas/entradas';
import {PagoExitoso} from './resultado-pago/pago-exitoso/pago-exitoso';
import {PagoFallido} from './resultado-pago/pago-fallido/pago-fallido';

export const routes: Routes = [
  { path: 'calendario', component: Calendario, pathMatch: 'full' },
  { path: 'seleccion', component: SeleccionEntradas, pathMatch: 'full' },
  { path: 'entradas', component: Entradas, pathMatch: 'full' },
  { path: 'pago-exitoso', component: PagoExitoso },
  { path: 'pago-fallido', component: PagoFallido },

];
