/** Sistema > Estado: una tarjeta por área. */
export interface EstadoSistema {
  generado: string;
  tarjetas: TarjetaEstado[];
}

export interface TarjetaEstado {
  clave: string;
  titulo: string;
  /** OK (verde), ALERTA (rojo, prende la campanita) o INFO (gris: no aplica o no se puede saber). */
  estado: 'OK' | 'ALERTA' | 'INFO';
  resumen: string;
  detalles: string[];
  /** Ruta de la app donde se resuelve (puede traer ?tab=...), o null. */
  enlace: string | null;
}

/** Un registro del historial de acciones. */
export interface RegistroAuditoria {
  id: number;
  fecha: string;
  usuario: string | null;
  rol: string | null;
  accion: string;
  descripcion: string;
  entidad: string | null;
  entidadId: string | null;
  detalle: string | null;
  /** Código HTTP: < 400 se hizo; >= 400 se intentó y falló. */
  resultado: number;
  ruta: string | null;
  ip: string | null;
}

/** Un error del servidor (agrupado: los repetidos suman cantidad). */
export interface Incidente {
  id: number;
  /** FRONT = error de JavaScript que mandó un navegador (request = "Pantalla /ruta"). */
  area: 'PAGOS' | 'FACTURACION' | 'IMPRESION' | 'MAILS' | 'SISTEMA' | 'FRONT';
  nivel: 'ERROR' | 'WARN';
  mensaje: string | null;
  detalle: string | null;
  origen: string | null;
  codigo: string | null;
  usuario: string | null;
  request: string | null;
  primeraVez: string;
  ultimaVez: string;
  cantidad: number;
  resuelto: boolean;
  resueltoPor: string | null;
  resueltoEn: string | null;
}

/** Sistema > Rendimiento: tiempos por endpoint desde que arrancó el servidor. */
export interface Rendimiento {
  desde: string;
  umbralMs: number;
  endpoints: EndpointRendimiento[];
  lentasRecientes: RequestLenta[];
}

export interface EndpointRendimiento {
  endpoint: string;
  cantidad: number;
  promedioMs: number;
  maxMs: number;
  lentas: number;
  errores: number;
}

export interface RequestLenta {
  momento: string;
  endpoint: string;
  ms: number;
  estado: number;
  usuario: string | null;
  /** El mismo id que sale en el log del servidor y en el código EXXXXXX de un error 500. */
  requestId: string;
}

/** Página de Spring Data (lo que devuelven los listados paginados). */
export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
