import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export type CodigoModulo = 'FACTURACION' | 'FACTURACION_BOLETERIA' | 'FACTURACION_ONLINE' | 'RESERVA_EFECTIVO' | 'POINT';

export interface EstadoModulo {
  codigo: CodigoModulo;
  nombre: string;
  descripcion: string;
  /** Prendido desde Sistema > Módulos. */
  activo: boolean;
  /** El servidor tiene lo que hace falta (certificado, token). Sólo funciona si están las dos cosas. */
  configurado: boolean;
  faltaConfigurar: string | null;
  /** Módulo del que depende (un submódulo sólo funciona si el suyo también está prendido); null = principal. */
  padre: CodigoModulo | null;
}

const CLAVE = 'modulos-sistema';

/**
 * Módulos que se pueden apagar desde Sistema > Módulos. Se recuerda el último estado conocido
 * para que una tablet que arranca sin señal siga mostrando lo mismo que antes del corte (igual
 * que FacturaService.habilitada).
 */
@Injectable({ providedIn: 'root' })
export class ModulosService {
  private http = inject(HttpClient);

  readonly modulos = signal<EstadoModulo[]>(this.leerGuardados());

  /** Cobro con Point prendido y con token en el servidor. */
  readonly pointActivo = computed(() => this.funciona('POINT'));

  funciona(codigo: CodigoModulo): boolean {
    const m = this.modulos().find((x) => x.codigo === codigo);
    return m !== undefined && m.activo && m.configurado && (m.padre === null || m.padre === undefined || this.funciona(m.padre));
  }

  actualizar(): void {
    this.http.get<EstadoModulo[]>(`${environment.apiBase}/interno/modulos`).subscribe({
      next: (lista) => this.guardar(lista),
      // Sin conexión se queda con el último valor conocido.
      error: () => {},
    });
  }

  /** SUPERADMIN. */
  cambiar(codigo: CodigoModulo, activo: boolean): Observable<EstadoModulo> {
    return this.http.put<EstadoModulo>(`${environment.apiBase}/interno/sistema/modulos/${codigo}`, { activo }).pipe(
      tap((m) => this.guardar(this.modulos().map((x) => (x.codigo === m.codigo ? m : x))))
    );
  }

  private guardar(lista: EstadoModulo[]): void {
    this.modulos.set(lista);
    try {
      localStorage.setItem(CLAVE, JSON.stringify(lista));
    } catch {
      // Sin storage: sólo se pierde el recuerdo entre sesiones.
    }
  }

  private leerGuardados(): EstadoModulo[] {
    try {
      return JSON.parse(localStorage.getItem(CLAVE) ?? '[]') as EstadoModulo[];
    } catch {
      return [];
    }
  }
}
