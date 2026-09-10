import { EstadoCompra, Reserva } from '../services/boleteria.service';

/** Una línea de pases o de extras ya agrupada por tipo de entrada, lista para el template. */
export interface DetalleLinea {
  nombre: string;
  cantidad: number;
}

/**
 * Reserva ya preparada para el template: pases/extras separados, etiqueta de estado y de fecha
 * resueltas, todo calculado una sola vez por resultado en lugar de recalcularse en cada ciclo
 * de detección de cambios. La usan tanto Control de Accesos (boleteria) como el panel de
 * anticipadas del POS, para que ambas pantallas muestren lo mismo de la misma forma.
 */
export interface ReservaVista {
  reserva: Reserva;
  pases: DetalleLinea[];
  extras: DetalleLinea[];
  totalPases: number;
  /**
   * "Hoy" / "Mañana" / "Ayer" cuando aplica, si no "vie 12/09". Sin fecha de visita hay dos
   * casos distintos y no se pueden mostrar igual: el regalo (lo usa quien lo recibe) y la
   * reserva abierta que genera un admin (invitado, premio), donde el titular es quien entra.
   */
  etiquetaFecha: string;
  etiquetaEstado: string;
}

const DIAS_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const DIAS_SEMANA_CORTOS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** "Hoy"/"Mañana"/"Ayer" cuando aplica; null para el resto (que se muestra con día de semana + fecha corta). */
export function etiquetaRelativaFecha(fechaVisita: string, hoy: string): string | null {
  if (fechaVisita === hoy) return 'Hoy';
  const dHoy = new Date(hoy + 'T00:00:00');
  const dVisita = new Date(fechaVisita + 'T00:00:00');
  const diffDias = Math.round((dVisita.getTime() - dHoy.getTime()) / 86400000);
  if (diffDias === 1) return 'Mañana';
  if (diffDias === -1) return 'Ayer';
  return null;
}

/** Encabezado largo de un día (separadores de la vista agrupada): relativa si aplica, si no "Jueves 12 de septiembre". */
export function etiquetaGrupoFecha(fechaVisita: string, hoy: string): string {
  const relativa = etiquetaRelativaFecha(fechaVisita, hoy);
  if (relativa) return relativa;
  const d = new Date(fechaVisita + 'T00:00:00');
  return `${DIAS_SEMANA[d.getDay()]} ${d.getDate()} de ${MESES[d.getMonth()]}`;
}

/** Etiqueta compacta para una fila: "Hoy" / "Mañana" / "Ayer", o "vie 12/09". */
export function etiquetaFechaFila(fechaVisita: string, hoy: string): string {
  const relativa = etiquetaRelativaFecha(fechaVisita, hoy);
  if (relativa) return relativa;
  const d = new Date(fechaVisita + 'T00:00:00');
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${DIAS_SEMANA_CORTOS[d.getDay()]} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}`;
}

/** A nivel de módulo para no reconstruir el objeto en cada lectura. */
const ETIQUETAS_ESTADO: Record<EstadoCompra, string> = {
  APROBADO: 'Pagada online',
  RESERVADO_EFECTIVO: 'A cobrar en caja',
  USADO: 'Ya utilizada',
  PENDIENTE_PAGO: 'Pago pendiente',
  CANCELADO: 'Cancelada',
  VENDIDO_EN_PUERTA: 'Vendida en puerta',
  REEMBOLSADA: 'Reembolsada',
};

export function etiquetaEstadoCompra(estado: EstadoCompra): string {
  return ETIQUETAS_ESTADO[estado] ?? estado;
}

export function aVista(reserva: Reserva, hoy: string): ReservaVista {
  const pases: DetalleLinea[] = [];
  const extras: DetalleLinea[] = [];
  let totalPases = 0;

  for (const detalle of reserva.detalles ?? []) {
    const tipo = detalle.tipoEntrada;
    if (!tipo) continue;

    if (tipo.tipo === 'ENTRADA') {
      pases.push({ nombre: tipo.nombre, cantidad: detalle.cantidad });
      totalPases += detalle.cantidad;
    } else {
      extras.push({ nombre: tipo.nombre, cantidad: detalle.cantidad });
    }
  }

  return {
    reserva,
    pases,
    extras,
    totalPases,
    // Sin fecha: se distingue por receptor, no por el prefijo del código. El receptor es el
    // dato de negocio (un regalo lo usa otra persona, y se valida con SU DNI); el REGALO-/
    // ABIERTA- del código es sólo su representación visible.
    etiquetaFecha: reserva.fechaVisita
      ? etiquetaFechaFila(reserva.fechaVisita, hoy)
      : reserva.receptorNombre
        ? 'Regalo — cualquier día'
        : 'Sin fecha — cualquier día',
    etiquetaEstado: etiquetaEstadoCompra(reserva.estado),
  };
}
