import { describe, expect, it } from 'vitest';
import { aFechaHoraISO, aFechaISO } from './fecha.util';

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
});
