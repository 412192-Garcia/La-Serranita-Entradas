const UMBRAL_MS_ENTRE_TECLAS = 150;
/**
 * Silencio tras la última tecla que se toma como "fin del escaneo" cuando el lector no manda
 * Enter. Un poco mayor que UMBRAL_MS_ENTRE_TECLAS: si el hueco fuera menor, todavía podría estar
 * llegando el resto del escaneo.
 */
const MS_FIN_ESCANEO = 180;
const LARGO_MINIMO_ESCANEO = 6;

/**
 * Los lectores de código de barras PDF417 (los que leen el DNI argentino) se comportan como
 * un teclado: "tipean" el texto decodificado en el campo con foco y (casi siempre) rematan con
 * Enter, igual que si alguien lo escribiera a mano y apretara Enter. El DNI no viene solo: el
 * PDF417 trae varios campos con un separador, y hay dos formatos según la edad del ejemplar:
 *
 *  - Nuevo (tarjeta, separador `"`):
 *    `tramite"apellidos"nombres"sexo"dni"ejemplar"fechaNac"fechaEmision"..." → el documento va 5º.
 *    Ej: `00589056703"GARCIA TINI"TOMAS JEREMIAS"M"46308241"A"03-02-2005"06-04-2019"204`
 *  - Viejo (libreta y algunos lectores, separador `@`):
 *    `apellidos@nombres@sexo@dni@ejemplar@fechaNac@...` → el documento va 4º.
 *
 * Por eso hay que extraer el campo del documento en vez de usar el texto crudo tal cual.
 */
export function esEscaneoDocumento(valorCrudo: string): boolean {
  return valorCrudo.includes('"') || valorCrudo.includes('@');
}

export function extraerDniDeEscaneo(valorCrudo: string): string {
  const limpio = valorCrudo.trim();
  if (limpio.includes('"')) {
    const dni = limpio.split('"')[4]?.trim();
    if (dni && /^\d+$/.test(dni)) return dni;
  }
  if (limpio.includes('@')) {
    const dni = limpio.split('@')[3]?.trim();
    if (dni && /^\d+$/.test(dni)) return dni;
  }
  return limpio.replace(/\D/g, '') || limpio;
}

/**
 * Detecta un escaneo a partir de eventos de teclado globales: acumula teclas mientras lleguen
 * rápido (por debajo de UMBRAL_MS_ENTRE_TECLAS entre una y la siguiente — un lector es mucho
 * más rápido que tipear a mano) y avisa con el texto crudo.
 *
 * El escaneo se cierra de dos formas: al recibir Enter (el sufijo habitual del lector), o —si
 * el lector no está configurado para mandar Enter— tras MS_FIN_ESCANEO de silencio, siempre que
 * lo acumulado tenga pinta de escaneo (formato PDF417 con separadores, o una tirada larga de
 * dígitos como el código del frente del DNI) y no de tecleo suelto sobre la página.
 *
 * Reutilizado por Boletería (buscador) y POS (escaneo de fondo). Llamar a destruir() desde el
 * ngOnDestroy del componente para cancelar cualquier cierre por silencio pendiente.
 */
export class DetectorEscaneoDni {
  private buffer = '';
  private ultimoKeyMs = 0;
  private finEscaneoTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly alDetectar: (textoCrudo: string) => void) {}

  /** Llamar desde un @HostListener('window:keydown') del componente. Ignora todo mientras el
   * foco esté en un campo de texto, para no interferir con el tipeo normal. */
  procesarTecla(event: KeyboardEvent): void {
    const activo = document.activeElement;
    const escribiendoEnCampo =
      activo instanceof HTMLInputElement ||
      activo instanceof HTMLTextAreaElement ||
      activo instanceof HTMLSelectElement;
    if (escribiendoEnCampo) return;

    if (event.key === 'Enter') {
      const escaneo = this.tomarBuffer();
      if (escaneo.length >= LARGO_MINIMO_ESCANEO) {
        event.preventDefault();
        this.alDetectar(escaneo);
      }
      return;
    }

    if (event.key.length === 1) {
      const ahora = Date.now();
      if (ahora - this.ultimoKeyMs > UMBRAL_MS_ENTRE_TECLAS) {
        this.buffer = '';
      }
      this.buffer += event.key;
      this.ultimoKeyMs = ahora;

      // Rearmar el cierre por silencio: si no llega ninguna tecla más (ni Enter) en
      // MS_FIN_ESCANEO, damos el escaneo por terminado.
      if (this.finEscaneoTimer) clearTimeout(this.finEscaneoTimer);
      this.finEscaneoTimer = setTimeout(() => this.cerrarPorSilencio(), MS_FIN_ESCANEO);
    }
  }

  /** Cancela cualquier cierre por silencio pendiente. Llamar desde ngOnDestroy. */
  destruir(): void {
    if (this.finEscaneoTimer) {
      clearTimeout(this.finEscaneoTimer);
      this.finEscaneoTimer = null;
    }
  }

  private tomarBuffer(): string {
    this.destruir();
    const escaneo = this.buffer;
    this.buffer = '';
    return escaneo;
  }

  private cerrarPorSilencio(): void {
    const escaneo = this.tomarBuffer();
    // Sin Enter no hay señal explícita de "esto fue un lector": sólo disparamos si lo acumulado
    // tiene forma de escaneo (separadores PDF417 o >=7 dígitos seguidos), nunca por unas pocas
    // teclas sueltas que alguien haya apretado con el foco fuera de un campo.
    const pareceEscaneo = esEscaneoDocumento(escaneo) || /^\d{7,}$/.test(escaneo.trim());
    if (escaneo.length >= LARGO_MINIMO_ESCANEO && pareceEscaneo) {
      this.alDetectar(escaneo);
    }
  }
}
