import { describe, expect, it } from 'vitest';
import { leerToken } from './sesion.service';

/** Arma un JWT de juguete: la firma no importa, leerToken no la valida. El payload va en base64url y sin relleno, como el real. */
function jwt(payload: object): string {
  const b64url = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64url({ alg: 'HS256' })}.${b64url(payload)}.firma`;
}

describe('leerToken', () => {
  it('lee el vencimiento y si es de sesión larga', () => {
    const t = leerToken(jwt({ sub: 'admin', exp: 2_000_000, mantener: true }));

    expect(t).toEqual({ venceEn: 2_000_000_000, sesionLarga: true });
  });

  it('un token sin el claim mantener es de turno', () => {
    expect(leerToken(jwt({ exp: 100 }))?.sesionLarga).toBe(false);
  });

  it('decodifica el payload sin importar cuánto relleno le faltaba', () => {
    // El largo del base64 sin "=" puede dar resto 0, 2 o 3 módulo 4 (el 1 no existe): se cubren todos.
    const restos = new Set<number>();
    for (let relleno = 0; relleno < 4; relleno++) {
      const token = jwt({ exp: 100, mantener: true, r: 'x'.repeat(relleno) });
      restos.add(token.split('.')[1].length % 4);
      expect(leerToken(token)).toEqual({ venceEn: 100_000, sesionLarga: true });
    }
    expect(restos.size).toBeGreaterThanOrEqual(3);
  });

  it('decodifica los caracteres propios de base64url (- y _)', () => {
    // Estos caracteres codifican como "+" y "/" en base64 común: en base64url pasan a "-" y "_".
    const token = jwt({ exp: 100, mantener: true, u: '>>>???~~~' });

    expect(token.split('.')[1]).toMatch(/[-_]/);
    expect(leerToken(token)).toEqual({ venceEn: 100_000, sesionLarga: true });
  });

  it('devuelve null si no se puede leer', () => {
    expect(leerToken('')).toBeNull();
    expect(leerToken('sin-puntos')).toBeNull();
    expect(leerToken('a.%%%.c')).toBeNull();
    expect(leerToken(jwt({ sub: 'sin-exp' }))).toBeNull();
  });
});
