import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SwUpdate } from '@angular/service-worker';
import { EMPTY } from 'rxjs';
import { describe, expect, it, beforeEach } from 'vitest';
import { App } from './app';

/**
 * App inyecta SwUpdate (banner de "hay una versión nueva"), Router y SesionService — que a su
 * vez pide HttpClient. El TestBed no los trae solos, así que van acá: SwUpdate como doble con
 * isEnabled en false, que es el camino de un navegador sin service worker registrado y el que
 * corresponde en un test.
 */
describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SwUpdate, useValue: { isEnabled: false, versionUpdates: EMPTY } },
      ],
    }).compileComponents();
  });

  it('se crea', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('sin actualización pendiente no muestra el banner de versión nueva', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.banner-actualizacion')).toBeNull();
  });

  it('con actualización disponible muestra el banner y su botón', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.componentInstance.actualizacionDisponible.set(true);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.banner-actualizacion')?.textContent).toContain('versión nueva');
    expect(compiled.querySelector('.banner-actualizacion button')?.textContent?.trim()).toBe('Actualizar');
  });
});
