import { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';

const text = {
  sv: { title: 'Installera som app', body: 'Lägg step.by.step på hemskärmen så öppnas det som en riktig app.', install: 'Installera', later: 'Inte nu', ios: 'Tryck på Dela-knappen i Safari och välj "Lägg till på hemskärmen".' },
  da: { title: 'Installér som app', body: 'Læg step.by.step på startskærmen, så åbner det som en rigtig app.', install: 'Installér', later: 'Ikke nu', ios: 'Tryk på Del-knappen i Safari og vælg "Føj til hjemmeskærm".' },
  en: { title: 'Install as an app', body: 'Add step.by.step to your home screen and it opens like a real app.', install: 'Install', later: 'Not now', ios: 'Tap the Share button in Safari and choose "Add to Home Screen".' },
};
const KEY = 'install-prompt-dismissed';

export default function InstallPrompt() {
  const [event, setEvent] = useState<any>(null);
  const [show, setShow] = useState(false);
  const lang = (localStorage.getItem('stride-lang') || 'sv') as keyof typeof text;
  const t = text[lang] || text.sv;
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);

  useEffect(() => {
    const installed = Capacitor.isNativePlatform() || matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone;
    let dismissed = false;
    try { dismissed = localStorage.getItem(KEY) === '1'; } catch { /* storage blocked: show it */ }
    if (installed || dismissed || !/android|iphone|ipad|ipod|mobile/i.test(navigator.userAgent)) return;
    if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    if (ios) { setShow(true); return; } // iOS has no install event, so only the instruction can be shown
    const onPrompt = (e: Event) => { e.preventDefault(); setEvent(e); setShow(true); };
    window.addEventListener('beforeinstallprompt', onPrompt);
    return () => window.removeEventListener('beforeinstallprompt', onPrompt);
  }, [ios]);

  const close = () => { try { localStorage.setItem(KEY, '1'); } catch { /* ignore */ } setShow(false); };
  if (!show) return null;
  return (
    <div role="dialog" aria-label={t.title} style={{ position: 'fixed', left: 12, right: 12, bottom: 'calc(12px + env(safe-area-inset-bottom))', zIndex: 9999, background: '#fff', color: '#29283b', borderRadius: 16, padding: 16, boxShadow: '0 8px 30px rgba(0,0,0,.25)', display: 'flex', gap: 12, alignItems: 'center' }}>
      <img src="/icon-192.png" alt="" width={48} height={48} style={{ borderRadius: 12 }} />
      <div style={{ flex: 1, fontSize: 14, lineHeight: 1.35 }}>
        <strong style={{ display: 'block', fontSize: 15 }}>{t.title}</strong>
        {ios ? t.ios : t.body}
        <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
          {!ios && <button onClick={async () => { event.prompt(); await event.userChoice; close(); }} style={{ background: '#29283b', color: '#fff', border: 0, borderRadius: 10, padding: '8px 14px', fontWeight: 600 }}>{t.install}</button>}
          <button onClick={close} style={{ background: 'transparent', color: '#29283b', border: 0, padding: '8px 10px' }}>{t.later}</button>
        </div>
      </div>
    </div>
  );
}
