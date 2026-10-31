// A name-only account has no email, so Supabase stores it under a made-up address built from the name.
// Browser and server share this file, so both build the same address from the same name.
export const USERNAME_DOMAIN = 'users.nrnworld.one';

// 3-20 characters: letters (a-z, å, ä, ö), digits, dot, underscore and hyphen.
// It must start and end with a letter or digit and may not contain two dots, because the name becomes an email local part.
export function cleanUsername(raw: string): string | null {
  const name = raw.trim().normalize('NFC');
  if (name.length < 3 || name.length > 20 || name.includes('..')) return null;
  return /^[a-z0-9åäö][a-z0-9åäö._-]*[a-z0-9åäö]$/.test(name.toLowerCase()) ? name : null;
}

// The same name in another case is the same account, so the address is built from the lower-case name.
// å, ä and ö become +a, +b and +c: a plus sign cannot be typed in a name, so two names never share an address.
export function usernameToEmail(name: string): string {
  const local = name.normalize('NFC').toLowerCase().replace(/å/g, '+a').replace(/ä/g, '+b').replace(/ö/g, '+c');
  return `${local}@${USERNAME_DOMAIN}`;
}
