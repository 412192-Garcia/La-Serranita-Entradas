import { describe, expect, it } from 'vitest';
import { apellidoSinNombre } from './apellido-autocompletado.util';

describe('apellidoSinNombre', () => {
  it('saca el nombre del principio del apellido', () => {
    expect(apellidoSinNombre('Juan', 'Juan Pérez')).toBe('Pérez');
  });

  it('respeta apellidos compuestos y nombres de dos palabras', () => {
    expect(apellidoSinNombre('María José', 'María José García López')).toBe('García López');
  });

  it('no distingue mayúsculas ni espacios de más', () => {
    expect(apellidoSinNombre('juan', '  JUAN   Pérez ')).toBe('Pérez');
  });

  it('no toca un apellido que no empieza con el nombre', () => {
    expect(apellidoSinNombre('Juan', 'Pérez')).toBeNull();
    expect(apellidoSinNombre('Ana', 'Anabella Ruiz')).toBeNull();
  });

  it('no deja el apellido vacío', () => {
    expect(apellidoSinNombre('Juan', 'Juan')).toBeNull();
  });

  it('sin nombre o sin apellido no hace nada', () => {
    expect(apellidoSinNombre('', 'Juan Pérez')).toBeNull();
    expect(apellidoSinNombre('Juan', null)).toBeNull();
  });
});
