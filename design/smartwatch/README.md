# step.by.step på handleden

Ett designkoncept: hur **step.by.step** hade kunnat se ut på en smartwatch (Wear OS och watchOS).
Sidan är en fristående prototyp i ren HTML, CSS och SVG — ingen build, inga beroenden, inga nya data.

> **Ska sidan byggas om från noll?** Använd [`step-by-step-smartwatch-spec.pdf`](step-by-step-smartwatch-spec.pdf) —
> 26 A4-sidor där varje skärm finns som bild plus tokens, mått och texter. `index.html` är facit;
> PDF:en renderas ur det i två steg — `node pdf/shots.cjs` (bilderna) och `node pdf/build.cjs` (sidorna).
>
> Bilderna finns också för sig: `step-by-step-smartwatch-bilder.zip` (18 PNG, 2×).
>
> Specen går att **ladda ner från sidan**: knappen `Spec .pdf` i toppbaren, kortet sist i
> *Om det skulle byggas*, eller länken i footern (`download` på PDF:en).

```bash
# öppna filen direkt, eller servera mappen
cd design/smartwatch
python3 -m http.server 5173
# → http://localhost:5173
```

## Framsidan

Urtavlan är en liten illustrerad värld: sol, backar, en stig och gruppen som går.
Den streckade cirkeln är klockans viktigaste yta och visar tre saker på en gång:

| I cirkeln | Betyder |
| :--- | :--- |
| **6 842** | steg idag, hämtade från telefonens HealthKit/Health Connect |
| **AV 8 000 STEG** | dagens mål |
| **86 %** + den korallfärgade bågen | hur långt man kommit mot målet |

Tiden ligger kvar mitt på urtavlan, datumet under. Inget mer — en klocka ska gå att läsa på en halv sekund.

## Sidan innehåller

1. **Framsidan** i stort format, med dagens steg som mörk app-vy bredvid.
2. **Fjorton vyer** i fem flöden — första gången (parkoppla, välj grupp), under dagen (steg, vecka, mål),
   gruppen (topplista, pepp, live), nära och race (bara här, race, analyskarta) och firande och vila
   (målet nått, alltid på, inställningar).
3. **Komplikationer** — samma data på en främmande urtavla: ring, siffra och fotspår.
4. **Designprinciper** — säker zon, 48 dp träffyta, typografi, sant svart, en accent, haptik.
5. **Mått och typografi** — anatomi för den runda skärmen.
6. **Arkitektur** — telefonen äger kontot, klockan visar och peppar.
7. **Om det skulle byggas** — Compose for Wear OS, SwiftUI/WidgetKit och bryggan mot Capacitor-appen.

## Designval

| Del | Val |
| :--- | :--- |
| Färg | Korall `#FF704F`, lavendel `#7C60BB`, sage `#7CAE5C`, grädde `#FBF3E4`, bläck `#2E2A3B` |
| Typografi | Outfit för siffror och rubriker, DM Sans för etiketter — samma som appen |
| Ytor | Sant svart `#08070E` i appvyerna, illustrerad grädde på framsidan |
| Skala | Allt i urtavlorna räknas från `--w` (klockans bredd) så vyerna skalar utan att klippas |
| Rörelse | En ring som fylls, en siffra som räknas upp. Ingenting studsar |

Sidan har mörkt och ljust armband (knappen uppe till höger), fungerar ned till 320 px och respekterar
`prefers-reduced-motion`. Tryck på **Peppa tillbaka** i notisen — den fungerar.

## Bra att veta

Konceptet är ritat i webbläsaren. Det är inte byggt, inte testat på en riktig klocka och inte en del av
den publicerade appen. Alla siffror, namn och färger är lånade från step.by.step.
