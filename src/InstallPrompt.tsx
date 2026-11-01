import { languageForPlace } from './defaultLanguage';
import { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';

const text = {
  sv: { title: 'Ladda ner appen', body: 'Riktig stegräknare. Tillåt installation när telefonen frågar.', get: 'Ladda ner', later: 'Inte nu', ios: 'Tryck på Dela-knappen i Safari och välj "Lägg till på hemskärmen".' },
  da: { title: 'Download appen', body: 'Rigtig skridttæller. Tillad installation, når telefonen spørger.', get: 'Download', later: 'Ikke nu', ios: 'Tryk på Del-knappen i Safari og vælg "Føj til hjemmeskærm".' },
  en: { title: 'Download the app', body: 'A real step counter. Allow installation when your phone asks.', get: 'Download', later: 'Not now', ios: 'Tap the Share button in Safari and choose "Add to Home Screen".' },
};
const KEY = 'install-prompt-dismissed';

// Android gets the real app (APK, native step counter). iPhone has no such file, so it only gets the home-screen tip.
export default function InstallPrompt() {
  const ua = navigator.userAgent;
  const android = /android/i.test(ua);
  const ios = /iphone|ipad|ipod/i.test(ua);
  const [show, setShow] = useState(false);
  const t = text[(localStorage.getItem('stride-lang') || languageForPlace()) as keyof typeof text] || text.sv;

  useEffect(() => {
    const installed = Capacitor.isNativePlatform() || matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone;
    let dismissed = false;
    try { dismissed = localStorage.getItem(KEY) === '1'; } catch { /* storage blocked: show it */ }
    setShow(!installed && !dismissed && (android || ios));
  }, [android, ios]);

  // Room under the page so the bar never hides the last row; it can be scrolled into view.
  useEffect(() => { if (!show) return; document.body.style.paddingBottom = '72px'; return () => { document.body.style.paddingBottom = ''; }; }, [show]);

  const close = () => { try { localStorage.setItem(KEY, '1'); } catch { /* ignore */ } setShow(false); };
  if (!show) return null;
  // A slim bar: it must not cover the login choices on the start page.
  return (
    <div role="dialog" aria-label={t.title} style={{ position: 'fixed', left: 8, right: 8, bottom: 'calc(8px + env(safe-area-inset-bottom))', zIndex: 9999, background: '#fff', color: '#29283b', borderRadius: 14, padding: '8px 10px', boxShadow: '0 6px 22px rgba(0,0,0,.25)', display: 'flex', gap: 10, alignItems: 'center' }}>
      <img src={import.meta.env.BASE_URL + 'icon-192.png'} alt="" width={36} height={36} style={{ borderRadius: 9 }} />
      <div style={{ flex: 1, fontSize: 11.5, lineHeight: 1.3 }}>
        <strong style={{ display: 'block', fontSize: 13 }}>{t.title}</strong>
        {ios ? t.ios : t.body}
      </div>
      {android && <a href={import.meta.env.BASE_URL + 'step-by-step.apk'} download onClick={close} style={{ background: '#29283b', color: '#fff', borderRadius: 9, padding: '8px 12px', fontSize: 12, fontWeight: 700, textDecoration: 'none', whiteSpace: 'nowrap' }}>{t.get}</a>}
      <button onClick={close} aria-label={t.later} style={{ background: 'transparent', color: '#29283b', border: 0, padding: '6px 8px', fontSize: 16, lineHeight: 1 }}>✕</button>
    </div>
  );
}
