/* Renderar skärmbilderna till PDF:en: index.html -> pdf/img/*.png
   Kräver att index.html serveras (SERVER, default http://127.0.0.1:5173),
   en headless Chromium (CHROME_PATH) och puppeteer-core.

   Kört från design/smartwatch:
     python3 -m http.server 5173 --bind 0.0.0.0
     NODE_PATH=<sökväg med puppeteer-core> node pdf/shots.cjs
     NODE_PATH=<sökväg med puppeteer-core> node pdf/build.cjs
*/
const fs = require('fs');
const puppeteer = require('puppeteer-core');

const CHROME = process.env.CHROME_PATH || '/tmp/cal/chromium.bin';
const LIB = process.env.CHROME_LIBS || '/tmp/cal/libs/lib:/tmp/nspr/dist/lib';
const SERVER = process.env.SERVER || 'http://127.0.0.1:5173';
const OUT = __dirname + '/img/';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const b = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--font-render-hinting=none',
      '--force-color-profile=srgb', '--hide-scrollbars'],
    defaultViewport: { width: 1440, height: 1000, deviceScaleFactor: 2 },
    env: { ...process.env, LD_LIBRARY_PATH: LIB },
  });
  const p = await b.newPage();
  await p.goto(SERVER + '/index.html', { waitUntil: 'networkidle0' });
  await p.evaluate(() => {
    document.documentElement.style.scrollBehavior = 'auto';
    document.querySelectorAll('.reveal').forEach(e => e.classList.add('in'));
    const st = document.createElement('div'); st.id = '__stage';
    st.style.cssText = 'position:fixed;inset:0;z-index:99999;display:grid;place-items:center;' +
      'background:#FAF9F6;pointer-events:none';
    document.body.appendChild(st);
  });
  await new Promise(r => setTimeout(r, 900));

  // Klonar ett element till en egen scen så att det kan fotas i exakt bredd.
  async function shot(selector, index, width, file, pad) {
    const h = await p.evaluateHandle(({ selector, index, width, pad }) => {
      const src = document.querySelectorAll(selector)[index];
      if (!src) throw new Error('saknas: ' + selector + '[' + index + ']');
      const clone = src.cloneNode(true);
      clone.style.transform = 'none';
      clone.style.margin = '0';
      clone.style.width = width + 'px';
      if (/\.watch/.test(selector)) clone.style.setProperty('--w', String(width));
      const st = document.getElementById('__stage');
      st.innerHTML = '';
      const box = document.createElement('div');
      box.style.cssText = 'padding:' + (pad || 0) + 'px;background:#FAF9F6;display:inline-block';
      box.appendChild(clone);
      st.appendChild(box);
      return box;
    }, { selector, index, width, pad });
    await new Promise(r => setTimeout(r, 400));
    const el = h.asElement();
    const bb = await el.boundingBox();
    await el.screenshot({ path: OUT + file });
    console.log(file, Math.round(bb.width) + 'x' + Math.round(bb.height));
  }

  // Framsidan: den illustrerade urtavlan ur hero-sektionen (inte kort 01).
  await shot('.hero-stage .watch--main', 0, 640, 'front-face.png', 34);
  for (let i = 0; i < 14; i++) {
    await shot('.screen-card .watch', i, 420, 'view-' + String(i + 1).padStart(2, '0') + '.png', 26);
  }
  await shot('.band', 0, 1120, 'band.png', 0);
  await shot('.anatomy', 0, 900, 'anatomy.png', 0);

  await p.evaluate(() => { const s = document.getElementById('__stage'); if (s) s.remove(); });
  await new Promise(r => setTimeout(r, 250));
  await (await p.$('.hero-stage')).screenshot({ path: OUT + 'front-stage.png' });
  await b.close();
})();
