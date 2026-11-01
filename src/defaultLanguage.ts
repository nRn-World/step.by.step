export type PlaceLanguage = 'sv' | 'da' | 'en';
const DANISH_ZONES = new Set(['Europe/Copenhagen', 'Atlantic/Faroe', 'America/Nuuk', 'America/Godthab', 'America/Thule', 'America/Danmarkshavn', 'America/Scoresbysund']);

/** Language for the country the phone is in, read from its time zone: Sweden gets Swedish, Denmark Danish, everywhere else English. */
export function languageForPlace(zone: string = Intl.DateTimeFormat().resolvedOptions().timeZone): PlaceLanguage {
  if (zone === 'Europe/Stockholm') return 'sv';
  return DANISH_ZONES.has(zone) ? 'da' : 'en';
}
