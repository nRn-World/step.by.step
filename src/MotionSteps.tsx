import { languageForPlace } from './defaultLanguage';
import { useEffect, useRef, useState } from 'react';
import { Capacitor } from '@capacitor/core';

// Rough step count from the phone's motion sensor in the browser. The Android app reads the real step counter instead.
const text = {
  sv: { title: 'Uppskattade steg (webben)', body: 'Räknar steg med telefonens rörelsesensor medan sidan är öppen. Ungefärligt, inte lika exakt som appen.', start: 'Starta stegräkning', stop: 'Stoppa', counting: 'Räknar steg…', noSensor: 'Din webbläsare saknar rörelsesensor.', denied: 'Rörelsesensorn nekades. Tillåt den i webbläsarens inställningar.' },
  da: { title: 'Anslåede skridt (nettet)', body: 'Tæller skridt med telefonens bevægelsessensor, mens siden er åben. Ca., ikke lige så præcist som appen.', start: 'Start skridttælling', stop: 'Stop', counting: 'Tæller skridt…', noSensor: 'Din browser mangler bevægelsessensor.', denied: 'Bevægelsessensoren blev nægtet. Tillad den i browserens indstillinger.' },
  en: { title: 'Estimated steps (web)', body: 'Counts steps with the phone’s motion sensor while this page is open. Approximate, not as exact as the app.', start: 'Start step counting', stop: 'Stop', counting: 'Counting steps…', noSensor: 'Your browser has no motion sensor.', denied: 'Motion sensor was refused. Allow it in your browser settings.' },
};
const KEY = 'stride-motion';
const dayKey = () => new Date().toLocaleDateString('en-CA');

const readToday = (): number => {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null');
    return saved?.day === dayKey() ? saved.steps : 0;
  } catch { return 0; }
};

export default function MotionSteps({ token, onSaved }: { token: string; onSaved: () => void }) {
  const t = text[(localStorage.getItem('stride-lang') || languageForPlace()) as keyof typeof text] || text.sv;
  const [on, setOn] = useState(false);
  const [error, setError] = useState('');
  const [steps, setSteps] = useState(readToday);
  const count = useRef(readToday());
  const refreshed = useRef(onSaved); // a new callback each render must not restart the counter
  refreshed.current = onSaved;

  useEffect(() => {
    if (!on) return;
    let avg = 0, last = 0, above = false;
    const onMotion = (e: DeviceMotionEvent) => {
      const a = e.accelerationIncludingGravity;
      if (!a || a.x == null || a.y == null || a.z == null) return;
      const magnitude = Math.hypot(a.x, a.y, a.z);
      avg = avg * 0.9 + magnitude * 0.1; // slow average = gravity, the difference = movement
      const now = Date.now();
      const peak = magnitude - avg > 1.5;
      if (peak && !above && now - last > 350) { // one step per peak, at most about three a second
        last = now;
        count.current += 1;
        localStorage.setItem(KEY, JSON.stringify({ day: dayKey(), steps: count.current }));
        setSteps(count.current);
      }
      above = peak;
    };
    window.addEventListener('devicemotion', onMotion);
    const save = () => { if (count.current > 0) void fetch((import.meta.env.VITE_API_URL || '/api') + '/steps', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }, body: JSON.stringify({ steps: count.current }) }).then(() => refreshed.current()).catch(() => undefined); };
    const timer = window.setInterval(save, 30000);
    return () => { window.removeEventListener('devicemotion', onMotion); clearInterval(timer); save(); };
  }, [on, token]);

  if (Capacitor.isNativePlatform()) return null; // the app uses the real step counter

  const start = async () => {
    setError('');
    const motion = (window as any).DeviceMotionEvent;
    if (!motion) { setError(t.noSensor); return; }
    // iPhone asks for permission; this must run from the tap itself.
    if (typeof motion.requestPermission === 'function' && (await motion.requestPermission().catch(() => 'denied')) !== 'granted') { setError(t.denied); return; }
    setOn(true);
  };

  return (
    <article className="tracking-card">
      <div className="tracking-copy">
        <div className="track-title">{t.title}</div>
        <p>{on ? t.counting : t.body}</p>
        {on && <small>{steps} steg</small>}
        {error && <small className="gps-error">{error}</small>}
      </div>
      <button className={'tracking-button ' + (on ? 'tracking-active' : '')} onClick={() => on ? setOn(false) : void start()}>{on ? <><span className="stop-square" />{t.stop}</> : t.start}</button>
    </article>
  );
}
