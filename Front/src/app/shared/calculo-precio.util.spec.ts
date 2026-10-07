import { describe, expect, it } from 'vitest';
import { cotizarLocalmente, ReservaParaCotizar } from './calculo-precio.util';
import { TipoEntrada } from '../models/tipo-entrada';
import { DescuentoEfectivo } from '../services/configuracion.service';

/** El tipo ya subió a 150, pero la reserva se hizo cuando valía 100 (y con escalón de grupo: 2 por 180). */
const general: TipoEntrada = {
  id: 1,
  nombre: 'General',
  descripcion: '',
  precio: 150,
  activo: true,
  obligatorio: true,
  tipo: 'ENTRADA',
  maximoPorDia: null,
  entregaEntrada: true,
  orden: 1,
  soloPos: false,
};

// Escalón ya actualizado al precio nuevo: 2 pases por 270. Lo reservado no debe verse afectado.
const escalonesNuevos: DescuentoEfectivo[] = [
  { id: 1, tipoEntradaId: 1, tipoEntradaNombre: 'General', cantidadPases: 2, precioPromocionalTotal: 270 },
];

const reserva: ReservaParaCotizar = {
  montoTotal: 180,
  preciosLista: { 1: 100 },
  cantidades: { 1: 2 },
  reusarMonto: true,
};

const sinDescuento = {};

function cotizar(formaPago: 'EFECTIVO_BOLETERIA' | 'TARJETA', cantidad: number, r: ReservaParaCotizar | null) {
  return cotizarLocalmente(
    formaPago,
    [{ tipoEntradaId: 1, cantidad }],
    [],
    sinDescuento,
    [general],
    escalonesNuevos,
    [],
    [],
    r
  );
}

describe('cotizarLocalmente con una reserva cargada', () => {
  it('sin reserva, cotiza al precio actual (comportamiento de siempre)', () => {
    expect(cotizar('TARJETA', 2, null).subtotal).toBe(300);
  });

  it('en efectivo y con lo mismo que reservó, cobra el monto reservado aunque el escalón haya cambiado', () => {
    const c = cotizar('EFECTIVO_BOLETERIA', 2, reserva);
    expect(c.subtotal).toBe(180);
    expect(c.ahorro).toBe(20); // lista al reservar (200) - 180
  });

  it('con tarjeta, usa el precio de lista de cuando reservó y no el de hoy', () => {
    expect(cotizar('TARJETA', 2, reserva).subtotal).toBe(200); // 2 x 100, no 2 x 150
  });

  it('en efectivo con otra cantidad, no reusa el monto pero sí el precio de lista congelado', () => {
    // 3 pases: no hay escalón de 3 (el mayor es 2 por 270 = 135 c/u, y 3 lo supera) -> 135 x 3
    const c = cotizar('EFECTIVO_BOLETERIA', 3, reserva);
    expect(c.subtotal).toBe(405);
  });

  it('una reserva con artículos propios no reusa el monto (su total los incluye)', () => {
    const conArticulos = { ...reserva, reusarMonto: false };
    expect(cotizar('EFECTIVO_BOLETERIA', 2, conArticulos).subtotal).toBe(270); // escalón vigente
  });
});
