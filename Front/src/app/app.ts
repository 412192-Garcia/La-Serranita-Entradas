import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Front');

  private swUpdate = inject(SwUpdate);

  /** Se instaló una versión nueva en segundo plano; falta que el usuario recargue para usarla. */
  actualizacionDisponible = signal(false);
  actualizando = signal(false);

  constructor() {
    if (this.swUpdate.isEnabled) {
      this.swUpdate.versionUpdates
        .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
        .subscribe(() => this.actualizacionDisponible.set(true));
    }
  }

  actualizar(): void {
    this.actualizando.set(true);
    this.swUpdate.activateUpdate().then(() => document.location.reload());
  }
}
