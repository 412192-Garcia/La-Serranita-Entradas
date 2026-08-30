import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { DetectorEscaneoDni, esEscaneoDocumento, extraerDniDeEscaneo } from './escaner-dni.util';

const ESCANEO_NUEVO = '00589056703"GARCIA TINI"TOMAS JEREMIAS"M"46308241"A"03-02-2005"06-04-2019"204';
const ESCANEO_VIEJO = 'GARCIA TINI@TOMAS JEREMIAS@M@46308241@A@03/02/2005@06/04/2019';

describe('extraerDniDeEscaneo', () => {
  it('saca el documento del formato nuevo (separador ")', () => {
    expect(extraerDniDeEscaneo(ESCANEO_NUEVO)).toBe('46308241');
  });

  it('saca el documento del formato viejo (separador @)', () => {
    expect(extraerDniDeEscaneo(ESCANEO_VIEJO)).toBe('46308241');
  });

  it('deja pasar un DNI tipeado a mano', () => {
    expect(extraerDniDeEscaneo('46308241')).toBe('46308241');
    expect(extraerDniDeEscaneo(' 46.308.241 ')).toBe('46308241');
  });

  it('no toca texto libre sin separadores de escaneo', () => {
    expect(extraerDniDeEscaneo('Tomas Garcia')).toBe('Tomas Garcia');
  });
});

describe('esEscaneoDocumento', () => {
  it('reconoce ambos formatos de PDF417', () => {
    expect(esEscaneoDocumento(ESCANEO_NUEVO)).toBe(true);
    expect(esEscaneoDocumento(ESCANEO_VIEJO)).toBe(true);
  });

  it('es false para texto normal', () => {
    expect(esEscaneoDocumento('46308241')).toBe(false);
    expect(esEscaneoDocumento('tomas@mail.com')).toBe(true); // el @ del mail cae acá a propósito: lo resuelve extraerDniDeEscaneo
  });
});

describe('DetectorEscaneoDni', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function tipear(det: DetectorEscaneoDni, texto: string, gapMs = 5): void {
    for (const ch of texto) {
      det.procesarTecla(new KeyboardEvent('keydown', { key: ch }));
      vi.advanceTimersByTime(gapMs);
    }
  }

  it('detecta el escaneo cuando el lector remata con Enter', () => {
    const alDetectar = vi.fn();
    const det = new DetectorEscaneoDni(alDetectar);

    tipear(det, ESCANEO_NUEVO);
    det.procesarTecla(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(alDetectar).toHaveBeenCalledTimes(1);
    expect(alDetectar).toHaveBeenCalledWith(ESCANEO_NUEVO);
  });

  it('detecta el escaneo aunque el lector NO mande Enter (cierre por silencio)', () => {
    const alDetectar = vi.fn();
    const det = new DetectorEscaneoDni(alDetectar);

    tipear(det, ESCANEO_NUEVO);
    expect(alDetectar).not.toHaveBeenCalled(); // todavía "llegando"

    vi.advanceTimersByTime(200);

    expect(alDetectar).toHaveBeenCalledTimes(1);
    expect(alDetectar).toHaveBeenCalledWith(ESCANEO_NUEVO);
  });

  it('cierra por silencio un código de sólo dígitos (frente del DNI)', () => {
    const alDetectar = vi.fn();
    const det = new DetectorEscaneoDni(alDetectar);

    tipear(det, '46308241');
    vi.advanceTimersByTime(200);

    expect(alDetectar).toHaveBeenCalledWith('46308241');
  });

  it('NO dispara por unas pocas teclas sueltas sin Enter', () => {
    const alDetectar = vi.fn();
    const det = new DetectorEscaneoDni(alDetectar);

    tipear(det, 'hola');
    vi.advanceTimersByTime(200);

    expect(alDetectar).not.toHaveBeenCalled();
  });

  it('destruir() cancela el cierre por silencio pendiente', () => {
    const alDetectar = vi.fn();
    const det = new DetectorEscaneoDni(alDetectar);

    tipear(det, ESCANEO_NUEVO);
    det.destruir();
    vi.advanceTimersByTime(200);

    expect(alDetectar).not.toHaveBeenCalled();
  });

  it('ignora las teclas mientras el foco está en un input', () => {
    const alDetectar = vi.fn();
    const det = new DetectorEscaneoDni(alDetectar);

    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();

    tipear(det, ESCANEO_NUEVO);
    det.procesarTecla(new KeyboardEvent('keydown', { key: 'Enter' }));
    vi.advanceTimersByTime(200);

    expect(alDetectar).not.toHaveBeenCalled();
    input.remove();
  });
});
