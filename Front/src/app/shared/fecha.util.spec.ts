import { describe, expect, it } from 'vitest';
import { aFechaHoraISO, aFechaISO, aHoraLocalSinZona } from './fecha.util';

describe('fecha.util', () => {
  it('aFechaISO da la fecha local en yyyy-MM-dd', () => {
    expect(aFechaISO(new Date(2026, 8, 3))).toBe('2026-09-03');
  });

  it('aFechaHoraISO da fecha+hora LOCAL sin Z (no UTC)', () => {
    const d = new Date(2026, 8, 3, 20, 5, 9); // 20:05:09 hora local
    expect(aFechaHoraISO(d)).toBe('2026-09-03T20:05:09');
  });

  it('aFechaHoraISO no arrastra el desfasaje de toISOString cerca de medianoche', () => {
    const d = new Date(2026, 8, 3, 23, 30, 0);
    // toISOString() en ART (UTC-3) daría "2026-09-04T02:30..." — el día cambiado.
    expect(aFechaHoraISO(d).slice(0, 10)).toBe('2026-09-03');
  });

  describe('aHoraLocalSinZona', () => {
    it('deja igual lo que ya viene sin zona', () => {
      expect(aHoraLocalSinZona('2026-09-03T20:05:09')).toBe('2026-09-03T20:05:09');
    });

    it('convierte a hora local un timestamp en UTC de la cola offline vieja', () => {
      // Lo que dejaba toISOString() antes del cambio de formato. El instante es el mismo:
      // sólo se reescribe en la hora local del dispositivo, que es lo que el backend espera.
      const utc = '2026-09-04T02:30:00.000Z';
      expect(aHoraLocalSinZona(utc)).toBe(aFechaHoraISO(new Date(utc)));
      expect(aHoraLocalSinZona(utc)).not.toMatch(/Z$/);
    });

    it('también saca un offset explícito', () => {
      const conOffset = '2026-09-03T23:30:00-03:00';
      expect(aHoraLocalSinZona(conOffset)).toBe(aFechaHoraISO(new Date(conOffset)));
    });

    it('no rompe con un valor corrupto: lo devuelve tal cual', () => {
      expect(aHoraLocalSinZona('cualquier-cosaZ')).toBe('cualquier-cosaZ');
    });
  });
});
