import { computed, Component, OnInit, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Spinner } from '../../shared/spinner/spinner';
import { CodigoModulo, EstadoModulo, ModulosService } from '../../services/modulos.service';

/**
 * Sistema > Módulos (SUPERADMIN): prender y apagar partes del sistema (facturación y sus
 * submódulos, reservas en efectivo, cobro con Point). Los Points en sí (nombre y modo) se manejan
 * en Configuración > Points.
 */
@Component({
  selector: 'app-modulos-sistema',
  imports: [NgTemplateOutlet, FormsModule, Spinner],
  templateUrl: './modulos-sistema.html',
  styleUrl: './modulos-sistema.css',
})
export class ModulosSistema implements OnInit {
  private modulosService = inject(ModulosService);

  readonly modulos = this.modulosService.modulos;
  /** Módulos principales; sus submódulos se muestran adentro (ver hijos). */
  readonly principales = computed(() => this.modulos().filter((m) => !m.padre));

  hijos(codigo: CodigoModulo): EstadoModulo[] {
    return this.modulos().filter((m) => m.padre === codigo);
  }
  /** Módulo que se está por apagar: se pide confirmar antes (apagar la facturación no es un toque al pasar). */
  confirmandoApagar = signal<CodigoModulo | null>(null);
  guardando = signal<CodigoModulo | null>(null);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.modulosService.actualizar();
  }

  cambiar(m: EstadoModulo): void {
    if (m.activo && this.confirmandoApagar() !== m.codigo) {
      this.confirmandoApagar.set(m.codigo);
      return;
    }
    this.confirmandoApagar.set(null);
    this.guardando.set(m.codigo);
    this.error.set(null);
    this.modulosService.cambiar(m.codigo, !m.activo).subscribe({
      next: () => this.guardando.set(null),
      error: (err) => {
        this.guardando.set(null);
        this.error.set(this.mensaje(err, 'No se pudo cambiar el módulo.'));
      },
    });
  }

  private mensaje(err: unknown, porDefecto: string): string {
    if (err instanceof HttpErrorResponse && typeof err.error === 'string' && err.error) return err.error;
    return porDefecto;
  }
}
