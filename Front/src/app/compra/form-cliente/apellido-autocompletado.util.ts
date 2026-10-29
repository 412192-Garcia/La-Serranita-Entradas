/**
 * Chrome a veces autocompleta el nombre completo en "Apellido" ("Juan Pérez" en vez de "Pérez").
 * Si el apellido arranca con el nombre, devuelve el apellido sin él; si no, null.
 */
export function apellidoSinNombre(nombre: string | null | undefined, apellido: string | null | undefined): string | null {
  const n = (nombre ?? '').trim().replace(/\s+/g, ' ');
  const a = (apellido ?? '').trim().replace(/\s+/g, ' ');
  if (!n || !a) return null;
  const prefijo = `${n} `;
  if (a.length <= prefijo.length || !a.toLocaleLowerCase('es').startsWith(prefijo.toLocaleLowerCase('es'))) return null;
  return a.slice(prefijo.length);
}
