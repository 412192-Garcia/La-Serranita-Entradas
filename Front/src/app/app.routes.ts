import { Routes } from '@angular/router';
import {Calendario} from './calendario/calendario';
import {SeleccionEntradas} from './seleccion-entradas/seleccion-entradas';
import {Entradas} from './entradas/entradas';

export const routes: Routes = [
  { path: 'calendario', component: Calendario, pathMatch: 'full' },
  { path: 'seleccion', component: SeleccionEntradas, pathMatch: 'full' },
  { path: 'entradas', component: Entradas, pathMatch: 'full' }

];
