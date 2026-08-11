/** Fecha local (no UTC) en formato yyyy-MM-dd, para inputs type="date" y filtros de rango. */
export function aFechaISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
