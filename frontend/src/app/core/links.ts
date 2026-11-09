/** Opens the phone's dialler. */
export function callLink(phone: string): string {
  return `tel:${phone.replace(/[\s-]/g, '')}`;
}

/** Opens directions in Google Maps (the app on phones that have it). */
export function directionsLink(address: string): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`;
}

/** yyyy-MM-dd for a day from now, in the person's own time zone. */
export function dayFromNow(days: number, now = new Date()): string {
  const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() + days);
  const month = String(day.getMonth() + 1).padStart(2, '0');
  return `${day.getFullYear()}-${month}-${String(day.getDate()).padStart(2, '0')}`;
}
