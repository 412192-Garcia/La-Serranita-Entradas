const UMBRAL_MS_ENTRE_TECLAS = 150;
const LARGO_MINIMO_ESCANEO = 6;

/**
 * Los lectores de código de barras PDF417 (los que leen el dorso del DNI argentino) se
 * comportan como un teclado: "tipean" el texto decodificado en el campo con foco y rematan
 * con Enter, igual que si alguien lo escribiera a mano y apretara Enter. El DNI no viene solo:
 * el PDF417 trae varios campos separados por "@" (apellido@nombre@sexo@dni@ejemplar@fechaNacimiento@...),
 * así que hay que extraer el campo del documento en vez de usar el texto crudo tal cual.
 */
export function extraerDniDeEscaneo(valorCrudo: string): string {
  const limpio = valorCrudo.trim();
  if (limpio.includes('@')) {
    const dni = limpio.split('@')[3]?.trim();
    if (dni && /^\d+$/.test(dni)) return dni;
  }
  return limpio.replace(/\D/g, '') || limpio;
}

/**
 * Detecta un escaneo a partir de eventos de teclado globales: acumula teclas mientras lleguen
 * rápido (por debajo de UMBRAL_MS_ENTRE_TECLAS entre una y la siguiente — un lector es mucho
 * más rápido que tipear a mano) y avisa con el texto crudo cuando remata en Enter. Reutilizado
 * por Boletería (buscador) y POS (escaneo de fondo).
 */
export class DetectorEscaneoDni {
  private buffer = '';
  private ultimoKeyMs = 0;

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
      const escaneo = this.buffer;
      this.buffer = '';
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
    }
  }
}
