import { Component, computed, signal } from '@angular/core';
import { ConfiguracionDiasHorarios } from './dias-horarios/dias-horarios';
import { ConfiguracionCupones } from './cupones/cupones';
import { ConfiguracionTiposEntrada } from './tipos-entrada/tipos-entrada';
import { ConfiguracionPromociones } from './promociones/promociones';
import { ConfiguracionArticulos } from './articulos/articulos';
import { ConfiguracionUsuarios } from './usuarios/usuarios';
import { ConfiguracionPoints } from './points/points';
import { ConfiguracionFacturaPredeterminada } from './factura-predeterminada/factura-predeterminada';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { TourStep } from '../shared/tour/tour';

/** Cada pestaña muestra componentes hijos distintos (el resto ni existe en el DOM), así que
 * el tutorial tiene que cambiar de pasos según la pestaña activa — no hay un único recorrido
 * que sirva para las 4 a la vez. */
const PASOS_POR_TAB: Record<'dias' | 'descuentos' | 'catalogo' | 'usuarios' | 'cobros', TourStep[]> = {
  dias: [
    {
      selector: '[data-tour="horario-general"]',
      titulo: 'Horario general',
      texto: 'El horario de todos los días abiertos y cuántos minutos antes del cierre se deja de vender online para ese mismo día.',
    },
    {
      selector: '[data-tour="calendario-dias"]',
      titulo: 'Calendario',
      texto: 'Tocá un día para abrirlo o cerrarlo. Con Ctrl+click elegís varios y con Shift+click un rango.',
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
      texto: 'Descuentos con código para la compra online. Podés crear uno solo o un lote.',
    },
  ],
  catalogo: [
    {
      selector: '[data-tour="tipos-entrada"]',
      titulo: 'Tipos de entrada',
      texto: 'Los pases y extras que se venden, y el orden en que aparecen en la pantalla de venta.',
    },
    {
      selector: '[data-tour="articulos-varios"]',
      titulo: 'Artículos varios',
      texto: 'Souvenirs y otros artículos que se venden en boletería.',
    },
  ],
  usuarios: [
    {
      selector: '[data-tour="usuarios"]',
      titulo: 'Usuarios',
      texto: 'Acá creás las cuentas del personal: administradores o boleteros.',
    },
  ],
  cobros: [
    {
      selector: '[data-tour="factura-predeterminada"]',
      titulo: 'Factura por forma de pago',
      texto: 'Qué factura viene marcada en la venta según la forma de pago. El boletero la puede cambiar.',
    },
    {
      selector: '[data-tour="points"]',
      titulo: 'Points de Mercado Pago',
      texto: 'Los Points de Mercado Pago: cuáles aparecen en las tablets, con qué nombre y si cobran solos (integrados) o a mano.',
    },
  ],
};

@Component({
  selector: 'app-configuracion',
  imports: [
    ConfiguracionPoints,
    ConfiguracionFacturaPredeterminada,
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
  tab = signal<'dias' | 'descuentos' | 'catalogo' | 'usuarios' | 'cobros'>('dias');
  pasosTutorial = computed(() => PASOS_POR_TAB[this.tab()]);
}
