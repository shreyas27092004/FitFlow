/**
 * FitFlow Currency Helpers - Integer Paise Math Only
 * R9 / Contract: Never calculate money using floating point math in the frontend.
 */

export function formatPaise(paise: number): string {
  if (!Number.isInteger(paise)) {
    throw new Error(`formatPaise requires an integer paise value, received: ${paise}`);
  }

  const sign = paise < 0 ? '-' : '';
  const absPaise = Math.abs(paise);
  const rupees = Math.floor(absPaise / 100);
  const remainder = absPaise % 100;
  const remainderStr = remainder < 10 ? `0${remainder}` : `${remainder}`;

  return `${sign}Rs ${rupees}.${remainderStr}`;
}

/**
 * Format paise for simple display without currency symbol (e.g. for inputs or tabular digits)
 */
export function formatPaiseRaw(paise: number): string {
  if (!Number.isInteger(paise)) {
    throw new Error(`formatPaiseRaw requires an integer paise value, received: ${paise}`);
  }
  const sign = paise < 0 ? '-' : '';
  const absPaise = Math.abs(paise);
  const rupees = Math.floor(absPaise / 100);
  const remainder = absPaise % 100;
  const remainderStr = remainder < 10 ? `0${remainder}` : `${remainder}`;
  return `${sign}${rupees}.${remainderStr}`;
}
