import { Capacitor } from '@capacitor/core';

const KEY = 'stride-pending-join';
const SITE = 'https://nrnworld.one/step.by.step/';

/** The address people are sent to. Inside the installed app the page address is https://localhost, so the real site is used there. */
export function publicUrl(): string {
  return import.meta.env.VITE_PUBLIC_URL || (Capacitor.isNativePlatform() ? SITE : window.location.origin + window.location.pathname);
}

export const inviteLink = (code: string) => `${publicUrl()}?join=${code}`;

/** Remember an invite code from a link, so it survives the sign-in redirect (which drops the ?join= part). */
export function rememberJoin(link: string): void {
  try {
    const code = new URL(link, 'https://x.invalid/').searchParams.get('join');
    if (code && /^[A-Za-z0-9]{6,64}$/.test(code)) localStorage.setItem(KEY, code);
  } catch { /* not a link */ }
}
export const pendingJoin = (): string => { try { return localStorage.getItem(KEY) || ''; } catch { return ''; } };
export const clearPendingJoin = (): void => { try { localStorage.removeItem(KEY); } catch { /* ignore */ } };
