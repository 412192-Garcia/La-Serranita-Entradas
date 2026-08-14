import { Injectable } from '@angular/core';

export interface PaletaTema {
  nombre: string;
  color: string;
  /** Para saber si el tilde de "seleccionado" (y el texto que cae directo sobre el swatch) tiene que ser blanco o oscuro. */
  oscuro?: boolean;
}

/** Prearmadas de sobra a propósito: cuantas más opciones "ya probadas" haya, menos falta hace tirar del picker libre. */
export const PALETAS_PREARMADAS: PaletaTema[] = [
  { nombre: 'Verde bosque', color: '#39a935' },
  { nombre: 'Verde La Serranita', color: '#21a454' },
  { nombre: 'Azul océano', color: '#2563eb' },
  { nombre: 'Índigo', color: '#4f46e5' },
  { nombre: 'Violeta', color: '#7c3aed' },
  { nombre: 'Rosa', color: '#db2777' },
  { nombre: 'Rojo coral', color: '#dc2626' },
  { nombre: 'Naranja', color: '#ea580c' },
  { nombre: 'Marrón tierra', color: '#92400e' },
  { nombre: 'Verde lima', color: '#65a30d' },
  { nombre: 'Turquesa', color: '#0d9488' },
  { nombre: 'Celeste eléctrico', color: '#0ea5e9' },
  { nombre: 'Gris pizarra', color: '#475569' },
  { nombre: 'Ámbar', color: '#d97706' },
  { nombre: 'Fucsia', color: '#c026d3' },
  { nombre: 'Esmeralda', color: '#059669' },
  { nombre: 'Azul acero', color: '#0369a1' },
  { nombre: 'Vino', color: '#9f1239' },
  { nombre: 'Oliva', color: '#4d7c0f' },
];

/**
 * Mezcla claros y oscuros a propósito: el texto de la bajada de cada pantalla se adapta solo
 * (ver aplicarFondo). Después de los pálidos y de los oscuros casi negros hay un tercer grupo
 * "vivo": mismo criterio de contraste automático, pero con color de verdad en vez de quedar
 * lavado.
 */
export const PALETAS_FONDO: PaletaTema[] = [
  { nombre: 'Gris claro', color: '#f4f5f7' },
  { nombre: 'Blanco', color: '#ffffff' },
  { nombre: 'Beige cálido', color: '#f5f0e8' },
  { nombre: 'Celeste suave', color: '#eaf3fb' },
  { nombre: 'Lavanda suave', color: '#f2eefb' },
  { nombre: 'Menta suave', color: '#e9f7f1' },
  { nombre: 'Durazno suave', color: '#fdeee0' },
  { nombre: 'Rosa pálido', color: '#fce8f3' },
  { nombre: 'Amarillo pálido', color: '#fdf6e0' },
  { nombre: 'Verde menta', color: '#6ee7b7' },
  { nombre: 'Celeste cielo', color: '#7dd3fc' },
  { nombre: 'Lavanda vivo', color: '#c4b5fd' },
  { nombre: 'Rosa chicle', color: '#f0abfc' },
  { nombre: 'Amarillo sol', color: '#fcd34d' },
  { nombre: 'Coral', color: '#fda4af' },
  { nombre: 'Turquesa claro', color: '#5eead4' },
  { nombre: 'Esmeralda profundo', color: '#059669', oscuro: true },
  { nombre: 'Azul zafiro', color: '#0284c7', oscuro: true },
  { nombre: 'Ciruela', color: '#6d28d9', oscuro: true },
  { nombre: 'Gris oscuro', color: '#1f2430', oscuro: true },
  { nombre: 'Negro suave', color: '#17181c', oscuro: true },
  { nombre: 'Azul medianoche', color: '#1a2238', oscuro: true },
  { nombre: 'Verde bosque oscuro', color: '#16241d', oscuro: true },
  { nombre: 'Púrpura oscuro', color: '#241b33', oscuro: true },
  { nombre: 'Vino oscuro', color: '#2a1620', oscuro: true },
];

/**
 * Mezcla claros y oscuros a propósito: el texto DENTRO de las tarjetas se adapta solo (ver
 * aplicarTarjeta). Mismo grupo "vivo" intermedio que PALETAS_FONDO, entre los pálidos y los
 * casi negros.
 */
export const PALETAS_TARJETA: PaletaTema[] = [
  { nombre: 'Blanco', color: '#ffffff' },
  { nombre: 'Gris perla', color: '#f7f8fa' },
  { nombre: 'Crema', color: '#faf7f0' },
  { nombre: 'Celeste hielo', color: '#eef6fc' },
  { nombre: 'Lavanda pálida', color: '#f5f1fb' },
  { nombre: 'Durazno pálido', color: '#fdf1e7' },
  { nombre: 'Verde menta', color: '#6ee7b7' },
  { nombre: 'Celeste cielo', color: '#7dd3fc' },
  { nombre: 'Lavanda vivo', color: '#c4b5fd' },
  { nombre: 'Rosa chicle', color: '#f0abfc' },
  { nombre: 'Amarillo sol', color: '#fcd34d' },
  { nombre: 'Coral', color: '#fda4af' },
  { nombre: 'Turquesa claro', color: '#5eead4' },
  { nombre: 'Esmeralda profundo', color: '#059669', oscuro: true },
  { nombre: 'Azul zafiro', color: '#0284c7', oscuro: true },
  { nombre: 'Ciruela', color: '#6d28d9', oscuro: true },
  { nombre: 'Gris carbón', color: '#23272f', oscuro: true },
  { nombre: 'Azul noche', color: '#1e2a4a', oscuro: true },
  { nombre: 'Púrpura profundo', color: '#2a2140', oscuro: true },
  { nombre: 'Verde bosque profundo', color: '#182620', oscuro: true },
];

/** Sólo es una línea fina de borde: no hace falta que se adapte nada más al elegirla. */
export const PALETAS_BORDE: PaletaTema[] = [
  { nombre: 'Gris claro', color: '#e5e7eb' },
  { nombre: 'Gris medio', color: '#cbd5e1' },
  { nombre: 'Verde suave', color: '#bbdfc0' },
  { nombre: 'Azul suave', color: '#bfdbfe' },
  { nombre: 'Violeta suave', color: '#ddd6fe' },
  { nombre: 'Rosa suave', color: '#fbcfe8' },
  { nombre: 'Gris oscuro', color: '#3a3f4b', oscuro: true },
  { nombre: 'Gris carbón', color: '#4b5563', oscuro: true },
];

export interface DisenioPrearmado {
  nombre: string;
  colorPrimario: string;
  colorFondo: string;
  colorTarjeta: string;
  colorBorde: string;
}

/**
 * Combos ya armados: un click pisa los cuatro colores a la vez, coordinados entre sí. Para
 * quien no quiera elegir cada slot por separado. Grupo 1: variantes claras con un tinte suave
 * de color y tarjeta blanca. Grupo 2: variantes "vívidas", con el fondo en un tono de color de
 * verdad (no sólo un tinte) y la tarjeta blanca para que el contenido siga siendo fácil de
 * leer. Grupo 3: temas oscuros completos, incluyendo variantes con la tarjeta en un color
 * saturado (no gris/negro) sobre un fondo neutro oscuro.
 */
export const DISENIOS_PREARMADOS: DisenioPrearmado[] = [
  { nombre: 'Verde bosque (por defecto)', colorPrimario: '#39a935', colorFondo: '#f4f5f7', colorTarjeta: '#ffffff', colorBorde: '#e5e7eb' },
  { nombre: 'Verde selva', colorPrimario: '#059669', colorFondo: '#eafaf1', colorTarjeta: '#f3faf5', colorBorde: '#bbdfc0' },
  { nombre: 'La Serranita', colorPrimario: '#21a454', colorFondo: '#eaf7ee', colorTarjeta: '#ffffff', colorBorde: '#bfe4c9' },
  { nombre: 'Azul océano', colorPrimario: '#2563eb', colorFondo: '#eaf3fb', colorTarjeta: '#f2f8fd', colorBorde: '#bfdbfe' },
  { nombre: 'Violeta real', colorPrimario: '#7c3aed', colorFondo: '#f2eefb', colorTarjeta: '#f8f5fd', colorBorde: '#ddd6fe' },
  { nombre: 'Rosa fucsia', colorPrimario: '#db2777', colorFondo: '#fce8f3', colorTarjeta: '#fdf2f8', colorBorde: '#fbcfe8' },
  { nombre: 'Ámbar cálido', colorPrimario: '#d97706', colorFondo: '#fdf6e0', colorTarjeta: '#fefaf0', colorBorde: '#f3dba3' },
  { nombre: 'Turquesa tropical', colorPrimario: '#0d9488', colorFondo: '#5eead4', colorTarjeta: '#ffffff', colorBorde: '#99f6e4' },
  { nombre: 'Celeste vívido', colorPrimario: '#0284c7', colorFondo: '#7dd3fc', colorTarjeta: '#ffffff', colorBorde: '#bae6fd' },
  { nombre: 'Lavanda vívida', colorPrimario: '#7c3aed', colorFondo: '#c4b5fd', colorTarjeta: '#ffffff', colorBorde: '#ddd6fe' },
  { nombre: 'Fucsia vívido', colorPrimario: '#c026d3', colorFondo: '#f0abfc', colorTarjeta: '#ffffff', colorBorde: '#f5d0fe' },
  { nombre: 'Coral cálido', colorPrimario: '#dc2626', colorFondo: '#fda4af', colorTarjeta: '#ffffff', colorBorde: '#fecdd3' },
  { nombre: 'Sol amarillo', colorPrimario: '#d97706', colorFondo: '#fcd34d', colorTarjeta: '#ffffff', colorBorde: '#fde68a' },
  { nombre: 'Menta fresca', colorPrimario: '#059669', colorFondo: '#6ee7b7', colorTarjeta: '#ffffff', colorBorde: '#a7f3d0' },
  { nombre: 'Oscuro elegante', colorPrimario: '#2563eb', colorFondo: '#17181c', colorTarjeta: '#23272f', colorBorde: '#3a3f4b' },
  { nombre: 'Noche verde', colorPrimario: '#39a935', colorFondo: '#0f1a12', colorTarjeta: '#16241d', colorBorde: '#2c4433' },
  { nombre: 'Zafiro nocturno', colorPrimario: '#38bdf8', colorFondo: '#0b1220', colorTarjeta: '#0284c7', colorBorde: '#0369a1' },
  { nombre: 'Ciruela real', colorPrimario: '#c084fc', colorFondo: '#17181c', colorTarjeta: '#6d28d9', colorBorde: '#4c1d95' },
  { nombre: 'Esmeralda nocturna', colorPrimario: '#34d399', colorFondo: '#111827', colorTarjeta: '#059669', colorBorde: '#065f46' },
];

const HEX_VALIDO = /^#[0-9a-fA-F]{6}$/;

/** Debajo de esto se considera "oscuro" y hay que aclarar el texto que cae encima (fórmula de brillo percibido ITU-R BT.601). */
const UMBRAL_OSCURO = 140;

/** Tonos de texto de siempre: se usan tal cual mientras alcancen el contraste mínimo contra el fondo real elegido. */
const TEXTO_CLARO = { fuerte: '#f3f4f6', medio: '#d1d5db', suave: '#9ca3af' };
const TEXTO_OSCURO = { fuerte: '#111827', medio: '#374151', suave: '#6b7280' };

/**
 * Contraste mínimo (razón WCAG) exigido para cada nivel de texto contra el fondo real. Un tono
 * fijo (ver TEXTO_CLARO/TEXTO_OSCURO) fue elegido para verse bien contra fondos casi blancos o
 * casi negros, pero con las paletas de colores "vívidos" (ni pálidas ni casi negras, ver
 * PALETAS_FONDO/PALETAS_TARJETA) ese mismo tono fijo puede quedar con muy poco contraste — por
 * ejemplo el texto "suave" sobre la tarjeta violeta de "Ciruela real" bajaba a ~2.8:1, por
 * debajo incluso del mínimo de WCAG para texto grande. grisConContraste() recalcula el tono
 * sólo cuando el fijo no alcanza el mínimo.
 */
const CONTRASTE_FUERTE = 8;
const CONTRASTE_SECUNDARIO = 5.5;
const CONTRASTE_MUTED = 4.5;

/**
 * Aplica los colores elegidos por el usuario pisando variables CSS en :root con estilos
 * inline. Las variables derivadas (--color-primary-dark/-light, --color-input-bg, y el texto
 * de la bajada de cada pantalla) están definidas en función de las de acá en styles.css, así
 * que se recalculan solas — no hace falta pisarlas a mano.
 */
@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  /** Null o inválido = vuelve al verde por defecto de styles.css. */
  aplicarPrimario(colorTema: string | null | undefined): void {
    const root = document.documentElement.style;
    if (colorTema && HEX_VALIDO.test(colorTema)) {
      root.setProperty('--color-primary', colorTema);
    } else {
      root.removeProperty('--color-primary');
    }
  }

  /**
   * Null o inválido = vuelve al gris claro por defecto. Recalcula --color-heading-desc (el
   * único texto que hoy se apoya directo sobre el fondo de página) contra el color real con
   * contraste mínimo garantizado (ver conContrasteMinimo en aplicarTarjeta); el resto de los
   * textos vive dentro de tarjetas y se maneja aparte con aplicarTarjeta().
   */
  aplicarFondo(colorFondo: string | null | undefined): void {
    const root = document.documentElement.style;
    if (colorFondo && HEX_VALIDO.test(colorFondo)) {
      root.setProperty('--color-bg-page', colorFondo);
      const oscuro = this.esOscuro(colorFondo);
      const preferido = oscuro ? TEXTO_CLARO.medio : TEXTO_OSCURO.suave;
      root.setProperty('--color-heading-desc', ThemeService.conContrasteMinimo(colorFondo, preferido, CONTRASTE_SECUNDARIO, oscuro));
    } else {
      root.removeProperty('--color-bg-page');
      root.removeProperty('--color-heading-desc');
    }
  }

  /**
   * Null o inválido = vuelve al blanco por defecto. Recalcula los tres tonos de texto que se
   * usan DENTRO de las tarjetas (--color-text/-secondary/-muted) contra el color real elegido
   * — no sólo "claro u oscuro" a secas, sino con contraste mínimo garantizado (conContrasteMinimo):
   * un fondo casi negro o casi blanco no cambia nada (el tono fijo ya sobra de contraste), pero
   * un fondo "vívido" de contraste intermedio (ej. tarjeta violeta) aclara u oscurece el gris lo
   * que haga falta. También ajusta --color-bg-soft (más clara si la tarjeta es oscura, más
   * oscura si no). --color-input-bg sigue a --color-card-bg solo (está definida en función de
   * ella en styles.css), así que los inputs de adentro nunca quedan de un tono distinto.
   */
  aplicarTarjeta(colorTarjeta: string | null | undefined): void {
    const root = document.documentElement.style;
    if (colorTarjeta && HEX_VALIDO.test(colorTarjeta)) {
      root.setProperty('--color-card-bg', colorTarjeta);
      if (this.esOscuro(colorTarjeta)) {
        root.setProperty('--color-text', ThemeService.conContrasteMinimo(colorTarjeta, TEXTO_CLARO.fuerte, CONTRASTE_FUERTE, true));
        root.setProperty('--color-text-secondary', ThemeService.conContrasteMinimo(colorTarjeta, TEXTO_CLARO.medio, CONTRASTE_SECUNDARIO, true));
        root.setProperty('--color-text-muted', ThemeService.conContrasteMinimo(colorTarjeta, TEXTO_CLARO.suave, CONTRASTE_MUTED, true));
        // Más clara que la tarjeta (no más oscura): así "el panel de adentro" se sigue
        // notando como una zona propia en vez de fundirse con la tarjeta oscura.
        root.setProperty('--color-bg-soft', ThemeService.mezclar(colorTarjeta, 'blanco', 0.1));
      } else {
        root.setProperty('--color-text', ThemeService.conContrasteMinimo(colorTarjeta, TEXTO_OSCURO.fuerte, CONTRASTE_FUERTE, false));
        root.setProperty('--color-text-secondary', ThemeService.conContrasteMinimo(colorTarjeta, TEXTO_OSCURO.medio, CONTRASTE_SECUNDARIO, false));
        root.setProperty('--color-text-muted', ThemeService.conContrasteMinimo(colorTarjeta, TEXTO_OSCURO.suave, CONTRASTE_MUTED, false));
        root.setProperty('--color-bg-soft', ThemeService.mezclar(colorTarjeta, 'negro', 0.03));
      }
    } else {
      root.removeProperty('--color-card-bg');
      root.removeProperty('--color-text');
      root.removeProperty('--color-text-secondary');
      root.removeProperty('--color-text-muted');
      root.removeProperty('--color-bg-soft');
    }
  }

  /** Null o inválido = vuelve al gris claro por defecto. Es sólo una línea fina de borde, así que no hace falta ajustar nada más al elegirla (a diferencia de fondo/tarjeta). */
  aplicarBorde(colorBorde: string | null | undefined): void {
    const root = document.documentElement.style;
    if (colorBorde && HEX_VALIDO.test(colorBorde)) {
      root.setProperty('--color-border', colorBorde);
    } else {
      root.removeProperty('--color-border');
    }
  }

  /** Aplica los cuatro colores de un diseño prearmado de una sola vez. */
  aplicarDisenio(d: DisenioPrearmado): void {
    this.aplicarPrimario(d.colorPrimario);
    this.aplicarFondo(d.colorFondo);
    this.aplicarTarjeta(d.colorTarjeta);
    this.aplicarBorde(d.colorBorde);
  }

  private esOscuro(hex: string): boolean {
    const { r, g, b } = ThemeService.aRgb(hex);
    const brillo = (r * 299 + g * 587 + b * 114) / 1000;
    return brillo < UMBRAL_OSCURO;
  }

  private static aRgb(hex: string): { r: number; g: number; b: number } {
    return {
      r: parseInt(hex.slice(1, 3), 16),
      g: parseInt(hex.slice(3, 5), 16),
      b: parseInt(hex.slice(5, 7), 16),
    };
  }

  /** Un canal sRGB (0-255) pasado a su valor lineal (0-1), paso previo para calcular luminancia relativa (WCAG). */
  private static canalLineal(c: number): number {
    const cs = c / 255;
    return cs <= 0.03928 ? cs / 12.92 : Math.pow((cs + 0.055) / 1.055, 2.4);
  }

  /** Luminancia relativa WCAG (0 = negro, 1 = blanco), la base del cálculo de contraste real entre dos colores. */
  private static luminancia(hex: string): number {
    const { r, g, b } = ThemeService.aRgb(hex);
    return 0.2126 * ThemeService.canalLineal(r) + 0.7152 * ThemeService.canalLineal(g) + 0.0722 * ThemeService.canalLineal(b);
  }

  /** Razón de contraste WCAG entre dos colores (1 = sin contraste, 21 = blanco puro contra negro puro). */
  private static contraste(hexA: string, hexB: string): number {
    const lA = ThemeService.luminancia(hexA);
    const lB = ThemeService.luminancia(hexB);
    return (Math.max(lA, lB) + 0.05) / (Math.min(lA, lB) + 0.05);
  }

  /**
   * Gris (blanco o negro "diluido" lo justo) que llega exactamente al contraste pedido contra
   * el fondo real. Si ni el blanco ni el negro puro alcanzan (fondo casi al borde del umbral de
   * "oscuro"), se queda en el extremo disponible en vez de fallar.
   */
  private static grisConContraste(fondoHex: string, contrasteObjetivo: number, textoClaro: boolean): string {
    const lFondo = ThemeService.luminancia(fondoHex);
    const lTextoSinAcotar = textoClaro
      ? contrasteObjetivo * (lFondo + 0.05) - 0.05
      : (lFondo + 0.05) / contrasteObjetivo - 0.05;
    const lTexto = Math.min(1, Math.max(0, lTextoSinAcotar));
    const canal = lTexto <= 0.0031308 ? lTexto * 12.92 : 1.055 * Math.pow(lTexto, 1 / 2.4) - 0.055;
    const valor = Math.round(Math.min(1, Math.max(0, canal)) * 255)
      .toString(16)
      .padStart(2, '0');
    return `#${valor}${valor}${valor}`;
  }

  /**
   * El tono de texto de siempre (tonoPreferido) si ya alcanza el contraste mínimo contra el
   * fondo real; si no, un gris recalculado que sí lo alcanza. Así los fondos casi blancos o
   * casi negros (donde el tono fijo siempre sobra de contraste) se ven exactamente igual que
   * antes, y sólo los fondos de color intermedio (paletas "vívidas") activan el ajuste.
   */
  private static conContrasteMinimo(fondoHex: string, tonoPreferido: string, contrasteObjetivo: number, textoClaro: boolean): string {
    if (ThemeService.contraste(fondoHex, tonoPreferido) >= contrasteObjetivo) {
      return tonoPreferido;
    }
    return ThemeService.grisConContraste(fondoHex, contrasteObjetivo, textoClaro);
  }

  /** Mezcla un hex hacia blanco o negro un porcentaje dado (equivalente a color-mix, pero calculado acá porque la dirección depende de si el color de base es claro u oscuro). */
  private static mezclar(hex: string, hacia: 'blanco' | 'negro', porcentaje: number): string {
    const { r, g, b } = ThemeService.aRgb(hex);
    const objetivo = hacia === 'blanco' ? 255 : 0;
    const canal = (c: number) => Math.round(c + (objetivo - c) * porcentaje).toString(16).padStart(2, '0');
    return `#${canal(r)}${canal(g)}${canal(b)}`;
  }
}
