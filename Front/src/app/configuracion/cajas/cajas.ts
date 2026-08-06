import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService } from '../../Services/reporte.service';
import { CajaResumen, ReporteResumen } from '../../models/reporte';

function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

@Component({
  selector: 'app-configuracion-cajas',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './cajas.html',
  styleUrls: ['../configuracion-shared.css', './cajas.css'],
})
export class ConfiguracionCajas implements OnInit {
  private reporteService = inject(ReporteService);

  private readonly hoy = new Date();
  desde = signal(aFechaISO(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), this.hoy.getDate() - 29)));
  hasta = signal(aFechaISO(this.hoy));

  cargando = signal(false);
  error = signal<string | null>(null);
  resumen = signal<ReporteResumen | null>(null);

  /** Vacío = todos los boleteros. */
  filtroBoletero = signal<string>('');

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    if (!this.desde() || !this.hasta() || this.hasta() < this.desde()) {
      this.error.set('El rango de fechas es inválido.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.reporteService.getResumen(this.desde(), this.hasta()).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las cajas:', err);
        this.error.set('No se pudo cargar la información de cajas.');
        this.cargando.set(false);
      },
    });
  }

  /** Para colorear la fila de la caja en la tabla: rojo si faltó plata, verde si sobró o cerró justo. */
  claseDiferencia(caja: CajaResumen): string {
    if (caja.diferencia < 0) return 'diferencia-faltante';
    if (caja.diferencia > 0) return 'diferencia-sobrante';
    return 'diferencia-exacta';
  }

  /** Nombres únicos de boleteros con al menos una caja cerrada en el rango, para el filtro. */
  boleterosDisponibles(r: ReporteResumen): string[] {
    return [...new Set(r.cajas.map((c) => c.usuarioNombre))].sort();
  }

  cajasFiltradas(r: ReporteResumen): CajaResumen[] {
    const filtro = this.filtroBoletero();
    return filtro ? r.cajas.filter((c) => c.usuarioNombre === filtro) : r.cajas;
  }

  /** Los KPI de retiros/faltantes/sobrantes se recalculan sobre el subconjunto filtrado, no sobre el total del backend. */
  totalesCajasFiltradas(r: ReporteResumen): { retiros: number; faltantes: number; sobrantes: number } {
    let retiros = 0;
    let faltantes = 0;
    let sobrantes = 0;
    for (const c of this.cajasFiltradas(r)) {
      retiros += c.totalRetiros;
      if (c.diferencia < 0) faltantes += Math.abs(c.diferencia);
      else sobrantes += c.diferencia;
    }
    return { retiros, faltantes, sobrantes };
  }

  /** Desempeño acumulado por boletero: turnos trabajados, efectivo vendido, retiros y diferencia total. */
  rankingBoleteros(r: ReporteResumen): { nombre: string; turnos: number; efectivoVendido: number; retiros: number; diferencia: number }[] {
    const porBoletero = new Map<string, { turnos: number; efectivoVendido: number; retiros: number; diferencia: number }>();
    for (const c of r.cajas) {
      const acumulado = porBoletero.get(c.usuarioNombre) ?? { turnos: 0, efectivoVendido: 0, retiros: 0, diferencia: 0 };
      acumulado.turnos += 1;
      acumulado.efectivoVendido += c.montoEsperado - c.montoInicial + c.totalRetiros;
      acumulado.retiros += c.totalRetiros;
      acumulado.diferencia += c.diferencia;
      porBoletero.set(c.usuarioNombre, acumulado);
    }
    return [...porBoletero.entries()]
      .map(([nombre, datos]) => ({ nombre, ...datos }))
      .sort((a, b) => b.efectivoVendido - a.efectivoVendido);
  }
}
