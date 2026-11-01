import { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';

const text = {
  sv: { title: 'Ladda ner appen', body: 'Appen har riktig stegräknare som fungerar i fickan och med skärmen av. Ladda ner den gratis. Godkänn "Installera från den här källan" när telefonen frågar.', get: 'Ladda ner appen', later: 'Inte nu', ios: 'Tryck på Dela-knappen i Safari och välj "Lägg till på hemskärmen".' },
  da: { title: 'Download appen', body: 'Appen har en rigtig skridttæller, der virker i lommen og med skærmen slukket. Download den gratis. Godkend "Installér fra denne kilde", når telefonen spørger.', get: 'Download appen', later: 'Ikke nu', ios: 'Tryk på Del-knappen i Safari og vælg "Føj til hjemmeskærm".' },
  en: { title: 'Download the app', body: 'The app has a real step counter that works in your pocket with the screen off. Download it for free. Allow "Install from this source" when your phone asks.', get: 'Download the app', later: 'Not now', ios: 'Tap the Share button in Safari and choose "Add to Home Screen".' },
};
const KEY = 'install-prompt-dismissed';

// Android gets the real app (APK, native step counter). iPhone has no such file, so it only gets the home-screen tip.
export default function InstallPrompt() {
  const ua = navigator.userAgent;
  const android = /android/i.test(ua);
  const ios = /iphone|ipad|ipod/i.test(ua);
  const [show, setShow] = useState(false);
  const t = text[(localStorage.getItem('stride-lang') || 'sv') as keyof typeof text] || text.sv;

  useEffect(() => {
    const installed = Capacitor.isNativePlatform() || matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone;
    let dismissed = false;
    try { dismissed = localStorage.getItem(KEY) === '1'; } catch { /* storage blocked: show it */ }
    setShow(!installed && !dismissed && (android || ios));
  }, [android, ios]);

  const close = () => { try { localStorage.setItem(KEY, '1'); } catch { /* ignore */ } setShow(false); };
  if (!show) return null;
  return (
    <div role="dialog" aria-label={t.title} style={{ position: 'fixed', left: 12, right: 12, bottom: 'calc(12px + env(safe-area-inset-bottom))', zIndex: 9999, background: '#fff', color: '#29283b', borderRadius: 16, padding: 16, boxShadow: '0 8px 30px rgba(0,0,0,.25)', display: 'flex', gap: 12, alignItems: 'center' }}>
      <img src={import.meta.env.BASE_URL + 'icon-192.png'} alt="" width={48} height={48} style={{ borderRadius: 12 }} />
      <div style={{ flex: 1, fontSize: 14, lineHeight: 1.35 }}>
        <strong style={{ display: 'block', fontSize: 15 }}>{t.title}</strong>
        {ios ? t.ios : t.body}
        <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
          {android && <a href={import.meta.env.BASE_URL + 'step-by-step.apk'} download onClick={close} style={{ background: '#29283b', color: '#fff', borderRadius: 10, padding: '8px 14px', fontWeight: 600, textDecoration: 'none' }}>{t.get}</a>}
          <button onClick={close} style={{ background: 'transparent', color: '#29283b', border: 0, padding: '8px 10px' }}>{t.later}</button>
        </div>
      </div>
    </div>
  );
}
