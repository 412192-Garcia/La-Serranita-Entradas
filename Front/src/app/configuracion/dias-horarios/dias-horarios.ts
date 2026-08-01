import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ConfiguracionService, DiaApertura } from '../../Services/configuracion.service';

const MESES: string[] = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

interface CeldaMes {
  numero: number | null;
  fecha: string;
  dia: DiaApertura | null;
}

@Component({
  selector: 'app-configuracion-dias-horarios',
  imports: [FormsModule, DatePipe],
  templateUrl: './dias-horarios.html',
  styleUrls: ['../configuracion-shared.css', './dias-horarios.css'],
})
export class ConfiguracionDiasHorarios implements OnInit {
  private configuracionService = inject(ConfiguracionService);

  private mesBase = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  nombreMes = signal(MESES[this.mesBase.getMonth()]);
  anioMes = signal(this.mesBase.getFullYear());
  celdas = signal<CeldaMes[]>([]);
  cargandoDias = signal(false);

  horaAperturaGeneral = signal('09:00');
  horaCierreGeneral = signal('18:00');
  guardandoHorarioGeneral = signal(false);
  mensajeHorarioGeneral = signal<string | null>(null);

  /** Días tildados en el calendario: 1 = detalle individual, 2+ = panel de grupo. Click simple reemplaza, Ctrl/Cmd+click suma o saca, Shift+click selecciona el rango desde el ancla. */
  diasSeleccionados = signal<Set<string>>(new Set());
  /** Último día tocado con click simple o Ctrl+click: punto de partida para el rango de Shift+click. */
  private anclaSeleccion = signal<string | null>(null);
  horaAperturaEspecial = signal('');
  horaCierreEspecial = signal('');
  guardandoDia = signal(false);
  errorDias = signal<string | null>(null);

  horaAperturaGrupo = signal('');
  horaCierreGrupo = signal('');
  guardandoHorarioGrupo = signal(false);
  aplicandoBulk = signal(false);

  diaUnicoSeleccionado = computed<DiaApertura | null>(() => {
    const set = this.diasSeleccionados();
    if (set.size !== 1) return null;
    const [fecha] = set;
    return this.celdas().find((c) => c.fecha === fecha)?.dia ?? null;
  });

  constructor() {
    effect(() => {
      const dia = this.diaUnicoSeleccionado();
      this.horaAperturaEspecial.set(dia?.horaApertura?.slice(0, 5) ?? '');
      this.horaCierreEspecial.set(dia?.horaCierre?.slice(0, 5) ?? '');
    });
  }

  ngOnInit(): void {
    this.cargarHorarioGeneral();
    this.cargarMes();
  }

  cargarHorarioGeneral(): void {
    this.configuracionService.getHorarioGeneral().subscribe({
      next: (h) => {
        this.horaAperturaGeneral.set(h.horaApertura.slice(0, 5));
        this.horaCierreGeneral.set(h.horaCierre.slice(0, 5));
      },
      error: (err) => console.error('Error al cargar el horario general:', err),
    });
  }

  guardarHorarioGeneral(): void {
    if (this.guardandoHorarioGeneral()) return;
    this.guardandoHorarioGeneral.set(true);
    this.mensajeHorarioGeneral.set(null);

    this.configuracionService
      .actualizarHorarioGeneral(this.horaAperturaGeneral(), this.horaCierreGeneral())
      .subscribe({
        next: () => {
          this.mensajeHorarioGeneral.set('Horario general actualizado.');
          this.guardandoHorarioGeneral.set(false);
        },
        error: (err) => {
          console.error('Error al guardar el horario general:', err);
          this.mensajeHorarioGeneral.set('No se pudo guardar el horario general.');
          this.guardandoHorarioGeneral.set(false);
        },
      });
  }

  cargarMes(): void {
    this.cargandoDias.set(true);
    this.errorDias.set(null);
    const anio = this.anioMes();
    const mes = MESES.indexOf(this.nombreMes()) + 1;

    this.configuracionService.getMes(anio, mes).subscribe({
      next: (dias) => {
        this.celdas.set(this.armarCeldas(anio, mes, dias));
        this.cargandoDias.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los días del mes:', err);
        this.errorDias.set('No se pudieron cargar los días del mes.');
        this.cargandoDias.set(false);
      },
    });
  }

  private armarCeldas(anio: number, mes: number, dias: DiaApertura[]): CeldaMes[] {
    // El backend devuelve el mes completo y en orden, así que alcanza con recorrerlo:
    // sólo hay que anteponer las celdas vacías hasta el día de la semana en que arranca
    // (la semana empieza en lunes, por eso el domingo pasa de 0 a 6).
    const primerDia = new Date(anio, mes - 1, 1);
    const inicioSemana = primerDia.getDay() === 0 ? 6 : primerDia.getDay() - 1;

    const celdas: CeldaMes[] = [];
    for (let i = 0; i < inicioSemana; i++) {
      celdas.push({ numero: null, fecha: '', dia: null });
    }
    for (const d of dias) {
      celdas.push({ numero: Number(d.fecha.slice(-2)), fecha: d.fecha, dia: d });
    }
    return celdas;
  }

  cambiarMes(delta: number): void {
    this.mesBase.setDate(1);
    this.mesBase.setMonth(this.mesBase.getMonth() + delta);
    this.nombreMes.set(MESES[this.mesBase.getMonth()]);
    this.anioMes.set(this.mesBase.getFullYear());
    this.diasSeleccionados.set(new Set());
    this.anclaSeleccion.set(null);
    this.cargarMes();
  }

  toggleAbierto(dia: DiaApertura, event: Event): void {
    event.stopPropagation();
    this.configuracionService.setAbierto(dia.fecha, !dia.abierto).subscribe({
      next: (actualizado) => this.reemplazarDia(actualizado),
      error: (err) => {
        console.error('Error al cambiar el estado del día:', err);
        this.errorDias.set('No se pudo actualizar el día. Reintentá.');
      },
    });
  }

  /**
   * Click simple reemplaza la selección y marca el ancla; Ctrl/Cmd+click suma o saca ese
   * día sin perder el resto (y también mueve el ancla); Shift+click selecciona todo el
   * rango entre el ancla y el día clickeado, dentro del mes visible.
   */
  onClickCelda(c: CeldaMes, event: MouseEvent): void {
    if (!c.dia) return;

    if (event.shiftKey && this.anclaSeleccion()) {
      const fechasDelMes = this.celdas().filter((x) => x.dia).map((x) => x.fecha);
      const iAncla = fechasDelMes.indexOf(this.anclaSeleccion()!);
      const iActual = fechasDelMes.indexOf(c.fecha);
      if (iAncla !== -1 && iActual !== -1) {
        const [inicio, fin] = iAncla <= iActual ? [iAncla, iActual] : [iActual, iAncla];
        this.diasSeleccionados.set(new Set(fechasDelMes.slice(inicio, fin + 1)));
      }
      return;
    }

    const esMulti = event.ctrlKey || event.metaKey;
    this.anclaSeleccion.set(c.fecha);

    this.diasSeleccionados.update((actual) => {
      if (esMulti) {
        const copia = new Set(actual);
        if (copia.has(c.fecha)) {
          copia.delete(c.fecha);
        } else {
          copia.add(c.fecha);
        }
        return copia;
      }
      if (actual.size === 1 && actual.has(c.fecha)) return actual;
      return new Set([c.fecha]);
    });
  }

  limpiarSeleccion(): void {
    this.diasSeleccionados.set(new Set());
    this.anclaSeleccion.set(null);
  }

  aplicarEstadoBulk(abierto: boolean): void {
    const fechas = Array.from(this.diasSeleccionados());
    if (fechas.length === 0 || this.aplicandoBulk()) return;

    this.aplicandoBulk.set(true);
    this.errorDias.set(null);

    forkJoin(fechas.map((f) => this.configuracionService.setAbierto(f, abierto))).subscribe({
      next: (actualizados) => {
        actualizados.forEach((d) => this.reemplazarDia(d));
        this.aplicandoBulk.set(false);
      },
      error: (err) => {
        console.error('Error al aplicar el cambio a los días seleccionados:', err);
        this.errorDias.set('No se pudo actualizar alguno de los días seleccionados. Reintentá.');
        this.aplicandoBulk.set(false);
      },
    });
  }

  guardarHorarioEspecial(): void {
    const dia = this.diaUnicoSeleccionado();
    if (!dia || this.guardandoDia()) return;
    this.guardandoDia.set(true);

    this.configuracionService
      .setHorarioEspecial(dia.fecha, this.horaAperturaEspecial() || null, this.horaCierreEspecial() || null)
      .subscribe({
        next: (actualizado) => {
          this.reemplazarDia(actualizado);
          this.guardandoDia.set(false);
        },
        error: (err) => {
          console.error('Error al guardar el horario especial:', err);
          this.errorDias.set('No se pudo guardar el horario especial.');
          this.guardandoDia.set(false);
        },
      });
  }

  quitarHorarioEspecial(): void {
    const dia = this.diaUnicoSeleccionado();
    if (!dia || this.guardandoDia()) return;
    this.guardandoDia.set(true);

    this.configuracionService.setHorarioEspecial(dia.fecha, null, null).subscribe({
      next: (actualizado) => {
        this.reemplazarDia(actualizado);
        this.guardandoDia.set(false);
      },
      error: (err) => {
        console.error('Error al quitar el horario especial:', err);
        this.errorDias.set('No se pudo quitar el horario especial.');
        this.guardandoDia.set(false);
      },
    });
  }

  guardarHorarioGrupo(): void {
    const fechas = Array.from(this.diasSeleccionados());
    if (fechas.length < 2 || this.guardandoHorarioGrupo()) return;
    this.guardandoHorarioGrupo.set(true);
    this.errorDias.set(null);

    const apertura = this.horaAperturaGrupo() || null;
    const cierre = this.horaCierreGrupo() || null;

    forkJoin(fechas.map((f) => this.configuracionService.setHorarioEspecial(f, apertura, cierre))).subscribe({
      next: (actualizados) => {
        actualizados.forEach((d) => this.reemplazarDia(d));
        this.guardandoHorarioGrupo.set(false);
      },
      error: (err) => {
        console.error('Error al guardar el horario del grupo:', err);
        this.errorDias.set('No se pudo guardar el horario para alguno de los días seleccionados.');
        this.guardandoHorarioGrupo.set(false);
      },
    });
  }

  quitarHorarioGrupo(): void {
    const fechas = Array.from(this.diasSeleccionados());
    if (fechas.length < 2 || this.guardandoHorarioGrupo()) return;
    this.guardandoHorarioGrupo.set(true);
    this.errorDias.set(null);

    forkJoin(fechas.map((f) => this.configuracionService.setHorarioEspecial(f, null, null))).subscribe({
      next: (actualizados) => {
        actualizados.forEach((d) => this.reemplazarDia(d));
        this.horaAperturaGrupo.set('');
        this.horaCierreGrupo.set('');
        this.guardandoHorarioGrupo.set(false);
      },
      error: (err) => {
        console.error('Error al quitar el horario del grupo:', err);
        this.errorDias.set('No se pudo quitar el horario para alguno de los días seleccionados.');
        this.guardandoHorarioGrupo.set(false);
      },
    });
  }

  private reemplazarDia(actualizado: DiaApertura): void {
    this.celdas.update((cs) =>
      cs.map((c) => (c.fecha === actualizado.fecha ? { ...c, dia: actualizado } : c))
    );
  }
}
