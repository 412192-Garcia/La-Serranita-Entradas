import {Component, OnInit, OnDestroy, ChangeDetectorRef, Output, EventEmitter} from '@angular/core';
import { Semana } from './semana/semana';
import { DiaCalendario } from './calendario-models';
import { DatePipe } from '@angular/common';
import { DiaAperturaService } from '../Services/dia-apertura.service';
import { Subscription } from 'rxjs';
import {FormsModule} from '@angular/forms';

const MESES_LETRAS: string[] = [
  'ENERO', 'FEBRERO', 'MARZO', 'ABRIL', 'MAYO', 'JUNIO',
  'JULIO', 'AGOSTO', 'SEPTIEMBRE', 'OCTUBRE', 'NOVIEMBRE', 'DICIEMBRE'
];

@Component({
  selector: 'app-calendario',
  imports: [Semana, DatePipe, FormsModule],
  templateUrl: './calendario.html',
  styleUrl: './calendario.css',
})
export class Calendario implements OnInit, OnDestroy {

  cargando: boolean = false;

  esRegalo: boolean = false;

  nombreMes: string = MESES_LETRAS[new Date().getMonth()];
  anioActual: number = new Date().getFullYear();
  nombreDias: string[] = ['LUN', 'MAR', 'MIE', 'JUE', 'VIE', 'SAB', 'DOM'];

  semanas: DiaCalendario[][] = [];
  fechaSeleccionada: Date | null = null;
  esMesMinimo: boolean = true;
  esMesMaximo: boolean = false;

  @Output() fechaSeleccionadaChange = new EventEmitter<Date | null>();
  @Output() esRegaloChange = new EventEmitter<boolean>();

  private fechaBase: Date = new Date();
  private subscripcionApertura: Subscription | null = null;
  /** Mes/año de la fecha abierta más lejana ya cargada; null si no hay ninguna (no limita el avance). */
  private ultimaFechaAbierta: Date | null = null;

  constructor(
    private diaService: DiaAperturaService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // cargarDatosMes() ya arma el calendario sin esperar esto (evita que un mes se vea
    // "sin límite" un instante antes de que llegue la respuesta).
    this.cargarDatosMes();

    this.diaService.getUltimaFechaAbierta().subscribe({
      next: (fechaStr) => {
        this.ultimaFechaAbierta = fechaStr ? new Date(fechaStr + 'T12:00:00') : null;
        this.actualizarLimiteMaximo();
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  private actualizarLimiteMaximo(): void {
    const anioDestino = this.fechaBase.getFullYear();
    const mesDestino = this.fechaBase.getMonth();
    this.esMesMaximo = this.ultimaFechaAbierta
      ? (anioDestino > this.ultimaFechaAbierta.getFullYear() ||
         (anioDestino === this.ultimaFechaAbierta.getFullYear() && mesDestino >= this.ultimaFechaAbierta.getMonth()))
      : false;
  }

  ngOnDestroy(): void {
    if (this.subscripcionApertura) this.subscripcionApertura.unsubscribe();
  }

  cargarDatosMes(): void {
    const anioDestino = this.fechaBase.getFullYear();
    const mesDestino = this.fechaBase.getMonth();
    const hoy = new Date();

    this.cargando = true;

    this.esMesMinimo = (anioDestino === hoy.getFullYear() && mesDestino === hoy.getMonth());
    this.actualizarLimiteMaximo();
    this.semanas = [];

    if (this.subscripcionApertura) this.subscripcionApertura.unsubscribe();

    this.subscripcionApertura = this.diaService.getDiasApertura(mesDestino, anioDestino).subscribe({
      next: (fechasAbiertas: string[]) => {
        this.nombreMes = MESES_LETRAS[mesDestino];
        this.anioActual = anioDestino;

        const setFechasAbiertas = new Set(fechasAbiertas);
        this.generarCalendario(anioDestino, mesDestino, setFechasAbiertas);

        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error del backend:', err);
        this.generarCalendario(anioDestino, mesDestino, new Set());

        this.cargando = false;
        this.cdr.detectChanges();
      }
    });
  }

  cambiarMes(desplazamiento: number): void {
    this.fechaBase.setDate(1);
    this.fechaBase.setMonth(this.fechaBase.getMonth() + desplazamiento);
    this.cargarDatosMes();
  }

  generarCalendario(anio: number, mes: number, setFechasAbiertas: Set<string>): void {
    const hoy = new Date();
    const hoyTiempo = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate()).getTime();

    const mesString = String(mes + 1).padStart(2, '0');
    const hoyFormatoString = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;

    const primerDiaMes = new Date(anio, mes, 1);
    const ultimoDiaMes = new Date(anio, mes + 1, 0);
    const totalDias = ultimoDiaMes.getDate();

    let diaSemanaInicio = primerDiaMes.getDay();
    diaSemanaInicio = diaSemanaInicio === 0 ? 6 : diaSemanaInicio - 1;

    let listaDiasPura: DiaCalendario[] = [];

    for (let i = 0; i < diaSemanaInicio; i++) {
      listaDiasPura.push({ numero: null, fecha: new Date(), esHoy: false, esPasado: false, abierto: false, seleccionado: false });
    }

    for (let nro = 1; nro <= totalDias; nro++) {
      const fechaDia = new Date(anio, mes, nro, 12, 0, 0);

      const diaString = String(nro).padStart(2, '0');
      const fechaFormatoString = `${anio}-${mesString}-${diaString}`;

      const esHoy = fechaFormatoString === hoyFormatoString;

      const fechaDiaTiempo = new Date(anio, mes, nro).getTime();
      const esPasado = fechaDiaTiempo < hoyTiempo && !esHoy;

      listaDiasPura.push({
        numero: nro,
        fecha: fechaDia,
        esHoy: esHoy,
        esPasado: esPasado,
        abierto: setFechasAbiertas.has(fechaFormatoString),
        seleccionado: this.fechaSeleccionada ? fechaDia.getTime() === this.fechaSeleccionada.getTime() : false
      });
    }

    while (listaDiasPura.length % 7 !== 0) {
      listaDiasPura.push({ numero: null, fecha: new Date(), esHoy: false, esPasado: false, abierto: false, seleccionado: false });
    }

    const matrizFinal: DiaCalendario[][] = [];
    for (let i = 0; i < listaDiasPura.length; i += 7) {
      matrizFinal.push(listaDiasPura.slice(i, i + 7));
    }

    this.semanas = matrizFinal;
    this.cdr.detectChanges();
  }

  onDiaSeleccionado(fecha: Date): void {
    this.fechaSeleccionada = fecha;
    this.semanas = this.semanas.map(semana =>
      semana.map(dia => ({
        ...dia,
        seleccionado: dia.numero ? dia.fecha.getTime() === fecha.getTime() : false
      }))
    );
    this.fechaSeleccionadaChange.emit(this.fechaSeleccionada);
    this.cdr.detectChanges();
  }

  onCambioRegalo(valor: boolean): void {
    this.esRegalo = valor;
    if (this.esRegalo) {
      this.fechaSeleccionada = null;
      this.fechaSeleccionadaChange.emit(null);
    }
    this.esRegaloChange.emit(this.esRegalo);
  }
}
