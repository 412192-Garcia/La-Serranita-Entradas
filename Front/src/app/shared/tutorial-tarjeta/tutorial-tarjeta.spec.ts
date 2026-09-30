import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TutorialTarjeta } from './tutorial-tarjeta';
import { TourStep } from '../tour/tour';

describe('TutorialTarjeta', () => {
  const PASOS: TourStep[] = [{ selector: '[data-tour="campo"]', titulo: 'Campo', texto: 'Explicación' }];

  function montar(pasos: TourStep[]) {
    const fixture = TestBed.createComponent(TutorialTarjeta);
    fixture.componentRef.setInput('pasos', pasos);
    fixture.detectChanges();
    return fixture;
  }

  it('sin pasos no muestra el botón', () => {
    const fixture = montar([]);
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('con pasos muestra el botón de tutorial (sólo el ícono, con etiqueta accesible), y tocarlo activa el recorrido', () => {
    const fixture = montar(PASOS);
    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-tutorial-tarjeta');
    expect(boton.getAttribute('aria-label')).toContain('tutorial');
    expect(boton.textContent?.trim()).toBe('');
    expect(fixture.componentInstance.activo()).toBe(false);

    boton.click();
    expect(fixture.componentInstance.activo()).toBe(true);
  });
});
