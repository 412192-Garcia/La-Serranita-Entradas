import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Tour, TourStep } from './tour';

/**
 * Los pasos pueden traer un `antes` que cambia de modo/pantalla (el POS pasa a Anticipadas para
 * explicarlo), y el tour tiene que esperar a que aparezca el elemento; si nunca aparece (ej. el
 * POS sin caja abierta), el paso se muestra centrado en vez de dejar el tour trabado sin botones.
 */
describe('Tour', () => {
  const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

  function montar(pasos: TourStep[]) {
    const fixture = TestBed.createComponent(Tour);
    fixture.componentRef.setInput('pasos', pasos);
    fixture.componentRef.setInput('activo', true);
    fixture.detectChanges();
    return { fixture, tour: fixture.componentInstance };
  }

  it('corre el `antes` de cada paso al llegar, y otra vez al retroceder', async () => {
    const antes1 = vi.fn();
    const antes2 = vi.fn();
    const objetivo = document.createElement('div');
    objetivo.setAttribute('data-tour', 'objetivo');
    document.body.appendChild(objetivo);

    try {
      const { tour } = montar([
        { selector: '[data-tour="objetivo"]', titulo: 'Uno', texto: '', antes: antes1 },
        { selector: '[data-tour="objetivo"]', titulo: 'Dos', texto: '', antes: antes2 },
      ]);
      await esperar(50);
      expect(antes1).toHaveBeenCalledTimes(1);
      expect(antes2).not.toHaveBeenCalled();

      tour.siguiente();
      await esperar(50);
      expect(antes2).toHaveBeenCalledTimes(1);

      tour.anterior();
      await esperar(50);
      expect(antes1).toHaveBeenCalledTimes(2);
    } finally {
      objetivo.remove();
    }
  });

  it('espera al elemento que el `antes` hace aparecer', async () => {
    const { tour } = montar([
      {
        selector: '[data-tour="aparece-despues"]',
        titulo: 'Uno',
        texto: '',
        // Simula el cambio de modo: el elemento entra al DOM recién en el próximo ciclo.
        antes: () => {
          setTimeout(() => {
            const el = document.createElement('div');
            el.setAttribute('data-tour', 'aparece-despues');
            // Sin layout real en el entorno de pruebas: se simulan las medidas.
            el.getBoundingClientRect = () => ({ top: 10, left: 10, width: 100, height: 40, bottom: 50, right: 110 }) as DOMRect;
            document.body.appendChild(el);
          }, 100);
        },
      },
    ]);

    try {
      await esperar(400);
      expect(tour.rectObjetivo()).not.toBeNull();
      expect(tour.posicionPopover()).not.toBeNull();
    } finally {
      document.querySelector('[data-tour="aparece-despues"]')?.remove();
    }
  });

  it('sin el elemento principal resalta el alternativo; con él, resalta el principal', async () => {
    const alternativo = document.createElement('div');
    alternativo.setAttribute('data-tour', 'alternativo');
    // Sin layout real en el entorno de pruebas: se simulan las medidas de cada elemento.
    const medida = (width: number, top: number) => () => ({ top, left: 10, width, height: 30, bottom: top + 30, right: 10 + width }) as DOMRect;
    alternativo.getBoundingClientRect = medida(300, 10);
    const principal = document.createElement('div');
    principal.setAttribute('data-tour', 'principal');
    principal.getBoundingClientRect = medida(80, 200);
    document.body.appendChild(alternativo);

    try {
      const paso: TourStep = { selector: '[data-tour="principal"]', alternativo: '[data-tour="alternativo"]', titulo: 'Uno', texto: '' };

      // Sin el principal (ej. no hay regalos): se resalta el alternativo.
      const a = montar([paso]);
      await esperar(100);
      expect(a.tour.rectObjetivo()?.width).toBe(300);
      a.fixture.destroy();

      // Con el principal presente, gana sobre el alternativo.
      document.body.appendChild(principal);
      const b = montar([paso]);
      await esperar(100);
      expect(b.tour.rectObjetivo()?.width).toBe(80);
    } finally {
      alternativo.remove();
      principal.remove();
    }
  });

  it('ignora un elemento presente pero oculto (sin medidas) y usa el alternativo', async () => {
    // Como el modal de cierre de Cajas: está en el DOM, pero escondido con CSS, así que mide todo en cero.
    const oculto = document.createElement('div');
    oculto.setAttribute('data-tour', 'oculto');
    oculto.getBoundingClientRect = () => ({ top: 0, left: 0, width: 0, height: 0, bottom: 0, right: 0 }) as DOMRect;
    const visible = document.createElement('div');
    visible.setAttribute('data-tour', 'visible');
    visible.getBoundingClientRect = () => ({ top: 20, left: 20, width: 150, height: 30, bottom: 50, right: 170 }) as DOMRect;
    document.body.append(oculto, visible);

    try {
      const { tour } = montar([{ selector: '[data-tour="oculto"]', alternativo: '[data-tour="visible"]', titulo: 'Uno', texto: '' }]);
      await esperar(150);
      expect(tour.rectObjetivo()?.width).toBe(150);
    } finally {
      oculto.remove();
      visible.remove();
    }
  });

  it('si el elemento no existe, muestra el paso centrado y sin resaltar (no queda trabado)', async () => {
    const { tour } = montar([{ selector: '[data-tour="no-existe"]', titulo: 'Uno', texto: '' }]);

    await esperar(1700); // MAX_ESPERA_ELEMENTO_MS + margen
    expect(tour.rectObjetivo()).toBeNull();
    expect(tour.posicionPopover()).not.toBeNull();
  });
});
