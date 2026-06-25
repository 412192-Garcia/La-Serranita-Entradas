import { Routes } from '@angular/router';
import {Calendario} from './calendario/calendario';

export const routes: Routes = [
  { path: 'calendario', component: Calendario, pathMatch: 'full' },

];
