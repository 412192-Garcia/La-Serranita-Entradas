/** Fecha local (no UTC) en formato yyyy-MM-dd, para inputs type="date" y filtros de rango. */
export function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Mismo día/mes un año antes (para comparativas año contra año en Reportes); si cae en 29/2,
 * JS corre la fecha sola al 1/3 del año no bisiesto — no hace falta un caso especial. */
export function restarUnAnio(fechaISO: string): string {
  const [anio, mes, dia] = fechaISO.split('-').map(Number);
  return aFechaISO(new Date(anio - 1, mes - 1, dia));
}
