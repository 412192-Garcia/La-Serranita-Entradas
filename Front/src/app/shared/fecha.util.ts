/** Fecha local (no UTC) en formato yyyy-MM-dd, para inputs type="date" y filtros de rango. */
export function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/**
 * Fecha y hora LOCAL (no UTC) en formato yyyy-MM-ddTHH:mm:ss. Para timestamps que el backend
 * guarda como LocalDateTime (el servidor corre en hora Argentina). NUNCA usar
 * `Date.toISOString()` para esto: devuelve UTC y la operación queda 3 h adelantada.
 */
export function aFechaHoraISO(d: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${aFechaISO(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

/** Mismo día/mes un año antes (para comparativas año contra año en Reportes); si cae en 29/2,
 * JS corre la fecha sola al 1/3 del año no bisiesto — no hace falta un caso especial. */
export function restarUnAnio(fechaISO: string): string {
  const [anio, mes, dia] = fechaISO.split('-').map(Number);
  return aFechaISO(new Date(anio - 1, mes - 1, dia));
}
