import { Component, computed, signal } from '@angular/core';
import { ConfiguracionDiasHorarios } from './dias-horarios/dias-horarios';
import { ConfiguracionCupones } from './cupones/cupones';
import { ConfiguracionTiposEntrada } from './tipos-entrada/tipos-entrada';
import { ConfiguracionPromociones } from './promociones/promociones';
import { ConfiguracionArticulos } from './articulos/articulos';
import { ConfiguracionUsuarios } from './usuarios/usuarios';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { TourStep } from '../shared/tour/tour';

/** Cada pestaña muestra componentes hijos distintos (el resto ni existe en el DOM), así que
 * el tutorial tiene que cambiar de pasos según la pestaña activa — no hay un único recorrido
 * que sirva para las 4 a la vez. */
const PASOS_POR_TAB: Record<'dias' | 'descuentos' | 'catalogo' | 'usuarios', TourStep[]> = {
  dias: [
    {
      selector: '[data-tour="horario-general"]',
      titulo: 'Horario general',
      texto: 'Rige todos los días abiertos, salvo que cargues un horario especial para un día puntual.',
    },
    {
      selector: '[data-tour="calendario-dias"]',
      titulo: 'Calendario',
      texto: 'Tocá un día para abrirlo o cerrarlo. Ctrl+click suma varios días a la selección; Shift+click selecciona todo un rango.',
    },
    {
      selector: '[data-tour="detalle-dia"]',
      titulo: 'Detalle del día',
      texto: 'Acá se edita el día (o los días) que tengas seleccionados en el calendario.',
    },
  ],
  descuentos: [
    {
      selector: '[data-tour="promociones"]',
      titulo: 'Promociones',
      texto: 'Descuentos generales sin código, para aplicar manualmente en boletería.',
    },
    {
      selector: '[data-tour="cupones"]',
      titulo: 'Cupones',
      texto: 'Tienen código y se cargan al comprar online. Podés crear uno individual o generar un lote de varios de una.',
    },
  ],
  catalogo: [
    {
      selector: '[data-tour="tipos-entrada"]',
      titulo: 'Tipos de entrada',
      texto: 'Acá se crean los pases y extras que se venden, junto con su orden de venta en la pantalla de venta (columna Orden, con flechas para reordenar).',
    },
    {
      selector: '[data-tour="articulos-varios"]',
      titulo: 'Artículos varios',
      texto: 'Souvenirs y otros artículos que se venden sueltos en boletería, aparte de las entradas.',
    },
  ],
  usuarios: [
    {
      selector: '[data-tour="usuarios"]',
      titulo: 'Usuarios',
      texto: 'Acá creás las cuentas del personal: administradores o boleteros.',
    },
  ],
};

@Component({
  selector: 'app-configuracion',
  imports: [
    ConfiguracionDiasHorarios,
    ConfiguracionCupones,
    ConfiguracionTiposEntrada,
    ConfiguracionPromociones,
    ConfiguracionArticulos,
    ConfiguracionUsuarios,
    CabeceraInterna,
  ],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class Configuracion {
  tab = signal<'dias' | 'descuentos' | 'catalogo' | 'usuarios'>('dias');
  pasosTutorial = computed(() => PASOS_POR_TAB[this.tab()]);
}
