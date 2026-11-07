/* Bygger design-PDF:en: spec.html -> step-by-step-smartwatch-spec.pdf
   Kräver en headless Chromium (CHROME_PATH) och puppeteer-core. */
const fs=require('fs');
const puppeteer=require('puppeteer-core');

const CHROME=process.env.CHROME_PATH||'/tmp/cal/chromium.bin';
const LIB=process.env.CHROME_LIBS||'/tmp/cal/libs/lib:/tmp/nspr/dist/lib';
const HTML=__dirname+'/spec.html';
const PDF=__dirname+'/../step-by-step-smartwatch-spec.pdf';

const views=[
{n:'01',t:'Parkoppla',role:'Uppstart · en gång',
 desc:'Första gången klockan används. Ett varv på handleden och ett tryck i telefonen — sedan finns grupperna där.',
 items:['Logotypen i en rundad ruta, 23 u, med mjuk skugga',
        'Eyebrow: Första gången',
        'Parkoppla i Outfit 700, 7 u',
        'Brödtext 3,6 u: Öppna step.by.step i telefonen och välj klockan. Sedan finns grupperna här.',
        'Chip (mute): Ingen inloggning här'],
 meas:['padding 11 % 12 % · gap 1,4 u','logotyp 23 u, radie 7 u','chip mute: rgba(255,255,255,.09)'],
 copy:'Ett varv på handleden och ett tryck i telefonen. Sedan finns grupperna där.',meta:'Uppstart · en gång'},

{n:'02',t:'Välj grupp',role:'Mina grupper · ett tryck',
 desc:'Har man flera grupper ligger de här. Svep och tryck — klockan följer valet tills man byter.',
 items:['Tre rader: Lunchpromenaden (4 medlemmar, vald, med bock), Kontorsgänget (6), Söndagspromenaden (2)',
        'Vald rad: korall 16 % bakgrund + 1 px korallring',
        'Badge: första bokstaven i gruppnamnet, lavendel',
        'Hint längst ned: Fler grupper läggs till i telefonen'],
 meas:['padding 12 % 11 %','rad: badge 8,2 u · namn 3,8 u · bock 4,2 u','vald rad: radie 3,4 u'],
 copy:'Har du flera grupper ligger de här. Svep och tryck — klockan följer valet tills du byter.',meta:'Mina grupper · ett tryck'},

{n:'03',t:'Dagens steg',role:'Översikt · alltid först',
 desc:'Första vyn efter urtavlan. Steg, sträcka och mål i en ring. Ett tryck går vidare till veckan.',
 items:['Ringen: 86 %, korall gradient, spår i 10 % vitt',
        'Eyebrow: Idag',
        'Stora siffran: 6 842, Outfit 700, 19 u',
        'Under: steg · 5,1 km',
        'Chip: Mål 8 000 · 86 %'],
 meas:['ring: stroke 3 (spår 2,4), inset 4,4 %','--off: 14 (100 − 86)','siffra 19 u · vikt 700 · spärrning -.05em'],
 copy:'Steg, sträcka och mål i en ring. Tryck en gång för att se veckan.',meta:'Översikt · alltid först'},

{n:'04',t:'Veckan',role:'Vecka · sju dagar',
 desc:'Sju staplar, ett streck för veckomålet och idag markerad i korall.',
 items:['34 210 i 12,5 u','Under: steg · 68 % av 50 000',
        'Sju staplar M T O T F L S — idag (L) i korall gradient',
        'Streckad linje = veckomålet 8 000 av 12 000, alltså 67 % upp i diagrammet'],
 meas:['padding 11 % 13 % · diagram 29 u högt','stapel: rgba(255,255,255,.11), radie 99','idag: #ff9a78 → #ff704f med glöd'],
 copy:'Sju staplar, ett streck för veckomålet och idag markerad i korall. Resten av veckan är redan räknad.',meta:'Vecka · sju dagar'},

{n:'05',t:'Målen',role:'Perioder · ett svep',
 desc:'Dag, vecka och månad på samma skärm. Gruppmålet får en egen rad när gruppen sätter ett.',
 items:['Dag 86 % (korall)','Vecka 68 % (lavendel #a58ce6)','Månad 34 % (sage #8fbe6d)','Chip: 8 000 steg i dag'],
 meas:['padding 13 % · gap 2,6 u','etikett 3,1 u versaler · procent i Outfit 4,2 u','bar 2,2 u, radie 99'],
 copy:'Dag, vecka och månad på samma skärm. Gruppmålet får en egen rad när gruppen sätter ett.',meta:'Perioder · ett svep'},

{n:'06',t:'Topplistan',role:'Topplista · svep för fler',
 desc:'Vem leder, vem är jag och hur långt efter är nästa. Tre åt gången, svep för resten.',
 items:['MO Morgonstjärnan 8 214 — krona i guld, 8,2 km · 4 pepp',
        'DU Du 6 842 — egen rad i korall',
        'GL Glada Gåsen 6 118 — 4,6 km',
        'Pager i botten: en aktiv, två passiva'],
 meas:['rad: avatar 8,6 u · namn 4,3 u · siffra 4,7 u','Du-rad: rgba(255,112,79,.09), radie 3 u','krona: 4 u, guld #e2a83f'],
 copy:'Tre åt gången, svep för resten. Kronan sitter kvar på den som leder och du själv markeras i korall.',meta:'Topplista · svep för fler'},

{n:'07',t:'Pepp på handleden',role:'Notis · haptik',
 desc:'Den enda vyn med en riktig knapp. Ett tryck skickar ett hjärta — haptiken kvitterar, inget ljud.',
 items:['Notis med MO Morgonstjärnan, tid: nu',
        'Text: pep dig med ett hjärta.',
        'Knapp: Peppa tillbaka (fungerar i prototypen)',
        'Fot: 8 pepp idag'],
 meas:['notis: yta #1c1930, radie 6,4 u, padding 4,4 u','knapp 3,9 u, gradient 140° korall → #ff8b63','klick: 8 → 9, hjärtat poppar, Pepp skickad ✓ i 1,4 s'],
 copy:'Ett tryck skickar ett hjärta. Haptiken kvitterar — inget ljud, ingen skärm som måste öppnas.',meta:'Notis · haptik'},

{n:'08',t:'Tillsammans live',role:'Live · bara siffror',
 desc:'Hur många som är ute just nu och gruppens samlade steg. Inga kartor, inga positioner.',
 items:['Live · Lunchpromenaden','Stor siffra: 3','Under: ute och går nu','Tre avatarer med ring i sage, korall och lavendel','Chip: 28 461 steg i gruppen'],
 meas:['siffra 21 u · 700 · spärrning -.045em','avatar 9,4 u, ring 0,8 u','padding 12 %'],
 copy:'Antal som är ute nu och gruppens samlade steg. Inga kartor, inga positioner.',meta:'Live · bara siffror'},

{n:'09',t:'Bara här',role:'Utan server · BLE',
 desc:'Gruppen nära dig, via Bluetooth. Listan finns bara så länge vyn är öppen — inget sparas.',
 items:['Eyebrow: Nära varandra','Bara här i Outfit 700, 8,6 u','Nätverk: en korall mittpunkt och fyra noder med streckade länkar','Under: 4 i närheten','Chip (mute): Ingen server · ingen position'],
 meas:['nätverk 86 % brett, viewBox 0 0 100 60','länkar: streckad 2,2 2,6 · opacity ,7','nod 3,2 · mittpunkt: korall ring 1,6'],
 copy:'Gruppen nära dig via Bluetooth. Listan finns bara så länge vyn är öppen.',meta:'Utan server · BLE'},

{n:'10',t:'Race mot partner',role:'Ihopkopplade grupper',
 desc:'Två lag mot samma mållinje. Klockan visar avståndet — inte vägen dit.',
 items:['Eyebrow: Vecka · gemensamt mål','Bana med streckad väg, två löpare och målflagga','1 240 i 12,5 u','Under: steg före Kontorsgänget (ordet i korall)','Två rader: Lunchpromenaden 42 180 · Kontorsgänget 40 940'],
 meas:['bana 84 % × 11 u','löpare 5,2 u vid 64 % (korall) och 52 % (lavendel)','målflagga: schackrutor 1,7 u'],
 copy:'Två lag mot samma mål. Klockan visar avståndet till mållinjen — inte vägen dit.',meta:'Ihopkopplade grupper'},

{n:'11',t:'Analyskarta',role:'Bara gruppmål jämförs',
 desc:'Samma två lag och samma vecka som racet, men som staplar. Talen måste stämma med vy 10.',
 items:['Lunchpromenaden 42 180 (100 %)','Kontorsgänget 40 940 (97 %)','Chip: Ni leder med 1 240','Under: Tillsammans 83 120 av 100 000'],
 meas:['padding 12 % 13 % · gap 2,4 u','bar 2,4 u: korall respektive lavendel','siffror i Outfit 3,6 u'],
 copy:'Samma två lag, samma vecka: staplarna jämför lagen och chipen säger hur mycket ni leder med.',meta:'Bara gruppmål jämförs'},

{n:'12',t:'Målet nått',role:'Firande · en gång per mål',
 desc:'Enda vyn där accentfärgen byts. Grönt betyder klart — ring, chip, trofé och en svag inre glöd.',
 items:['Hel ring i grön gradient (gradSage)','Trofén i ljust grönt på grön bricka','Målet nått · 8 000 · steg · idag','Chip: 4 av 4 klara'],
 meas:['gradSage #c3e8a4 → #8ed46c → #67a94c','.screen--done: inre glöd 16 %','chip + ikon: rgba(124,174,92,.2)','ingen korall, inget guld i vyn'],
 copy:'En kort scen i tre sekunder, ritad för rund skärm. En per mål: dag, vecka, helg, månad, år och gruppmål.',meta:'Firande · en gång per mål',accent:'sage'},

{n:'13',t:'Alltid på',role:'Ambient · låg ström',
 desc:'Nedtonat läge: tid, steg och procent. Inga färger, inga animationer — knappt någon batteri.',
 items:['lör 7 nov','09:41 i Outfit 300','6 842 steg · 86 %','En tunn ring r = 44 utan färg'],
 meas:['tid 23 u · vikt 300 · spärrning -.055em','datum 3,1 u versaler, spärrning .17em','ring: stroke 1,2 i spårets färg'],
 copy:'Tid, steg och procent i nedtonat läge. Inga färger, inga animationer — knappt någon batteri.',meta:'Ambient · låg ström'},

{n:'14',t:'Inställningar',role:'Inställningar · få val',
 desc:'Bara tre saker går att ändra på handleden. Allt annat sköts i telefonen.',
 items:['Ljust armband — av (grå)','Haptik — på (korall)','Språk — Svenska','Fot: Allt annat sköts i telefonen'],
 meas:['padding 12 % 13 %','rad 3,7 u, avdelare rgba(255,255,255,.12)','toggle 8,8 × 5 u, på = korall 85 %'],
 copy:'Ljust armband, haptik och språk. Allt annat sköts i telefonen.',meta:'Inställningar · få val'},
];

const uiStrings={
'01':['Första gången','Parkoppla','Öppna step.by.step i telefonen och välj klockan. Sedan finns grupperna här.','Ingen inloggning här'],
'02':['Mina grupper','Lunchpromenaden · 4 medlemmar','Kontorsgänget · 6 medlemmar','Söndagspromenaden · 2 medlemmar','Fler grupper läggs till i telefonen'],
'03':['Idag','6 842','steg · 5,1 km','Mål 8 000 · 86 %'],
'04':['Veckan','34 210','steg · 68 % av 50 000','M  T  O  T  F  L  S'],
'05':['Målen','Dag 86 %','Vecka 68 %','Månad 34 %','8 000 steg i dag'],
'06':['Topplista','Morgonstjärnan','8 214','8,2 km · 4 pepp','Du','6 842','5,1 km','Glada Gåsen','6 118','4,6 km'],
'07':['Morgonstjärnan','nu','pep dig med ett hjärta.','Peppa tillbaka','8 pepp idag','Pepp skickad ✓'],
'08':['Live · Lunchpromenaden','3','ute och går nu','28 461 steg i gruppen'],
'09':['Nära varandra','Bara här','4 i närheten','Ingen server · ingen position'],
'10':['Vecka · gemensamt mål','1 240','steg före Kontorsgänget','Lunchpromenaden 42 180','Kontorsgänget 40 940'],
'11':['Veckan · två lag','Lunchpromenaden 42 180','Kontorsgänget 40 940','Ni leder med 1 240','Tillsammans 83 120 av 100 000'],
'12':['Målet nått','8 000','steg · idag','4 av 4 klara'],
'13':['lör 7 nov','09:41','6 842 steg · 86 %'],
'14':['Inställningar','Ljust armband','Haptik','Språk','Svenska','Allt annat sköts i telefonen'],
};

const css=`
@font-face{font-family:'Outfit';src:url('../assets/outfit-latin-wght-normal.woff2') format('woff2');font-weight:100 900;font-display:block}
@font-face{font-family:'DM Sans';src:url('../assets/dm-sans-latin-wght-normal.woff2') format('woff2');font-weight:100 1000;font-display:block}
:root{
  --ink:#1f1e2e; --muted:#63606f; --dim:#8b8798; --line:#e6e0d5; --cream:#faf9f6;
  --coral:#ff704f; --coral-deep:#e2573a; --lav:#7c60bb; --sage:#5f9c47; --gold:#c08a26;
}
*{box-sizing:border-box}
html,body{margin:0;padding:0;background:#fff;color:var(--ink);
  font-family:'DM Sans',system-ui,sans-serif;-webkit-print-color-adjust:exact;print-color-adjust:exact}
@page{size:A4;margin:0}
.page{position:relative;width:210mm;height:296mm;overflow:hidden;background:#fff;
  padding:13mm 16mm 12mm;page-break-after:always;break-after:page}
.page:last-child{page-break-after:auto;break-after:auto}
h1,h2,h3,h4{font-family:'Outfit',sans-serif;margin:0;letter-spacing:-.03em;font-weight:700}
p{margin:0;line-height:1.55}
.kicker{font-size:7.2pt;font-weight:800;letter-spacing:.2em;text-transform:uppercase;color:var(--coral-deep)}
.hdr{display:flex;align-items:center;gap:2.4mm;font-size:7pt;color:var(--dim);
  letter-spacing:.14em;text-transform:uppercase;font-weight:700;padding-bottom:3mm;border-bottom:1px solid var(--line)}
.hdr img{width:6mm;height:6mm;border-radius:1.8mm}
.hdr .sp{flex:1}
.pgnum{font-family:'Outfit';font-weight:700;letter-spacing:0;text-transform:none;font-size:8pt;color:var(--ink)}
h2{font-size:21pt;line-height:1.04;letter-spacing:-.035em;margin:6mm 0 2.4mm}
.role{font-size:8.4pt;font-weight:800;letter-spacing:.16em;text-transform:uppercase;color:var(--coral-deep)}
.lead{font-size:9.4pt;color:var(--muted);max-width:118mm;line-height:1.6;margin-top:3mm}
.rule{height:2px;background:var(--coral);width:14mm;border-radius:2px;margin:5mm 0 0}

/* omslag */
.cover{background:var(--cream);padding:18mm 18mm 14mm;display:flex;flex-direction:column}
.cover .top{display:flex;align-items:center;gap:3mm}
.cover .top img{width:11mm;height:11mm;border-radius:3mm}
.cover .wm{font-family:'Outfit';font-weight:800;font-size:13pt;letter-spacing:-.045em}
.cover .wm i{font-style:normal;color:var(--coral)}
.cover h1{font-size:37pt;line-height:.98;letter-spacing:-.045em;margin:11mm 0 5mm;max-width:150mm}
.cover h1 em{font-style:normal;color:var(--coral-deep)}
.cover .sub{font-size:10pt;color:var(--muted);max-width:112mm;line-height:1.6}
.cover .hero{margin:8mm auto auto;width:126mm;background:var(--cream);border:1px solid var(--line);border-radius:6mm;
  padding:3mm;box-shadow:0 10mm 20mm -14mm rgba(35,32,54,.28)}
.cover .hero img{width:100%;display:block}
.cover .facts{display:flex;gap:4mm;margin-top:auto}
.cover .fact{flex:1;border-top:1px solid var(--line);padding-top:3.5mm}
.cover .fact b{display:block;font-family:'Outfit';font-size:15pt;letter-spacing:-.02em}
.cover .fact span{font-size:7.4pt;color:var(--dim);text-transform:uppercase;letter-spacing:.14em;font-weight:800}
.cover .meta{display:flex;justify-content:space-between;margin-top:5mm;font-size:7.6pt;color:var(--dim)}

/* innehåll */
.toc{display:grid;grid-template-columns:1fr 1fr;grid-template-rows:repeat(12,auto);
  grid-auto-flow:column;gap:0 8mm;margin-top:6mm}
.toc a,.toc div{display:flex;align-items:baseline;gap:2mm;font-size:8.8pt;padding:1.9mm 0;border-bottom:1px solid var(--line);color:var(--ink);text-decoration:none}
.toc b{font-weight:800}
.toc .n{font-family:'Outfit';font-weight:700;font-size:7.6pt;color:var(--coral-deep);min-width:7mm}
.toc .d{flex:1;border-bottom:1px dotted #d8d2c6;transform:translateY(-1mm)}
.toc .p{font-size:7.6pt;color:var(--dim)}

/* generella block */
.two{display:grid;grid-template-columns:1fr 1fr;gap:7mm;margin-top:6mm}
.box{border:1px solid var(--line);border-radius:4mm;padding:5mm 5.5mm;background:#fff}
.box.cream{background:var(--cream)}
.box h3{font-size:11pt;margin-bottom:2.6mm}
.box p{font-size:8.6pt;color:var(--muted)}
ul.clean{list-style:none;margin:0;padding:0}
ul.clean li{position:relative;padding-left:4.6mm;font-size:8.6pt;line-height:1.5;color:var(--muted);margin-bottom:1.7mm}
ul.clean li::before{content:"";position:absolute;left:0;top:1.5mm;width:2.4mm;height:1px;background:var(--coral);border-radius:1px}
ul.clean li b{color:var(--ink);font-weight:700}
.stop{border-left:3px solid var(--coral);background:#fff5f1;border-radius:2mm;padding:4mm 5mm;margin-top:4mm}
.stop h3{font-size:10pt;margin-bottom:2mm;color:var(--coral-deep)}
.stop li::before{background:var(--coral-deep);height:1.4px}

/* vyer */
.vhead{display:flex;align-items:flex-end;gap:5mm;margin:6mm 0 5mm}
.vbadge{width:13mm;height:13mm;border-radius:3mm;background:rgba(255,112,79,.14);color:var(--coral-deep);
  display:flex;align-items:center;justify-content:center;font-family:'Outfit';font-weight:800;font-size:13pt;flex:none}
.vhead h2{margin:0;font-size:25pt}
.vhead .role{margin-top:1.6mm}
.vbody{display:grid;grid-template-columns:104mm 1fr;gap:7mm;align-items:start}
.shot{background:var(--cream);border:1px solid var(--line);border-radius:5mm;padding:2mm}
.shot img{width:100%;display:block}
.vtext h3{font-size:9.4pt;letter-spacing:.14em;text-transform:uppercase;color:var(--dim);margin:0 0 2.4mm}
.vtext h3+h3,.vtext ul+h3{margin-top:5mm}
.vtext ul.clean li{font-size:8.4pt;margin-bottom:1.5mm;color:#54515f}
/* texter i vyn */
.uigrid{display:grid;grid-template-columns:1fr 1fr;gap:1.5mm 7mm;margin-top:2.6mm}
.uigrid span{position:relative;padding-left:3.8mm;font-size:8.3pt;color:#4e4b5a;line-height:1.4}
.uigrid span::before{content:"›";position:absolute;left:0;top:-.2mm;color:var(--coral);font-weight:800;font-size:9pt}
.ui-note{font-size:7.2pt;color:var(--dim);margin-top:3mm}
.ui-note code{font-family:ui-monospace,Menlo,monospace;font-size:6.8pt;background:#f4f1ea;padding:.3mm 1mm;border-radius:1mm}

.sitecopy{position:absolute;left:16mm;right:16mm;bottom:15mm;
  border-top:1px solid var(--line);padding-top:3.4mm;display:flex;gap:5mm;align-items:flex-start}
.sitecopy .lbl{font-size:7pt;font-weight:800;letter-spacing:.16em;text-transform:uppercase;color:var(--dim);padding-top:.6mm;flex:none}
.sitecopy p{font-size:8.8pt;color:var(--muted);font-style:italic}
.sitecopy .meta{font-style:normal;font-size:7.4pt;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:var(--coral-deep)}

/* tokens */
.sw{display:grid;grid-template-columns:repeat(4,1fr);gap:3.4mm;margin-top:4mm}
.sw .c{border:1px solid var(--line);border-radius:3mm;overflow:hidden}
.sw .chip{height:16mm}
.sw .lbl{padding:2.4mm 2.8mm;font-size:7pt;line-height:1.4}
.sw .lbl b{display:block;font-family:'Outfit';font-size:8.4pt;letter-spacing:-.01em;margin-bottom:.6mm}
.sw .lbl code{font-family:ui-monospace,Menlo,monospace;font-size:6.6pt;color:var(--dim)}
table.t{width:100%;border-collapse:collapse;margin-top:3mm}
table.t th,table.t td{text-align:left;font-size:8.2pt;padding:2.2mm 2mm;border-bottom:1px solid var(--line);vertical-align:top}
table.t th{font-size:7pt;letter-spacing:.14em;text-transform:uppercase;color:var(--dim);font-weight:800}
table.t td b{font-weight:700}
table.t td code{font-family:ui-monospace,Menlo,monospace;font-size:7.4pt;background:#f4f1ea;padding:.4mm 1.2mm;border-radius:1mm}
.type{display:flex;align-items:baseline;gap:5mm;border-bottom:1px solid var(--line);padding:3mm 0}
.type .sample{font-family:'Outfit';font-weight:700;letter-spacing:-.03em}
.type .info{font-size:7.6pt;color:var(--dim);margin-left:auto;text-align:right}

/* anatomi / komplikationer */
.wide{background:var(--cream);border:1px solid var(--line);border-radius:5mm;padding:3mm;margin-top:5mm}
.wide img{width:100%;display:block}
.cols3{display:grid;grid-template-columns:repeat(3,1fr);gap:5mm;margin-top:5mm}
.cols3 h3{font-size:10pt;margin-bottom:1.6mm}
.cols3 .k{font-size:6.8pt;font-weight:800;letter-spacing:.16em;text-transform:uppercase;color:var(--coral-deep)}
.cols3 p{font-size:8.2pt;color:var(--muted);margin-top:1.4mm}

/* kod */
.code{background:#221f2e;border-radius:4mm;padding:4.5mm 5mm;margin-top:5mm;overflow:hidden}
.code .lbl{font-size:6.8pt;font-weight:800;letter-spacing:.16em;text-transform:uppercase;color:#a49dc0;margin-bottom:2.6mm}
.code pre{margin:0;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:7.4pt;line-height:1.55;
  color:#efeaf5;white-space:pre-wrap}
.code pre i{color:#8fd0a8;font-style:normal}
.code pre b{color:#ffb59e;font-weight:400}

/* checklista */
.check{columns:2;column-gap:8mm;margin-top:5mm}
.check div{break-inside:avoid;display:flex;gap:2.6mm;font-size:8.4pt;color:#4e4b5a;line-height:1.45;margin-bottom:2.6mm}
.check .bx{width:3.4mm;height:3.4mm;border:1.2px solid #c9a99f;border-radius:.9mm;flex:none;margin-top:.7mm}
.foot{position:absolute;left:16mm;right:16mm;bottom:7mm;display:flex;justify-content:space-between;
  font-size:6.8pt;color:#b3aec0;letter-spacing:.1em;text-transform:uppercase}
`;

const head=(label,pg)=>`<div class="hdr"><img src="../assets/brand-mark.png" alt=""> step.by.step · på handleden<span class="sp"></span>${label}<span class="pgnum">${pg}</span></div>`;

const viewPages=views.map((v,i)=>{
  const pg=8+i;
  return `<div class="page">
  ${head('Vy '+v.n,v.n)}
  <div class="vhead">
    <div class="vbadge">${v.n}</div>
    <div><h2>${v.t}</h2><div class="role">${v.role}</div></div>
  </div>
  <p class="lead">${v.desc}</p>
  <div class="vbody" style="margin-top:6mm">
    <figure class="shot" style="margin:0"><img src="img/view-${v.n}.png" alt="${v.t}"></figure>
    <div class="vtext">
      <h3>Innehåll</h3>
      <ul class="clean">${v.items.map(x=>`<li>${x}</li>`).join('')}</ul>
      <h3>Mått och färg</h3>
      <ul class="clean">${v.meas.map(x=>`<li>${x}</li>`).join('')}</ul>
    </div>
  </div>
  <div class="box" style="margin-top:6mm;padding:4.4mm 5mm">
    <h3 style="font-size:9.4pt;letter-spacing:.14em;text-transform:uppercase;color:var(--dim);margin:0 0 0">
      Texter i vyn, ordagrant</h3>
    <div class="uigrid">${(uiStrings[v.n]||[]).map(x=>`<span>${x}</span>`).join('')}</div>
    <p class="ui-note">Versalerna kommer från <code>text-transform:uppercase</code> — skriv aldrig in dem i versaler i texten.</p>
  </div>
  <div class="sitecopy">
    <span class="lbl">På sajten</span>
    <p>${v.copy}<br><span class="meta">${v.meta}</span></p>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>`;}).join('\n');

const html=`<!doctype html><html lang="sv"><head><meta charset="utf-8">
<title>step.by.step på handleden — designspec</title><style>${css}</style></head><body>

<!-- 1 OMSLAG -->
<div class="page cover">
  <div class="top"><img src="../assets/brand-mark.png" alt=""><span class="wm">step<i>.</i>by<i>.</i>step</span></div>
  <h1>Samma steg.<br><em>Mycket mindre skärm.</em></h1>
  <p class="sub">Designspec för smartwatch — hur step.by.step ser ut på en rund handledsdator.
  Fjorton vyer, två armband och en urtavla som är en liten värld. Allt ritat i appens egna färger, typsnitt och siffror.</p>
  <div class="hero"><img src="img/front-face.png" alt="Framsidan: illustrerad urtavla"></div>
  <div class="facts">
    <div class="fact"><b>14</b><span>vyer</span></div>
    <div class="fact"><b>2</b><span>armband</span></div>
    <div class="fact"><b>41/45</b><span>millimeter</span></div>
    <div class="fact"><b>1</b><span>accent i taget</span></div>
  </div>
  <div class="meta"><span>Koncept · Wear OS &amp; watchOS</span><span>Version 1.2 · 8 november 2026</span></div>
</div>

<!-- 2 INNEHÅLL -->
<div class="page">
  ${head('Innehåll','2')}
  <h2>Så läses specen</h2>
  <p class="lead">Varje vy har en egen sida: en bild på exakt hur den ser ut, en lista med vad som står i den,
  och de mått som styr. Allt är räknat i <b>u</b> — en enhet som är 1/107 av klockans bredd — så vyn skalar
  med klockan i stället för att klippas.</p>
  <div class="rule"></div>
  <div class="toc">
    <div><span class="n">3</span><b>Regler och förbud</b><span class="d"></span><span class="p">3</span></div>
    <div><span class="n">4</span><b>Framsidan</b><span class="d"></span><span class="p">4</span></div>
    <div><span class="n">5</span><b>Klockan som komponent</b><span class="d"></span><span class="p">5</span></div>
    <div><span class="n">6</span><b>Färger</b><span class="d"></span><span class="p">6</span></div>
    <div><span class="n">7</span><b>Typografi</b><span class="d"></span><span class="p">7</span></div>
    <div><span class="n">01</span><b>Parkoppla</b><span class="d"></span><span class="p">8</span></div>
    <div><span class="n">02</span><b>Välj grupp</b><span class="d"></span><span class="p">9</span></div>
    <div><span class="n">03</span><b>Dagens steg</b><span class="d"></span><span class="p">10</span></div>
    <div><span class="n">04</span><b>Veckan</b><span class="d"></span><span class="p">11</span></div>
    <div><span class="n">05</span><b>Målen</b><span class="d"></span><span class="p">12</span></div>
    <div><span class="n">06</span><b>Topplistan</b><span class="d"></span><span class="p">13</span></div>
    <div><span class="n">07</span><b>Pepp på handleden</b><span class="d"></span><span class="p">14</span></div>
    <div><span class="n">08</span><b>Tillsammans live</b><span class="d"></span><span class="p">15</span></div>
    <div><span class="n">09</span><b>Bara här</b><span class="d"></span><span class="p">16</span></div>
    <div><span class="n">10</span><b>Race mot partner</b><span class="d"></span><span class="p">17</span></div>
    <div><span class="n">11</span><b>Analyskarta</b><span class="d"></span><span class="p">18</span></div>
    <div><span class="n">12</span><b>Målet nått</b><span class="d"></span><span class="p">19</span></div>
    <div><span class="n">13</span><b>Alltid på</b><span class="d"></span><span class="p">20</span></div>
    <div><span class="n">14</span><b>Inställningar</b><span class="d"></span><span class="p">21</span></div>
    <div><span class="n">A</span><b>Komplikationer</b><span class="d"></span><span class="p">22</span></div>
    <div><span class="n">B</span><b>Anatomi</b><span class="d"></span><span class="p">23</span></div>
    <div><span class="n">C</span><b>Principer</b><span class="d"></span><span class="p">24</span></div>
    <div><span class="n">D</span><b>Arkitektur och bygge</b><span class="d"></span><span class="p">25</span></div>
    <div><span class="n">E</span><b>Checklista</b><span class="d"></span><span class="p">26</span></div>
  </div>
  <div class="box cream" style="margin-top:7mm">
    <h3>Tre saker som gäller hela bygget</h3>
    <ul class="clean" style="margin-top:2mm">
      <li><b>En accent i taget.</b> Korall betyder mål och rörelse, lavendel betyder grupp, grönt betyder klart.</li>
      <li><b>Siffran är hjälten.</b> Steg och procent i Outfit, etiketter i DM Sans — aldrig mindre än 3 u.</li>
      <li><b>Ingen position.</b> Klockan visar siffror och skickar pepp. Den vet aldrig var någon är.</li>
    </ul>
  </div>
  <div class="two" style="margin-top:5mm">
    <div class="box">
      <h3>Så är bilderna gjorda</h3>
      <p>Varje urtavla på sidorna 8–21 är renderad direkt ur HTML-prototypen i 2× upplösning
      (944 px per klocka) — ingen illustration är ritad separat. Bilden och koden visar därför
      alltid samma sak: ändras koden ska bilden renderas om.</p>
      <p style="margin-top:2mm"><b>Alla 18 bilder:</b> framsidan (s. 1 och 4) · vy 01–14 (s. 8–21) ·
      komplikationerna (s. 22) · anatomin (s. 23) · prototypens hero-sektion (s. 24).</p>
    </div>
    <div class="box">
      <h3>Vad som inte ska byggas</h3>
      <ul class="clean">
        <li>Ingen riktig data, inga API:er, ingen inloggning.</li>
        <li>Ingen karta, ingen GPS, ingen position — någonstans.</li>
        <li>Inga fler vyer än de fjorton. Konceptet är stängt.</li>
        <li>Inga externa typsnitt, ikoner eller CDN. Allt är inline.</li>
      </ul>
    </div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 3 REGLER -->
<div class="page">
  ${head('Regler','3')}
  <h2>Det som måste stämma<br>innan något annat.</h2>
  <p class="lead">De här punkterna är inte smaksak. De är sådant som gick fel på vägen och som en agent
  annars upprepar: en slogan i cirkeln, fel veckodag, en genomskinlig yta som blir grå.</p>
  <div class="two">
    <div class="box">
      <h3>Fasta värden</h3>
      <ul class="clean">
        <li><b>Dag:</b> lördag 7 november 2026 · <b>tid:</b> 09:41</li>
        <li><b>Dagens steg:</b> 6 842 av 8 000 = 86 % · 5,1 km</li>
        <li><b>Veckan:</b> 34 210 av 50 000 = 68 % · månaden 34 %</li>
        <li><b>Racet:</b> 42 180 mot 40 940 = 1 240 före</li>
        <li><b>Tusental</b> skrivs med hårt mellanslag: 6 842, 42 180</li>
      </ul>
    </div>
    <div class="box">
      <h3>Skalan</h3>
      <ul class="clean">
        <li><b>u</b> = klockans bredd / 107. Allt i en urtavla mäts i u.</li>
        <li>Sätt <b>--w</b> i runtime från klockans <b>clientWidth</b> (ResizeObserver) — aldrig låst till 320.</li>
        <li>Allt innehåll inom <b>78 %</b> av diametern. Padding 11–15 %, aldrig mindre.</li>
        <li>Rund skärm: inga hörn, inga rader som går ut i kanten.</li>
      </ul>
    </div>
  </div>
  <div class="stop">
    <h3>Fyra förbud</h3>
    <ul class="clean">
      <li>Ingen slogan i den streckade cirkeln på framsidan. Den visar <b>steg och mål</b>: 86 %, 6 842, AV 8 000 STEG.</li>
      <li>Aldrig fredag. Datumet är <b>LÖR 7 NOV</b> (lör 7 nov i alltid-på-läget).</li>
      <li>Inga kartor, positioner eller GPS i någon vy — bara siffror.</li>
      <li>Inga halvtransparenta glasytor för armband eller paneler. De blir gråa block i stället för ytor.</li>
    </ul>
  </div>
  <div class="box" style="margin-top:4mm">
    <h3>Färgerna betyder olika saker</h3>
    <div class="cols3" style="margin-top:0">
      <div><span class="k" style="color:var(--coral-deep)">Korall</span><p>Mål, rörelse, dagens steg, “du” i listan.</p></div>
      <div><span class="k" style="color:var(--lav)">Lavendel</span><p>Grupp, det andra laget, val bland grupper.</p></div>
      <div><span class="k" style="color:var(--sage)">Sage</span><p>Klart. Målet nått, vänner ute, completed-rad.</p></div>
    </div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 4 FRAMSIDAN -->
<div class="page">
  ${head('Framsidan','4')}
  <div class="kicker">Urtavlan</div>
  <h2>En liten värld,<br>med siffrorna i cirkeln.</h2>
  <p class="lead">Framsidan är inte en mörk appvy. Det är en handritad sommardag: gräddhimlen, solen med
  streckad gloria, två backar, en stig, en hund och tre personer som går åt höger. Ovanpå landskapet ligger
  en streckad vit cirkel — det är där stegen och målet bor.</p>
  <div class="wide" style="text-align:center;padding:4mm"><img src="img/front-face.png" alt="Framsidan" style="width:116mm;margin:0 auto"></div>
  <div class="two" style="margin-top:5mm">
    <div class="box">
      <h3>I cirkeln, uppifrån och ned</h3>
      <ul class="clean">
        <li><b>86 %</b> i DM Sans 800, korallbrun #C9622F</li>
        <li><b>6 842</b> i Outfit 700, bläck #2E2A3B — dagens steg</li>
        <li><b>AV 8 000 STEG</b> i DM Sans 800, spärrning .45 — målet</li>
        <li>En korallbåge visar 86 % av ringen, spåret är #EADFC8</li>
      </ul>
    </div>
    <div class="box">
      <h3>Runt om</h3>
      <ul class="clean">
        <li><b>step.by.step</b> längst upp, punkterna i korall</li>
        <li><b>09:41</b> i Outfit 600, 29 px på 200 px yta</li>
        <li><b>LÖR 7 NOV</b> under tiden, spärrning 1,5</li>
        <li>Solen uppe till höger, fåglar till vänster, gruppen nere till höger</li>
      </ul>
    </div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 5 KLOCKAN -->
<div class="page">
  ${head('Klockan','5')}
  <div class="kicker">Komponenten</div>
  <h2>Chassi, krona, glas — och skalan.</h2>
  <p class="lead">Samma klockkomponent används sexton gånger: en på framsidan, fjorton i vyerna och en
  bakom. Chassit är en conic-gradient i metall, kronan sitter till höger, och ett tunt glaslager ger
  reflexen överst.</p>
  <div class="two" style="grid-template-columns:78mm 1fr;align-items:center">
    <figure class="shot" style="margin:0"><img src="img/view-03.png" alt="Klockan"></figure>
    <div class="box cream">
      <h3>Mått som styr allt</h3>
      <ul class="clean">
        <li><b>Skärm:</b> inset 3,4 % i chassit, rund, overflow hidden</li>
        <li><b>u = --w × 0,00932 px</b> — allt inuti räknas i u</li>
        <li><b>--w:</b> 320 på framsidan, 224 bakom, 252 i korten</li>
        <li><b>Ringen:</b> inset 4,4 %, stroke 3, spår 2,4</li>
        <li><b>Glas:</b> två radial-gradienter + en sned reflex, opacity ,9</li>
        <li><b>Krona:</b> 9 px bred, 12 % hög, top 38 %, right −5 px</li>
      </ul>
    </div>
  </div>
  <div class="box" style="margin-top:5mm">
    <h3>Typografin i en urtavla</h3>
    <table class="t">
      <tr><th>Roll</th><th>Storlek</th><th>Vikt</th><th>Färg</th></tr>
      <tr><td>Stor siffra</td><td><code>19 u</code></td><td>700, spärrning -.05em</td><td>#f7f2ec</td></tr>
      <tr><td>Liten siffra</td><td><code>12,5 u</code></td><td>700</td><td>#f7f2ec</td></tr>
      <tr><td>Eyebrow</td><td><code>3,3 u</code></td><td>800, .19em versaler</td><td>#9c96ad</td></tr>
      <tr><td>Etikett</td><td><code>4,3 u</code></td><td>600</td><td>#9c96ad</td></tr>
      <tr><td>Chip</td><td><code>3,7 u</code></td><td>800</td><td>#ffa48c på korall 16 %</td></tr>
    </table>
  </div>
  <div class="code">
    <div class="lbl">Chassit och skärmen i CSS</div>
    <pre>.watch{<b>position</b>:relative; <b>aspect-ratio</b>:1; <i>--w</i>:300}
.case{<b>border-radius</b>:50%; <b>background</b>:conic-gradient(from 200deg,
  <i>--case-a</i>,<i>--case-b</i> 14%,<i>--case-a</i> 27%,#0b0a12 41%,
  <i>--case-a</i> 58%,<i>--case-b</i> 72%,<i>--case-a</i> 86%,#0b0a12 97%,<i>--case-a</i>)}
.screen{<b>position</b>:absolute; <b>inset</b>:3.4%; <b>border-radius</b>:50%; <b>overflow</b>:hidden;
  <i>--u</i>:calc(var(<i>--w</i>,300) * .00932px)}
.face{<b>position</b>:absolute; <b>inset</b>:0; <b>padding</b>:13% 15%; <b>display</b>:flex;
  <b>flex-direction</b>:column; <b>align-items</b>:center; <b>justify-content</b>:center}</pre>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 6 FÄRGER -->
<div class="page">
  ${head('Färger','6')}
  <div class="kicker">Tokens</div>
  <h2>Sidan är grädde. Klockan är natt.</h2>
  <p class="lead">Sidans ytor är ljusa och lugna; all dramatik ligger i klockans skärm. Mörkt armband är
  standard — ljust armband är en egen palett, inte en inversion.</p>
  <div class="sw">
    <div class="c"><div class="chip" style="background:#faf9f6"></div><div class="lbl"><b>Sida</b><code>#faf9f6</code></div></div>
    <div class="c"><div class="chip" style="background:#fffefc"></div><div class="lbl"><b>Kort</b><code>#fffefc</code></div></div>
    <div class="c"><div class="chip" style="background:#25243a"></div><div class="lbl"><b>Bläck</b><code>#25243a</code></div></div>
    <div class="c"><div class="chip" style="background:#e9e4da"></div><div class="lbl"><b>Linje</b><code>#e9e4da</code></div></div>
    <div class="c"><div class="chip" style="background:#ff704f"></div><div class="lbl"><b>Korall</b><code>#ff704f</code></div></div>
    <div class="c"><div class="chip" style="background:#ea6242"></div><div class="lbl"><b>Korall djup</b><code>#ea6242</code></div></div>
    <div class="c"><div class="chip" style="background:#7c60bb"></div><div class="lbl"><b>Lavendel</b><code>#7c60bb</code></div></div>
    <div class="c"><div class="chip" style="background:#7cae5c"></div><div class="lbl"><b>Sage</b><code>#7cae5c</code></div></div>
  </div>
  <div class="sw" style="margin-top:3.4mm">
    <div class="c"><div class="chip" style="background:#08070e"></div><div class="lbl"><b>Skärm</b><code>#08070e</code></div></div>
    <div class="c"><div class="chip" style="background:linear-gradient(140deg,#12101d,#08070e)"></div><div class="lbl"><b>Skärm topp</b><code>#12101d</code></div></div>
    <div class="c"><div class="chip" style="background:#1c1930"></div><div class="lbl"><b>Notis</b><code>#1c1930</code></div></div>
    <div class="c"><div class="chip" style="background:#f7f2ec"></div><div class="lbl"><b>Text på skärm</b><code>#f7f2ec</code></div></div>
    <div class="c"><div class="chip" style="background:#9c96ad"></div><div class="lbl"><b>Dämpad</b><code>#9c96ad</code></div></div>
    <div class="c"><div class="chip" style="background:#2b2739"></div><div class="lbl"><b>Linje skärm</b><code>#2b2739</code></div></div>
    <div class="c"><div class="chip" style="background:#c3e8a4"></div><div class="lbl"><b>Klar ljus</b><code>#c3e8a4</code></div></div>
    <div class="c"><div class="chip" style="background:#67a94c"></div><div class="lbl"><b>Klar mörk</b><code>#67a94c</code></div></div>
  </div>
  <div class="two" style="margin-top:6mm">
    <div class="box">
      <h3>Ljust armband</h3>
      <ul class="clean">
        <li>Skärm <b>#fbf6ee</b> → <b>#f4ece0</b>, text <b>#25243a</b>, dämpad <b>#8b8698</b></li>
        <li>Chip: korall 13 %, text <b>#c1522f</b></li>
        <li>Chassi: <b>#d8cfc2</b> mot <b>#8f8778</b></li>
      </ul>
    </div>
    <div class="box">
      <h3>Grönt betyder klart</h3>
      <ul class="clean">
        <li>Full ring = målet nått = <b>alltid grön</b></li>
        <li>Ring: #c3e8a4 → #8ed46c → #67a94c</li>
        <li>Chip och ikon: rgba(124,174,92,.2)</li>
        <li>Ingen korall och inget guld kvar i den vyn</li>
      </ul>
    </div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 7 TYPOGRAFI -->
<div class="page">
  ${head('Typografi','7')}
  <div class="kicker">Två typsnitt, två jobb</div>
  <h2>Outfit räknar.<br>DM Sans förklarar.</h2>
  <p class="lead">Samma par som i appen. Outfit för rubriker och alla siffror som betyder något;
  DM Sans för brödtext, etiketter och knappar. Siffror sätts med tabular-nums så de inte hoppar.</p>
  <div style="margin-top:6mm">
    <div class="type"><span class="sample" style="font-size:26pt">6 842 steg</span><span class="info">Outfit 700 · 19 u · -.05em<br>siffran är hjälten</span></div>
    <div class="type"><span class="sample" style="font-size:16pt">Målet nått</span><span class="info">Outfit 700 · 7 u<br>rubrik i urtavlan</span></div>
    <div class="type"><span class="sample" style="font-family:'DM Sans';font-weight:800;font-size:9pt;letter-spacing:.19em">IDAG · VECKAN · GRUPPEN</span><span class="info">DM Sans 800 · 3,3 u · .19em<br>eyebrow, versaler</span></div>
    <div class="type"><span class="sample" style="font-family:'DM Sans';font-weight:600;font-size:9.5pt;letter-spacing:0">steg · 5,1 km · mål 8 000</span><span class="info">DM Sans 600 · 4,3 u<br>etiketten under siffran</span></div>
    <div class="type"><span class="sample" style="font-family:'DM Sans';font-weight:700;font-size:13pt">Hur går det för oss?</span><span class="info">DM Sans 400–700 · 12–16 px<br>brödtext på sajten</span></div>
  </div>
  <div class="two" style="margin-top:6mm">
    <div class="box cream">
      <h3>Storlekar på sajten</h3>
      <table class="t">
        <tr><td>h1</td><td><code>clamp(40px,5.2vw,66px)</code></td></tr>
        <tr><td>h2</td><td><code>clamp(30px,3.6vw,44px)</code></td></tr>
        <tr><td>Kortrubrik</td><td><code>16.5px</code></td></tr>
        <tr><td>Korttext</td><td><code>12.5px / 1.55</code></td></tr>
        <tr><td>Meta</td><td><code>10.5px · .1em · versaler</code></td></tr>
      </table>
    </div>
    <div class="box">
      <h3>I urtavlan</h3>
      <table class="t">
        <tr><td>Stor siffra</td><td><code>calc(var(--u)*19)</code></td></tr>
        <tr><td>Liten siffra</td><td><code>calc(var(--u)*12.5)</code></td></tr>
        <tr><td>Etikett</td><td><code>calc(var(--u)*4.3)</code></td></tr>
        <tr><td>Eyebrow</td><td><code>calc(var(--u)*3.3)</code></td></tr>
        <tr><td>Chip</td><td><code>calc(var(--u)*3.7)</code></td></tr>
      </table>
    </div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

${viewPages}

<!-- 22 KOMPLIKATIONER -->
<div class="page">
  ${head('Komplikationer','22')}
  <div class="kicker">På en främmande urtavla</div>
  <h2>Tre storlekar räcker.</h2>
  <p class="lead">Samma data utan att appen öppnas: en ring för procenten, en rektangel för siffran och en
  liten ruta som öppnar appen. Rubrikerna ska ligga på samma höjd — därför en fast 112 px-ruta runt varje ikon.</p>
  <div class="wide"><img src="img/band.png" alt="Komplikationer"></div>
  <div class="cols3">
    <div><span class="k">Ringen</span><h3>Dagens mål i en blick</h3><p>112 px rund yta, ring med stroke 6–7 och procenten i mitten i Outfit 700, 20 px.</p></div>
    <div><span class="k">Siffran</span><h3>Pill med appikonen</h3><p>6 842 i Outfit 20 px, “steg idag” i DM Sans 10 px versaler. Yta #1c1930, radie 16 px.</p></div>
    <div><span class="k">Fotspåret</span><h3>Liten plats</h3><p>66 px appikon med radie 20 px. Ett tryck öppnar appen.</p></div>
  </div>
  <div class="box" style="margin-top:5mm">
    <h3>Regler för bandet</h3>
    <ul class="clean">
      <li>Ytan är mörk även när sidan är ljus — komplikationer ligger på klockans urtavla.</li>
      <li>Varje ikon i en <b>.comp-top</b> som är 112 px hög och centrerar innehållet.</li>
      <li>Etiketter i DM Sans 800, 11 px, .13em, versaler, dämpad färg.</li>
    </ul>
  </div>
  <div class="code">
    <div class="lbl">De tre komplikationerna i CSS</div>
    <pre>.comp-top{<b>height</b>:112px; <b>display</b>:grid; <b>place-items</b>:center}
.ring-comp{<b>width</b>:112px; <b>height</b>:112px; <b>border-radius</b>:50%;
  <b>background</b>:conic-gradient(<i>#ff704f</i> 86%, <i>rgba(255,255,255,.14)</i> 0)}
.pill-comp{<b>display</b>:flex; <b>gap</b>:8px; <b>align-items</b>:center;
  <b>padding</b>:8px 14px; <b>border-radius</b>:16px; <b>background</b>:<i>#1c1930</i>}
.foot-comp{<b>width</b>:66px; <b>height</b>:66px; <b>border-radius</b>:20px; <b>overflow</b>:hidden}</pre>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 23 ANATOMI -->
<div class="page">
  ${head('Anatomi','23')}
  <div class="kicker">Mått och typografi</div>
  <h2>Ritat för 41 millimeter.</h2>
  <p class="lead">Klockan är 466 × 466 px (45 mm) eller 396 × 484 px (41 mm). Allt nedan utgår från den
  runda ytan — säker zon, ringens betydelse, träffytan och etiketternas storlek.</p>
  <div class="wide"><img src="img/anatomy.png" alt="Anatomi"></div>
  <div class="two">
    <div class="box"><h3><span style="color:var(--coral-deep)">1</span> Säker zon · 78 %</h3><p>Den streckade cirkeln. Utanför den försvinner text under klockans kant och armbandets fäste.</p></div>
    <div class="box"><h3><span style="color:var(--lav)">2</span> Ringen betyder dagens mål</h3><p>Procenten bor i ringen, siffran i mitten är alltid steg. Aldrig två saker som betyder samma.</p></div>
    <div class="box"><h3><span style="color:var(--sage)">3</span> Träffyta 48 × 48 dp</h3><p>Centrerad i nederkant där tummen landar — nåbar med handskar, utan att titta.</p></div>
    <div class="box"><h3><span style="color:var(--gold)">4</span> Etiketter 12 px, 0,18 em</h3><p>Versaler med luft mellan bokstäverna och minst 4,5:1 i kontrast, även i ljust armband.</p></div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 24 PRINCIPER -->
<div class="page">
  ${head('Principer','24')}
  <div class="kicker">Sex regler</div>
  <h2>Lugnt, kort och<br>utan studs.</h2>
  <p class="lead">Samma ton som i appen, men anpassad efter en handled: kortare väg till siffran och
  färre saker som rör sig.</p>
  <div class="two">
    <div class="box"><h3>Cirkeln först</h3><p>Allt innehåll håller sig inom 78 % av diametern. Inget klipps av rundningen, inget hamnar i kanten.</p></div>
    <div class="box"><h3>En tumme, en handling</h3><p>Högst en knapp per vy, alltid centrerad och minst 48 dp. Svep för nästa vy, tryck för att peppra.</p></div>
    <div class="box"><h3>Siffran är hjälten</h3><p>Steg och procent i Outfit 700, 40–64 px. Etiketter i DM Sans 12 px med spärrning — aldrig mindre.</p></div>
    <div class="box"><h3>Sant svart</h3><p>Bakgrund #08070E på OLED. Pixlarna släcks, batteriet räcker längre och klockans kant försvinner i mörkret.</p></div>
    <div class="box"><h3>En accent i taget</h3><p>Korall betyder mål och rörelse. Lavendel betyder grupp. Aldrig två accenter som slåss i samma vy.</p></div>
    <div class="box"><h3>Stillhet och haptik</h3><p>Ingen studsande animation. En ring som fylls, en siffra som räknas upp. Bekräftelsen känns i handleden.</p></div>
  </div>
  <div class="two" style="margin-top:7mm;grid-template-columns:98mm 1fr;gap:8mm;align-items:center">
    <figure class="shot" style="margin:0;padding:2.5mm">
      <img src="img/front-stage.png" alt="Prototypens hero-sektion i webbläsaren">
    </figure>
    <div>
      <h3 style="font-size:11pt;letter-spacing:-.02em;margin-bottom:2.4mm">Sidan och klockan är samma sak</h3>
      <p style="font-size:8.4pt;color:var(--muted);line-height:1.62">
      Hero-sektionen i prototypen: den illustrerade urtavlan med stegmärket i cirkeln, den mörka
      appvyn bredvid och armbanden under. Det är samma HTML och samma SVG som ligger bakom varje
      bild i den här specen — ingenting är ritat separat.
      </p>
      <p style="font-size:8.4pt;color:var(--muted);line-height:1.62;margin-top:2.6mm">
      <b style="color:var(--ink)">Alla 18 bilder:</b> framsidan (s. 1 och 4) · vy 01–14 (s. 8–21) ·
      komplikationerna (s. 22) · anatomin (s. 23) · den här översikten (s. 24).
      </p>
    </div>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 25 ARKITEKTUR -->
<div class="page">
  ${head('Arkitektur','25')}
  <div class="kicker">Klockan är en spegel</div>
  <h2>Telefonen äger.<br>Klockan visar.</h2>
  <p class="lead">Ingen inloggning på handleden. Telefonen äger kontot, grupperna och historiken; klockan
  visar läget och skickar pepp. Mellan dem går bara siffror.</p>
  <div class="two">
    <div class="box">
      <h3>Telefonen · äger allt</h3>
      <ul class="clean">
        <li>Konto, inloggning och grupper</li>
        <li>HealthKit och Health Connect</li>
        <li>Skapa, bjud in och para ihop</li>
        <li>Bluetooth-gruppen nära dig</li>
      </ul>
    </div>
    <div class="box">
      <h3>Klockan · visar och peppar</h3>
      <ul class="clean">
        <li>Dagens steg, mål och procent</li>
        <li>Topplista och race</li>
        <li>Pepp med haptik</li>
        <li>Ingen inloggning, ingen GPS</li>
      </ul>
    </div>
  </div>
  <div class="box cream" style="margin-top:6mm">
    <h3>Om det skulle byggas</h3>
    <ul class="clean">
      <li><b>Wear OS 5:</b> Compose for Wear OS för vyerna, Tiles för topplistan, Complications API för stegringen, Health Services för steg.</li>
      <li><b>watchOS 11:</b> SwiftUI för samma fjorton vyer, WidgetKit för komplikationer, HealthKit för steg, WatchConnectivity för siffror.</li>
      <li><b>Bryggan:</b> Capacitor-appen kan inte köras på en klocka. En klockmodul läser samma grupp-API via telefonen — och äger aldrig kontot själv.</li>
    </ul>
  </div>
  <div class="stop">
    <h3>Det här är ett koncept</h3>
    <p style="font-size:8.4pt;color:var(--muted)">Inget är byggt eller testat på en riktig klocka, och det är inte en del av den
    publicerade appen. Alla siffror, namn och färger är lånade från step.by.step — inga nya data.</p>
  </div>
  <div class="foot"><span>step.by.step · designspec</span><span>nrnworld.one</span></div>
</div>

<!-- 26 CHECKLISTA -->
<div class="page">
  ${head('Checklista','26')}
  <div class="kicker">Innan du är klar</div>
  <h2>Tjugofyra saker<br>att bocka av.</h2>
  <p class="lead">Kör igenom listan efter bygget. Varje rad är något som syns direkt om det är fel —
  eller som en agent annars hoppar över.</p>
  <div class="check">
    <div><span class="bx"></span><span>En <b>h1</b> på sidan: “Samma steg. Mycket mindre skärm.”</span></div>
    <div><span class="bx"></span><span>Framsidan visar sol, två fåglar, backar, stig, hund och tre personer</span></div>
    <div><span class="bx"></span><span>Cirkeln visar <b>86 %</b>, <b>6 842</b> och <b>AV 8 000 STEG</b> — ingen slogan</span></div>
    <div><span class="bx"></span><span>Datumet är <b>LÖR 7 NOV</b> (och <b>lör 7 nov</b> i alltid-på)</span></div>
    <div><span class="bx"></span><span>14 klockkort i 5 flöden, numrerade 01–14, i rätt ordning</span></div>
    <div><span class="bx"></span><span>Ett lavendel-asideskort i flöde 01</span></div>
    <div><span class="bx"></span><span>Chassi med conic-gradient, krona till höger, glans överst</span></div>
    <div><span class="bx"></span><span>Allt innehåll inom säker zon — inget klipps av rundningen</span></div>
    <div><span class="bx"></span><span><b>--w</b> sätts i runtime från klockans bredd (ResizeObserver)</span></div>
    <div><span class="bx"></span><span>Ringen animeras 0 → 86 % på 1,7 s och stannar</span></div>
    <div><span class="bx"></span><span>Vy 12 är <b>grön</b>: ring, chip, trofé och inre glöd — ingen korall</span></div>
    <div><span class="bx"></span><span>6 842 / 8 000 / 86 % / 5,1 km stämmer i alla vyer</span></div>
    <div><span class="bx"></span><span>Veckan: 34 210 av 50 000 = 68 %, idag (L) i korall</span></div>
    <div><span class="bx"></span><span>Race och analyskarta visar samma tal: 42 180 mot 40 940</span></div>
    <div><span class="bx"></span><span>All text på svenska, tusental med hårt mellanslag</span></div>
    <div><span class="bx"></span><span>Temaknappen växlar armband och minns valet</span></div>
    <div><span class="bx"></span><span>Pepp-knappen: 8 → 9, hjärtat poppar, “Pepp skickad ✓” i 1,4 s</span></div>
    <div><span class="bx"></span><span>Inblendning fungerar och stängs av vid reduced-motion</span></div>
    <div><span class="bx"></span><span>Inga konsolfel, inga 404:or, inga externa requests</span></div>
    <div><span class="bx"></span><span>Ingen horisontell scroll vid 320, 390, 768, 1024, 1440 px</span></div>
    <div><span class="bx"></span><span>Kortens metarad ligger på samma höjd inom varje rad</span></div>
    <div><span class="bx"></span><span>Komplikationernas rubriker ligger på samma höjd (112 px)</span></div>
    <div><span class="bx"></span><span>Varje urtavla går att läsa på en halv sekund</span></div>
    <div><span class="bx"></span><span>Alla 18 bilder i PDF:en är byggda ur samma <code>index.html</code></span></div>
  </div>
  <div class="two" style="margin-top:6mm">
    <div class="box">
      <h3>Så här verifierar du</h3>
      <ul class="clean">
        <li>Öppna sidan i en webbläsare <b>med</b> och en <b>utan</b> <code>color-mix</code> — ytorna ska se likadana ut.</li>
        <li>Dra fönstret från 1440 till 320 px. Ingenting ska hoppa eller klippas.</li>
        <li>Tryck pepp tre gånger snabbt: räknaren får bara gå upp ett steg per 1,4 s.</li>
        <li>Växla armband två gånger och ladda om — valet ska ligga kvar.</li>
      </ul>
    </div>
    <div class="box">
      <h3>Vad som inte ska byggas</h3>
      <ul class="clean">
        <li>Ingen riktig data, inga API:er, ingen inloggning.</li>
        <li>Ingen karta, ingen GPS, ingen position — någonstans.</li>
        <li>Inga fler vyer än de fjorton. Konceptet är stängt.</li>
        <li>Inga externa typsnitt, ikoner eller CDN. Allt är inline.</li>
      </ul>
    </div>
  </div>
  <div class="box cream" style="margin-top:5mm">
    <h3>Ändringslogg</h3>
    <table class="t">
      <tr><th>Version</th><th>Datum</th><th>Ändring</th></tr>
      <tr><td><b>1.2</b></td><td>2026-11-08</td><td>Prototypens hero-bild inlagd (s. 24). Bildförteckning: alla 18 bilder redovisade på s. 2 och s. 24.</td></tr>
      <tr><td><b>1.1</b></td><td>2026-11-08</td><td>Målet nått lyser grönt (gradSage, ring, chip, trofé). Regel: grönt = klart. PDF med alla vyer.</td></tr>
      <tr><td><b>1.0</b></td><td>2026-11-08</td><td>Första versionen: 14 vyer, komplikationer, principer, anatomi, arkitektur.</td></tr>
    </table>
  </div>
  <div class="foot"><span>step.by.step · på handleden</span><span>nrnworld.one</span></div>
</div>

</body></html>`;

fs.writeFileSync(HTML,html,'utf8');
console.log('spec.html:',(html.length/1024).toFixed(1)+' kB');

(async()=>{
  const b=await puppeteer.launch({executablePath:CHROME,headless:true,
    args:['--no-sandbox','--disable-gpu','--font-render-hinting=none','--force-color-profile=srgb'],
    env:{...process.env,LD_LIBRARY_PATH:LIB}});
  const p=await b.newPage();
  await p.goto('http://127.0.0.1:5173/pdf/spec.html',{waitUntil:'networkidle0'});
  await p.evaluate(()=>document.fonts.ready);
  await new Promise(r=>setTimeout(r,1200));
  await p.pdf({path:PDF,format:'A4',printBackground:true,preferCSSPageSize:true,
    margin:{top:0,right:0,bottom:0,left:0}});
  console.log('pdf klar:',PDF, (fs.statSync(PDF).size/1048576).toFixed(2)+' MB');
  await b.close();
})();
