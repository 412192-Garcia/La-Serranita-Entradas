import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';
import { SesionService } from './Services/sesion.service';

/** Rutas del módulo público (compra de entradas): comparten bundle/injector con el módulo
 *  interno, así que necesitan excluirse a mano — nunca deben pintarse con un tema personalizado. */
const RUTAS_PUBLICAS = ['/entradas', '/pago-exitoso', '/pago-fallido'];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Front');

  private swUpdate = inject(SwUpdate);
  private router = inject(Router);
  private sesion = inject(SesionService);

  /** Se instaló una versión nueva en segundo plano; falta que el usuario recargue para usarla. */
  actualizacionDisponible = signal(false);
  actualizando = signal(false);

  constructor() {
    if (this.swUpdate.isEnabled) {
      this.swUpdate.versionUpdates
        .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
        .subscribe(() => this.actualizacionDisponible.set(true));
    }

    // El tema personalizado es un beneficio del módulo interno (gestión): la compra pública de
    // entradas tiene que verse siempre con el verde fijo del parque, sin importar si el
    // dispositivo tiene guardada una sesión de staff con colores propios.
    this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => {
      const esRutaPublica = RUTAS_PUBLICAS.some((ruta) => e.urlAfterRedirects.startsWith(ruta));
      this.sesion.aplicarTema(esRutaPublica ? null : this.sesion.usuario());
    });
  }

  actualizar(): void {
    this.actualizando.set(true);
    this.swUpdate.activateUpdate().then(() => document.location.reload());
  }
}
