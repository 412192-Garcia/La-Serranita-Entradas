import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ResumenCierre } from './resumen-cierre';
import { CajaService, Caja, OperacionCaja } from '../../services/caja.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { ConfiguracionService } from '../../services/configuracion.service';
import { TipoEntrada } from '../../models/tipo-entrada';

/**
 * La matriz de revisión parte cada tamaño de grupo en sub-filas por descuento (tal cual fue:
 * −15%, −$500, o normal), para que mover una venta con descuento arrastre su monto REAL y no la
 * tarifa de lista.
 */
describe('ResumenCierre — matriz con descuentos', () => {
  let corregirCaja: ReturnType<typeof vi.fn>;

  const general: TipoEntrada = {
    id: 1, nombre: 'General', descripcion: '', precio: 9000, activo: true,
    obligatorio: true, tipo: 'ENTRADA', maximoPorDia: null, entregaEntrada: true, orden: null, soloPos: false,
  };

  function venta(
    compraId: number,
    monto: number,
    desc: { pct?: number; monto?: number } = {},
  ): OperacionCaja {
    return {
      tipo: 'VENTA',
      fecha: '2026-08-15T13:00:00',
      monto,
      montoArticulos: 0,
      segmentosEntrada: [{
        tipoEntradaId: 1, tipoNombre: 'General', cantidad: 2, monto,
        descuentoPorcentaje: desc.pct ?? null, descuentoMonto: desc.monto ?? null,
      }],
      pagoEnDolares: false,
      formaPago: 'EFECTIVO_BOLETERIA',
      detalle: '2x General',
      compraId,
    };
  }

  function cajaCon(operaciones: OperacionCaja[]): Caja {
    return {
      id: 7, estado: 'CERRADA', fechaApertura: '2026-08-15T08:00:00', montoInicial: 5000,
      fechaCierre: '2026-08-15T18:00:00',
      // conteoEfectivo consistente con montoContado: el recuento precargado no cuenta como "modificado".
      totalVentasEfectivo: 34200, totalRetiros: 0, efectivoEsperado: 40000, montoContado: 40000,
      diferencia: 0, cambioContado: 0, retiros: [],
      conteoEfectivo: [{ denominacion: 20000, cantidad: 2 }],
      totalVentasTarjeta: 0, totalVentasQr: 0, totalCerradoTarjeta: 0, totalCerradoQr: 0,
      diferenciaTarjeta: 0, diferenciaQr: 0, totalVentasPosnet: null, totalCerradoPosnet: null,
      diferenciaPosnet: null, cierresPosnet: [],
      entradasFisicasInicial: 100, entradasFisicasRestantes: 4, entradasFisicasEsperadas: 4,
      diferenciaEntradas: 0, totalIngresosEntradas: 0, ingresosEntradas: [],
      operaciones, ajustes: [], totalEntradasPagas: 4, entradasVendidasPorTipo: [],
      huboVentaDolares: false, dolaresEsperado: null, dolaresContado: null, diferenciaDolares: null,
      habilitada: true,
    };
  }

  function montar(operaciones: OperacionCaja[]) {
    const fixture = TestBed.createComponent(ResumenCierre);
    fixture.componentRef.setInput('caja', cajaCon(operaciones));
    const c = fixture.componentInstance as any;
    c.modoRevision.set(true);
    fixture.detectChanges(); // monta el <app-conteo-cierre> del modo revisión
    return c;
  }

  const filas = (c: any) => c.matrices().find((m: any) => m.tipoId === 1).filas.filter((f: any) => f.cantidad === 2);
  const fila = (c: any, descKey: string) => filas(c).find((f: any) => f.descKey === descKey);
  const ajustesEnviados = () => corregirCaja.mock.calls[0][1].ajustes;

  beforeEach(async () => {
    corregirCaja = vi.fn().mockReturnValue(of({}));
    await TestBed.configureTestingModule({
      imports: [ResumenCierre],
      providers: [
        { provide: TipoEntradaService, useValue: { getTiposEntrada: () => of([general]) } },
        { provide: ConfiguracionService, useValue: { getDescuentosEfectivo: () => of([]) } },
        {
          provide: CajaService,
          useValue: { corregirCaja, eliminarAjuste: () => of({}), deshabilitarCaja: () => of({}) },
        },
      ],
    }).compileComponents();
  });

  it('separa venta normal, con −% y con −$ en filas distintas', () => {
    const c = montar([
      venta(10, 18000),
      venta(11, 16200, { pct: 10 }),
      venta(12, 17500, { monto: 500 }),
    ]);
    expect(filas(c).map((f: any) => f.descKey).sort()).toEqual(['0', 'M500', 'P10']);
    expect(fila(c, 'P10').descLabel).toBe('−10%');
    expect(fila(c, 'M500').descLabel).toBe('−$500');
    expect(fila(c, '0').descLabel).toBeNull();
  });

  it('mover la venta −10% genera un ajuste por el monto real cobrado', () => {
    const c = montar([venta(10, 18000), venta(11, 16200, { pct: 10 })]);
    const f = fila(c, 'P10');

    c.quitar(1, 2, f.descKey, 'EFECTIVO_BOLETERIA');
    c.agregar(1, 2, f.desc, 'TARJETA');

    expect(c.deltas().EFECTIVO_BOLETERIA).toBe(-16200);
    expect(c.deltas().TARJETA).toBe(16200);

    c.aplicar();
    expect(ajustesEnviados()).toHaveLength(1);
    expect(ajustesEnviados()[0]).toMatchObject({
      formaOrigen: 'EFECTIVO_BOLETERIA', formaDestino: 'TARJETA', monto: 16200, cantidadVentas: 1,
    });
  });

  it('mover la venta con descuento fijo (−$500) arrastra su monto real', () => {
    const c = montar([venta(10, 18000), venta(12, 17500, { monto: 500 })]);
    const f = fila(c, 'M500');

    c.quitar(1, 2, f.descKey, 'EFECTIVO_BOLETERIA');
    c.agregar(1, 2, f.desc, 'TARJETA');

    c.aplicar();
    expect(ajustesEnviados()[0].monto).toBe(17500);
  });

  it('mover la venta sin descuento genera un ajuste por la tarifa de lista', () => {
    const c = montar([venta(10, 18000), venta(11, 16200, { pct: 10 })]);
    const f = fila(c, '0');

    c.quitar(1, 2, f.descKey, 'EFECTIVO_BOLETERIA');
    c.agregar(1, 2, f.desc, 'TARJETA');

    c.aplicar();
    expect(ajustesEnviados()[0].monto).toBe(18000);
  });

  it('agregar una venta no registrada con descuento % usa el monto con descuento', () => {
    const c = montar([venta(10, 18000)]);

    c.nuevaVentaTipoId.set(1);
    c.nuevaVentaCantidad.set(2);
    c.nuevaVentaForma.set('TARJETA');
    c.nuevaVentaDescModo.set('porcentaje');
    c.nuevaVentaDescValor.set(10);
    expect(c.nuevaVentaMonto()).toBe(16200);

    c.agregarVenta();
    c.aplicar();
    expect(ajustesEnviados()).toHaveLength(1);
    expect(ajustesEnviados()[0]).toMatchObject({ formaOrigen: null, formaDestino: 'TARJETA', monto: 16200 });
  });

  it('agregar una venta no registrada con descuento fijo $', () => {
    const c = montar([venta(10, 18000)]);

    c.nuevaVentaTipoId.set(1);
    c.nuevaVentaCantidad.set(2);
    c.nuevaVentaForma.set('EFECTIVO_BOLETERIA');
    c.nuevaVentaDescModo.set('monto');
    c.nuevaVentaDescValor.set(500);
    expect(c.nuevaVentaMonto()).toBe(17500);

    c.agregarVenta();
    c.aplicar();
    expect(ajustesEnviados()[0].monto).toBe(17500);
  });

  it('editar el recuento del cierre (sin ajustes) manda la corrección con el nuevo conteo', () => {
    const c = montar([venta(10, 18000)]);
    const conteo = c.conteoCierre();

    // La cajera había contado mal las entradas que quedaban.
    conteo.entradasFisicasRestantes.set(9);
    expect(c.hayCambios()).toBe(true);

    c.aplicar();
    const payload = corregirCaja.mock.calls[0][1];
    expect(payload.entradasFisicasRestantes).toBe(9);
    expect(payload.ajustes).toEqual([]);
  });
});
