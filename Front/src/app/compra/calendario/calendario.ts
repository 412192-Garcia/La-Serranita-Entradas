import {Component, Input, OnInit, OnDestroy, ChangeDetectorRef, Output, EventEmitter, ViewChild, ElementRef} from '@angular/core';
import { Semana } from './semana/semana';
import { DiaCalendario } from './calendario-models';
import { DatePipe } from '@angular/common';
import { DiaAperturaService } from '../../services/dia-apertura.service';
import { Subscription, catchError, forkJoin, of } from 'rxjs';
import {FormsModule} from '@angular/forms';
import {Spinner} from '../../shared/spinner/spinner';

const MESES_LETRAS: string[] = [
  'ENERO', 'FEBRERO', 'MARZO', 'ABRIL', 'MAYO', 'JUNIO',
  'JULIO', 'AGOSTO', 'SEPTIEMBRE', 'OCTUBRE', 'NOVIEMBRE', 'DICIEMBRE'
];

@Component({
  selector: 'app-calendario',
  imports: [Semana, DatePipe, FormsModule, Spinner],
  templateUrl: './calendario.html',
  styleUrl: './calendario.css',
})
export class Calendario implements OnInit, OnDestroy {

  cargando: boolean = false;

  esRegalo: boolean = false;

  /** El toggle "Comprar como Regalo" del flujo online (quien compra ≠ quien entra). */
  @Input() mostrarOpcionRegalo: boolean = true;

  /** El toggle "Entrada sin fecha" del generador de reservas del admin: misma mecánica (fecha
   * null), pero a nombre del propio titular y con otra redacción — no es un regalo. */
  @Input() mostrarOpcionSinFecha: boolean = false;

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
  /** El día elegido antes de marcar "regalo", para devolverlo si lo desmarca. */
  private fechaAntesDelRegalo: Date | null = null;
  private subscripcionApertura: Subscription | null = null;
  /** Mes/año de la fecha abierta más lejana ya cargada; null si no hay ninguna (no limita el avance). */
  private ultimaFechaAbierta: Date | null = null;

  /** Envuelve la grilla de semanas (ver calendario.html): algunos meses tienen 5 filas y otros
   * 6, así que cambiar de mes puede cambiar la altura de esto — animarCambioDeAltura() lo
   * suaviza en vez de dejar que salte de golpe. */
  @ViewChild('semanasWrapper') private semanasWrapperRef?: ElementRef<HTMLElement>;

  /** Corre "actualizar" (que cambia this.semanas) y anima la transición si el alto de la
   * grilla cambió: fija el alto viejo, fuerza un reflow, y en el frame siguiente pasa al alto
   * nuevo con una transición CSS — al terminar, limpia el alto inline para que vuelva a
   * seguir el contenido normalmente (ej. si el ancho del embed cambia después). */
  private animarCambioDeAltura(actualizar: () => void): void {
    const el = this.semanasWrapperRef?.nativeElement;
    if (!el) {
      actualizar();
      return;
    }

    const alturaAnterior = el.getBoundingClientRect().height;
    actualizar();
    const alturaNueva = el.getBoundingClientRect().height;

    if (Math.abs(alturaNueva - alturaAnterior) < 1) return;

    el.style.transition = 'none';
    el.style.height = `${alturaAnterior}px`;
    el.getBoundingClientRect(); // fuerza el reflow con el alto viejo antes de animar

    const limpiar = () => {
      el.style.transition = '';
      el.style.height = '';
      el.removeEventListener('transitionend', limpiar);
    };
    el.addEventListener('transitionend', limpiar);

    requestAnimationFrame(() => {
      el.style.transition = 'height 0.25s ease';
      el.style.height = `${alturaNueva}px`;
    });
  }

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
    // No se vacía "semanas" acá: al cambiar de mes se queda viendo la grilla del mes anterior
    // (atenuada, ver overlay-carga en el html) hasta que llega la respuesta nueva, en vez de
    // colapsar a un spinner de otro alto y volver a expandirse de golpe cuando se reemplaza.

    if (this.subscripcionApertura) this.subscripcionApertura.unsubscribe();

    this.subscripcionApertura = forkJoin({
      fechasAbiertas: this.diaService.getDiasApertura(mesDestino, anioDestino),
      // Si esta consulta falla no se rompe el calendario: hoy simplemente se ofrece como siempre y el
      // servidor rechaza la compra si ya pasó el límite.
      compraDeHoyCerrada: this.diaService.getCompraDeHoyCerrada().pipe(catchError(() => of(false))),
    }).subscribe({
      next: ({ fechasAbiertas, compraDeHoyCerrada }) => {
        this.nombreMes = MESES_LETRAS[mesDestino];
        this.anioActual = anioDestino;

        const setFechasAbiertas = new Set(fechasAbiertas);
        this.animarCambioDeAltura(() => this.generarCalendario(anioDestino, mesDestino, setFechasAbiertas, compraDeHoyCerrada));

        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error del backend:', err);
        this.animarCambioDeAltura(() => this.generarCalendario(anioDestino, mesDestino, new Set()));

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

  generarCalendario(anio: number, mes: number, setFechasAbiertas: Set<string>, compraDeHoyCerrada = false): void {
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
        compraCerrada: esHoy && compraDeHoyCerrada,
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
      this.fechaAntesDelRegalo = this.fechaSeleccionada;
      this.fechaSeleccionada = null;
      this.fechaSeleccionadaChange.emit(null);
    }
    this.esRegaloChange.emit(this.esRegalo);
    // Al desmarcar el regalo vuelve el día que había elegido: si no, las entradas se ocultaban
    // hasta volver a tocar el mismo día en el calendario.
    if (!this.esRegalo && this.fechaAntesDelRegalo) {
      this.onDiaSeleccionado(this.fechaAntesDelRegalo);
      this.fechaAntesDelRegalo = null;
    }
  }
}
