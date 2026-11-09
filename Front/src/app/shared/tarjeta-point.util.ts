const TIPOS_TARJETA: Record<string, string> = {
  credit_card: 'crédito',
  debit_card: 'débito',
  prepaid_card: 'prepaga',
};

/** "Visa crédito · 3 cuotas", a partir de lo que devuelve Mercado Pago para un cobro con el Point. */
export function descripcionTarjeta(medio: string | null, tipo: string | null, cuotas: number | null): string | null {
  if (!medio) return null;
  const nombre = medio === 'account_money' ? 'Dinero en Mercado Pago' : medio.charAt(0).toUpperCase() + medio.slice(1);
  const tipoTexto = tipo ? TIPOS_TARJETA[tipo] : null;
  const cuotasTexto = cuotas && cuotas > 1 ? ` · ${cuotas} cuotas` : '';
  return `${nombre}${tipoTexto ? ' ' + tipoTexto : ''}${cuotasTexto}`;
}
