import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReporteService } from '../../Services/reporte.service';
import { CajaResumen, ReporteResumen } from '../../models/reporte';
import { CajaService, Caja, CajaAbierta } from '../../Services/caja.service';
import { CabeceraInterna } from '../../shared/cabecera-interna/cabecera-interna';
import { CierreCajaModal } from './cierre-caja-modal/cierre-caja-modal';
import { ResumenCierre } from './resumen-cierre/resumen-cierre';

function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

@Component({
  selector: 'app-configuracion-cajas',
  imports: [FormsModule, CurrencyPipe, DatePipe, CabeceraInterna, CierreCajaModal, ResumenCierre],
  templateUrl: './cajas.html',
  styleUrls: ['../configuracion-shared.css', './cajas.css'],
})
export class ConfiguracionCajas implements OnInit {
  private reporteService = inject(ReporteService);
  private cajaService = inject(CajaService);

  // ---------- Cajas abiertas ahora mismo ----------
  cajasAbiertas = signal<CajaAbierta[]>([]);
  cargandoCajasAbiertas = signal(false);

  // ---------- Cerrar caja / corregir un cierre ya hecho ----------
  /** Controla sólo la visibilidad del modal (ver [class.oculto]): nunca se destruye, así "Cancelar" no borra lo ya cargado. */
  mostrarCierre = signal(false);
  /** Id de la caja abierta que se está por cerrar. Sólo cambia al apuntar a una caja distinta (no al cancelar). */
  cajaIdCierre = signal<number | null>(null);
  /** Detalle de esa misma caja (vía obtenerDetalle): esperado sigue oculto porque sigue abierta, sólo trae huboVentaDolares/retiros. */
  cajaCierreDetalle = signal<Caja | null>(null);
  /** Si está seteada, el modal corrige esa caja ya cerrada en vez de cerrar una abierta. Sólo cambia al apuntar a una caja distinta. */
  cajaParaCorregir = signal<Caja | null>(null);
  /** Resultado a mostrar justo después de cerrar una caja (no una corrección): el admin necesita ver que el cierre se guardó y con qué números, no volver directo a la lista sin feedback. */
  cajaRecienCerrada = signal<Caja | null>(null);

  /** Id de la fila desplegada (null = ninguna); el detalle de sólo lectura de esa caja se carga debajo. */
  filaExpandidaId = signal<number | null>(null);
  cajaDetalle = signal<Caja | null>(null);
  cargandoDetalle = signal(false);
  errorDetalle = signal<string | null>(null);

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
    this.cargarCajasAbiertas();
  }

  cargarCajasAbiertas(): void {
    this.cargandoCajasAbiertas.set(true);
    this.cajaService.obtenerCajasAbiertas().subscribe({
      next: (cs) => {
        this.cajasAbiertas.set(cs);
        this.cargandoCajasAbiertas.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las cajas abiertas:', err);
        this.cargandoCajasAbiertas.set(false);
      },
    });
  }

  /** Abre el modal para cerrar esa caja abierta (busca su detalle, sin exponer lo esperado: sigue abierta). */
  iniciarCierre(cajaId: number): void {
    this.cajaParaCorregir.set(null);
    this.cajaIdCierre.set(cajaId);
    this.mostrarCierre.set(true);
    this.cajaService.obtenerDetalle(cajaId).subscribe({
      next: (c) => this.cajaCierreDetalle.set(c),
      error: (err) => console.error('Error al cargar el detalle de la caja a cerrar:', err),
    });
  }

  /** Abre el modal para corregir un cierre ya hecho (precargado con lo que ya tiene esa caja). */
  iniciarCorreccion(caja: Caja): void {
    this.cajaIdCierre.set(null);
    this.cajaCierreDetalle.set(null);
    this.cajaParaCorregir.set(caja);
    this.mostrarCierre.set(true);
  }

  /** Se cerró o corrigió con éxito: refresca las listas afectadas. */
  onCajaCerrada(c: Caja): void {
    const eraCorreccion = this.cajaParaCorregir() !== null;
    this.mostrarCierre.set(false);
    this.cajaIdCierre.set(null);
    this.cajaCierreDetalle.set(null);
    this.cajaParaCorregir.set(null);
    this.cargarCajasAbiertas();
    this.cargar();
    if (eraCorreccion) {
      if (this.filaExpandidaId() === c.id) {
        this.cajaDetalle.set(c);
      }
    } else {
      // Recién se cerró (no una corrección): mostrar el resultado, si no el modal
      // simplemente desaparece y el admin no tiene forma de saber que se guardó.
      this.cajaRecienCerrada.set(c);
    }
  }

  /** Se agregó un retiro/aporte desde dentro del modal de cierre, sin llegar a cerrar: refresca sólo esa caja, sin tocar el resto del formulario. */
  onCajaActualizadaCierre(c: Caja): void {
    if (this.cajaParaCorregir()) {
      this.cajaParaCorregir.set(c);
    } else {
      this.cajaCierreDetalle.set(c);
    }
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

  /** Despliega el detalle de esa fila, o lo cierra si ya estaba abierta. */
  toggleDetalle(cajaId: number): void {
    if (this.filaExpandidaId() === cajaId) {
      this.filaExpandidaId.set(null);
      this.cajaDetalle.set(null);
      this.errorDetalle.set(null);
      return;
    }
    this.filaExpandidaId.set(cajaId);
    this.cargandoDetalle.set(true);
    this.errorDetalle.set(null);
    this.cajaDetalle.set(null);
    this.cajaService.obtenerDetalle(cajaId).subscribe({
      next: (c) => {
        this.cajaDetalle.set(c);
        this.cargandoDetalle.set(false);
      },
      error: (err) => {
        console.error('Error al cargar el detalle de la caja:', err);
        this.errorDetalle.set('No se pudo cargar el detalle de esta caja.');
        this.cargandoDetalle.set(false);
      },
    });
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
