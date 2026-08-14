/** Comparador de texto con reglas de español (acentos, ñ) para ordenar columnas de listas admin. */
export function compararTexto(a: string, b: string): number {
  return a.localeCompare(b, 'es');
}

/** Comparador numérico para columnas ordenables que admiten valor nulo (ej: "sin límite"): los nulos quedan siempre al final, sea cual sea la dirección. */
export function compararNumero(a: number | null, b: number | null): number {
  if (a === null && b === null) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return a - b;
}

/** Comparador para columnas de estado activo/inactivo: inactivo antes que activo en orden ascendente. */
export function compararBooleano(a: boolean, b: boolean): number {
  return Number(a) - Number(b);
}
