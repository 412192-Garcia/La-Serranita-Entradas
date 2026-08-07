import { Component, signal } from '@angular/core';
import { ConfiguracionDiasHorarios } from './dias-horarios/dias-horarios';
import { ConfiguracionCupones } from './cupones/cupones';
import { ConfiguracionTiposEntrada } from './tipos-entrada/tipos-entrada';
import { ConfiguracionPromociones } from './promociones/promociones';
import { ConfiguracionArticulos } from './articulos/articulos';
import { ConfiguracionReportes } from './reportes/reportes';
import { ConfiguracionCajas } from './cajas/cajas';
import { ConfiguracionUsuarios } from './usuarios/usuarios';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';

@Component({
  selector: 'app-configuracion',
  imports: [
    ConfiguracionDiasHorarios,
    ConfiguracionCupones,
    ConfiguracionTiposEntrada,
    ConfiguracionPromociones,
    ConfiguracionArticulos,
    ConfiguracionReportes,
    ConfiguracionCajas,
    ConfiguracionUsuarios,
    CabeceraInterna,
  ],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class Configuracion {
  tab = signal<'dias' | 'descuentos' | 'catalogo' | 'reportes' | 'cajas' | 'usuarios'>('dias');
}
