import { Component, signal } from '@angular/core';
import { ConfiguracionDiasHorarios } from './dias-horarios/dias-horarios';
import { ConfiguracionCupones } from './cupones/cupones';
import { ConfiguracionTiposEntrada } from './tipos-entrada/tipos-entrada';
import { ConfiguracionReportes } from './reportes/reportes';
import { ConfiguracionUsuarios } from './usuarios/usuarios';
import { CabeceraInterna, EnlaceCabecera } from '../shared/cabecera-interna/cabecera-interna';

@Component({
  selector: 'app-configuracion',
  imports: [ConfiguracionDiasHorarios, ConfiguracionCupones, ConfiguracionTiposEntrada, ConfiguracionReportes, ConfiguracionUsuarios, CabeceraInterna],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class Configuracion {
  tab = signal<'dias' | 'cupones' | 'tipos' | 'reportes' | 'usuarios'>('dias');

  readonly enlaceBoleteria: EnlaceCabecera = { texto: 'Volver a Boletería', ruta: '/boleteria' };
}
