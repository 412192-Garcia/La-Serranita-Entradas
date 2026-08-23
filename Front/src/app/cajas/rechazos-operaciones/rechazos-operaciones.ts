import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RechazoService, OperacionRechazada, TipoOperacionRechazada } from '../../services/rechazo.service';
import { BoleteriaService } from '../../services/boleteria.service';
import { Spinner } from '../../shared/spinner/spinner';

type Filtro = 'pendientes' | 'resueltas' | 'todas';

const ETIQUETAS_TIPO: Record<TipoOperacionRechazada, string> = {
  VENTA: 'Venta',
  RETIRO_APORTE: 'Retiro/Aporte',
  INGRESO_ENTRADAS: 'Entradas',
  COMPROBANTE_EMAIL: 'Email de comprobante',
};

/** Traducciones de las claves más comunes del payload crudo, para no mostrarle al admin
 * "idempotencyKey" o "fechaOriginal" — ruido interno que no aporta nada para entender qué pasó. */
const ETIQUETAS_CAMPO: Record<string, string> = {
  cantidad: 'Cantidad',
  tipo: 'Tipo',
  motivo: 'Motivo',
  monto: 'Monto',
  formaPago: 'Forma de pago',
  items: 'Ítems',
  pagaCon: 'Paga con',
  compraId: 'Compra ID',
  codigoReserva: 'Reserva',
  email: 'Email',
  tipoEmail: 'Email de',
  detalleTecnico: 'Detalle técnico',
};

const CAMPOS_OCULTOS = new Set(['idempotencyKey', 'fechaOriginal']);

/** VENTA/RETIRO_APORTE/INGRESO_ENTRADAS rechazados porque la caja ya no estaba abierta traen
 * siempre este mismo motivo (los tres llaman a getAbiertaOrThrow) — sirve para distinguir esa
 * causa puntual de las demás que puede tener una VENTA, donde SÍ hay un botón (ver
 * reabrirYReintentar) y no tiene sentido duplicar con un texto de sugerencia. */
const PATRON_CAJA_CERRADA = /caja abierta/i;

/** El resto de las causas de VENTA no tienen ninguna acción segura de un click: la venta nunca
 * se guardó y el pago (el efectivo en mano, la tarjeta pasada) ya ocurrió o no en el mundo real
 * — reintentarla a ciegas contra datos desactualizados arriesga vender con un precio/cupo/promo
 * que ya cambió.
 *
 * Sólo cubre causas que realmente pueden pasar con el POS real: el carrito vacío, la forma de
 * pago faltante, el pase obligatorio, un dato de artículo inválido o el pago en dólares
 * incompleto NO están acá porque el botón "Cobrar" ya los bloquea del lado del cliente — no hay
 * forma de mandar una venta así desde la app. Lo que sí puede pasar es que el ESTADO DEL SERVIDOR
 * cambie durante el rato que la venta esperó sin conexión antes de reintentarse sola: alguien más
 * vendió el cupo que quedaba, un admin borró un tipo de entrada/artículo, o desactivó una promo.
 * Primer patrón que matchea gana. */
const SUGERENCIAS_VENTA: { patron: RegExp; sugerencia: string }[] = [
  { patron: /cupo diario/i, sugerencia: 'Se llenó el cupo de esa fecha: avisale al boletero, no se puede vender más para ese día.' },
  { patron: /TipoEntrada no encontrada/i, sugerencia: 'Ese tipo de entrada ya no existe: avisale al boletero para armar la venta con los tipos actuales.' },
  { patron: /[Pp]romoción.*(no encontrada|ya no está activa)/, sugerencia: 'La promo usada ya no está activa: avisale al boletero para vender sin ella o con una vigente.' },
  { patron: /[Aa]rtículo no encontrado/, sugerencia: 'Un artículo del catálogo usado en la venta ya no existe: avisale al boletero para sacarlo del carrito.' },
];

const SUGERENCIA_VENTA_GENERICA = 'Avisale al boletero para que revise el motivo y reintente la venta.';
const SUGERENCIA_SIN_CAJA_GUARDADA =
  'Este rechazo es de antes de que se guardara la caja de origen: no se puede reabrir solo, hay que cargar la operación a mano desde acá cuando el boletero abra su próxima caja.';

@Component({
  selector: 'app-rechazos-operaciones',
  imports: [FormsModule, DatePipe, Spinner],
  templateUrl: './rechazos-operaciones.html',
  // configuracion-shared.css no es global (cada página que la usa la suma a su propio styleUrls,
  // ver cajas.ts): sin esto, .tarjeta/.ayuda/.chip-filtro/.btn-primario/.alerta-error de acá
  // quedarían sin estilo, porque el encapsulamiento de Angular no deja que el CSS de cajas.ts
  // le llegue a los elementos que renderiza este componente hijo.
  styleUrls: ['../../configuracion/configuracion-shared.css', './rechazos-operaciones.css'],
})
export class RechazosOperaciones implements OnInit {
  private rechazoService = inject(RechazoService);
  private boleteriaService = inject(BoleteriaService);

  cargando = signal(false);
  error = signal<string | null>(null);
  rechazos = signal<OperacionRechazada[]>([]);
  filtro = signal<Filtro>('pendientes');

  /** Id del rechazo cuyo formulario de "marcar resuelto" está abierto (null = ninguno). */
  resolviendoId = signal<number | null>(null);
  notaResolucion = signal('');
  guardandoResolucion = signal(false);

  /** Id del rechazo de tipo COMPROBANTE_EMAIL cuyo mail se está reenviando ahora mismo. */
  reenviandoId = signal<number | null>(null);
  errorReenvio = signal<Record<number, string>>({});

  /** Id del rechazo (RETIRO_APORTE/INGRESO_ENTRADAS) cuya caja se está reabriendo ahora mismo. */
  reabriendoId = signal<number | null>(null);
  errorReabrir = signal<Record<number, string>>({});

  ngOnInit(): void {
    this.cargar();
  }

  cambiarFiltro(f: Filtro): void {
    this.filtro.set(f);
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    const resuelto = this.filtro() === 'todas' ? undefined : this.filtro() === 'resueltas';
    this.rechazoService.listar(resuelto).subscribe({
      next: (rs) => {
        this.rechazos.set(rs);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las operaciones rechazadas:', err);
        this.error.set('No se pudieron cargar las operaciones rechazadas.');
        this.cargando.set(false);
      },
    });
  }

  etiquetaTipo(tipo: TipoOperacionRechazada): string {
    return ETIQUETAS_TIPO[tipo] ?? tipo;
  }

  /** Parsea el payload JSON crudo a pares clave/valor legibles, ocultando ruido interno. */
  camposPayload(r: OperacionRechazada): { etiqueta: string; valor: string }[] {
    let datos: Record<string, unknown>;
    try {
      datos = JSON.parse(r.payload);
    } catch {
      return [];
    }
    return Object.entries(datos)
      .filter(([clave, valor]) => !CAMPOS_OCULTOS.has(clave) && valor !== null && valor !== undefined)
      .map(([clave, valor]) => ({
        etiqueta: ETIQUETAS_CAMPO[clave] ?? clave,
        valor: typeof valor === 'object' ? JSON.stringify(valor) : String(valor),
      }));
  }

  abrirResolucion(id: number): void {
    this.resolviendoId.set(id);
    this.notaResolucion.set('');
  }

  cancelarResolucion(): void {
    this.resolviendoId.set(null);
    this.notaResolucion.set('');
  }

  confirmarResolucion(id: number): void {
    this.guardandoResolucion.set(true);
    this.rechazoService.resolver(id, this.notaResolucion().trim() || undefined).subscribe({
      next: () => {
        this.guardandoResolucion.set(false);
        this.resolviendoId.set(null);
        this.notaResolucion.set('');
        this.cargar();
      },
      error: (err) => {
        console.error('Error al marcar el rechazo como resuelto:', err);
        this.guardandoResolucion.set(false);
      },
    });
  }

  /** Compra ID guardado en el payload de un rechazo COMPROBANTE_EMAIL; null si no se pudo leer. */
  private compraIdDe(r: OperacionRechazada): number | null {
    return this.campoNumericoDe(r, 'compraId');
  }

  private cajaIdDe(r: OperacionRechazada): number | null {
    return this.campoNumericoDe(r, 'cajaId');
  }

  /** true si un rechazo VENTA/RETIRO_APORTE/INGRESO_ENTRADAS trae guardada la caja de origen:
   * los de antes de que existiera este campo no la tienen, y no se pueden reabrir automáticamente. */
  tieneCajaGuardada(r: OperacionRechazada): boolean {
    return this.cajaIdDe(r) != null;
  }

  esReabribleAutomaticamente(r: OperacionRechazada): boolean {
    return r.tipoOperacion === 'RETIRO_APORTE' || r.tipoOperacion === 'INGRESO_ENTRADAS' || r.tipoOperacion === 'VENTA';
  }

  private campoNumericoDe(r: OperacionRechazada, clave: string): number | null {
    try {
      const datos = JSON.parse(r.payload) as Record<string, unknown>;
      const valor = datos[clave];
      return typeof valor === 'number' ? valor : null;
    } catch {
      return null;
    }
  }

  /** Otros rechazos VENTA/RETIRO_APORTE/INGRESO_ENTRADAS pendientes de la MISMA caja: si se
   * encolaron varias operaciones sin conexión y la caja se cerró antes de reintentarlas, cada
   * una quedó como un rechazo separado — conviene resolverlas todas juntas en un solo
   * reabrir/cerrar en vez de una reapertura por cada una. Incluye a "r" mismo. */
  loteDeLaMismaCaja(r: OperacionRechazada): OperacionRechazada[] {
    const cajaId = this.cajaIdDe(r);
    if (cajaId == null) return [r];
    return this.rechazos().filter(
      (x) => !x.resuelto && this.esReabribleAutomaticamente(x) && this.cajaIdDe(x) === cajaId
    );
  }

  /** Para los rechazos sin acción de un click, una pista concreta de qué decirle al boletero —
   * para que "Marcar resuelto" no sea la única opción sin ninguna guía de qué hacer. Si la causa
   * es "caja cerrada" en un tipo reabrible con la caja guardada, no hace falta: ya hay un botón
   * que lo resuelve solo, un texto encima sería ruido. */
  sugerenciaAccion(r: OperacionRechazada): string | null {
    if (this.esReabribleAutomaticamente(r) && PATRON_CAJA_CERRADA.test(r.motivo)) {
      return this.tieneCajaGuardada(r) ? null : SUGERENCIA_SIN_CAJA_GUARDADA;
    }
    if (r.tipoOperacion === 'VENTA') {
      const match = SUGERENCIAS_VENTA.find((s) => s.patron.test(r.motivo));
      return match ? match.sugerencia : SUGERENCIA_VENTA_GENERICA;
    }
    return null;
  }

  /** Acción directa para el caso más común (falló el envío del comprobante): reenviarlo desde
   * acá mismo, sin tener que ir a buscar la compra en Boletería. Si el reenvío funciona, el
   * rechazo se marca resuelto solo — reenviar YA ES la resolución de este caso puntual. */
  reenviarMail(r: OperacionRechazada): void {
    const compraId = this.compraIdDe(r);
    if (compraId == null) return;

    this.reenviandoId.set(r.id);
    this.errorReenvio.update((errores) => {
      const { [r.id]: _omitido, ...resto } = errores;
      return resto;
    });

    this.boleteriaService.reenviarMail(compraId).subscribe({
      next: () => {
        this.reenviandoId.set(null);
        this.rechazoService.resolver(r.id, 'Reenviado desde Cajas').subscribe({
          next: () => this.cargar(),
          error: (err) => console.error('Se reenvió el mail pero no se pudo marcar el rechazo como resuelto:', err),
        });
      },
      error: (err) => {
        console.error('Error al reenviar el mail:', err);
        this.reenviandoId.set(null);
        this.errorReenvio.update((errores) => ({
          ...errores,
          [r.id]: typeof err?.error === 'string' ? err.error : 'No se pudo reenviar el mail.',
        }));
      },
    });
  }

  /** Acción directa para el caso más común de RETIRO_APORTE/INGRESO_ENTRADAS (la caja ya no
   * estaba abierta): reabrirla, aplicar el/los movimiento(s) rechazado(s), y volver a cerrarla
   * con el mismo conteo — el backend ya hace todo eso y marca los rechazos resueltos si sale
   * bien. Si hay otros rechazos pendientes de la MISMA caja, se resuelven todos juntos en un
   * solo reabrir/cerrar (ver loteDeLaMismaCaja) en vez de una reapertura por cada uno. Si el
   * rechazo era por otra razón (ej. un dato inválido), el backend simplemente lo rechaza de
   * nuevo con el mismo motivo, así que no hace falta adivinar acá cuándo mostrarlo o no. */
  reabrirYReintentar(r: OperacionRechazada): void {
    const lote = this.loteDeLaMismaCaja(r);
    const mensaje =
      lote.length > 1
        ? `Esto va a reabrir la caja de origen, aplicar las ${lote.length} operaciones rechazadas de esa caja (ventas, retiros/aportes o entradas), y volver a cerrarla una sola vez con el mismo conteo que ya tenía. ¿Confirmás?`
        : 'Esto va a reabrir la caja de origen, aplicar la operación rechazada, y volver a cerrarla con el mismo conteo que ya tenía. ¿Confirmás?';
    const confirmado = window.confirm(mensaje);
    if (!confirmado) return;

    const ids = lote.map((x) => x.id);
    this.reabriendoId.set(r.id);
    this.errorReabrir.update((errores) => {
      const copia = { ...errores };
      for (const id of ids) delete copia[id];
      return copia;
    });

    this.rechazoService.reabrirYReintentarLote(ids).subscribe({
      next: () => {
        this.reabriendoId.set(null);
        this.cargar();
      },
      error: (err) => {
        console.error('Error al reabrir y reintentar:', err);
        this.reabriendoId.set(null);
        const mensajeError = typeof err?.error === 'string' ? err.error : 'No se pudo reabrir la caja y reintentar.';
        this.errorReabrir.update((errores) => {
          const copia = { ...errores };
          for (const id of ids) copia[id] = mensajeError;
          return copia;
        });
      },
    });
  }
}
