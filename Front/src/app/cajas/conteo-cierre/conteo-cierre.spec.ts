import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ConteoCierre } from './conteo-cierre';
import { Caja } from '../../services/caja.service';

function cajaBase(over: Partial<Caja> = {}): Caja {
  return {
    id: 7, estado: 'CERRADA', fechaApertura: '', fechaCierre: '', montoInicial: 5000,
    totalVentasEfectivo: 0, totalRetiros: 0, efectivoEsperado: 0, montoContado: 0,
    diferencia: 0, cambioContado: 0, retiros: [], conteoEfectivo: [],
    totalVentasTarjeta: 0, totalVentasQr: 0, totalCerradoTarjeta: 0, totalCerradoQr: 0,
    diferenciaTarjeta: 0, diferenciaQr: 0, totalVentasPosnet: null, totalCerradoPosnet: null,
    diferenciaPosnet: null, cierresPosnet: [],
    entradasFisicasInicial: 100, entradasFisicasCortadas: 0, entradasFisicasEsperadas: 0,
    diferenciaEntradas: 0, totalIngresosEntradas: 0, ingresosEntradas: [],
    operaciones: [], ajustes: [], totalEntradasPagas: 0, entradasVendidasPorTipo: [],
    huboVentaDolares: false, dolaresEsperado: null, dolaresContado: null, diferenciaDolares: null,
    habilitada: true,
    ...over,
  };
}

function montar(caja: Caja) {
  const fixture = TestBed.createComponent(ConteoCierre);
  fixture.componentRef.setInput('precarga', caja);
  fixture.detectChanges();
  return fixture.componentInstance as any;
}

describe('ConteoCierre', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ConteoCierre] }).compileComponents();
  });

  it('precarga el conteo de billetes y calcula el total contado', () => {
    const c = montar(cajaBase({
      conteoEfectivo: [{ denominacion: 10000, cantidad: 4 }, { denominacion: 1000, cantidad: 3 }],
      cambioContado: 250, entradasFisicasCortadas: 12,
    }));
    expect(c.montoContadoCalculado()).toBe(43250);
    expect(c.valor().entradasFisicasCortadas).toBe(12);
  });

  it('separa los cierres de posnet por forma cuando venían separados', () => {
    const c = montar(cajaBase({
      cierresPosnet: [
        { id: 1, formaPago: 'TARJETA', monto: 1000, nota: null, fecha: '' },
        { id: 2, formaPago: 'MERCADO_PAGO_QR', monto: 500, nota: null, fecha: '' },
      ],
    }));
    expect(c.modoPosnet()).toBe('SEPARADO');
    const cierres = c.valor().cierresPosnet;
    expect(cierres).toHaveLength(2);
    expect(cierres.find((x: any) => x.formaPago === 'TARJETA').monto).toBe(1000);
  });

  it('reconoce los cierres combinados', () => {
    const c = montar(cajaBase({ cierresPosnet: [{ id: 1, formaPago: null, monto: 1500, nota: null, fecha: '' }] }));
    expect(c.modoPosnet()).toBe('COMBINADO');
    expect(c.valor().cierresPosnet).toEqual([{ formaPago: null, monto: 1500, nota: null }]);
  });

  it('valida que se cargue cuántas entradas se cortaron', () => {
    const c = montar(cajaBase({ entradasFisicasCortadas: 5 }));
    expect(c.validar()).toBeNull();
    c.entradasFisicasCortadas.set(null);
    expect(c.validar()).toContain('talonario');
  });
});
