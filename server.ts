import express from 'express';
import { createClient } from '@supabase/supabase-js';
import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, mkdirSync, renameSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { countActivities, recordWalk, type Walk } from './activity';

const app = express();
const authClient = process.env.SUPABASE_URL && process.env.SUPABASE_ANON_KEY
  ? createClient(process.env.SUPABASE_URL, process.env.SUPABASE_ANON_KEY, {auth:{persistSession:false}}) : null;

// Only the installed app and explicitly configured web origins may call the API cross-origin.
app.use('/api',(req,res,next)=>{
  const allowed = ['capacitor://localhost','https://localhost',...(process.env.APP_ORIGINS||'').split(',').map(s=>s.trim()).filter(Boolean)];
  const origin=req.headers.origin;
  if(origin&&allowed.includes(origin)){
    res.setHeader('Access-Control-Allow-Origin',origin);
    res.setHeader('Vary','Origin');
    res.setHeader('Access-Control-Allow-Headers','Authorization, Content-Type');
    res.setHeader('Access-Control-Allow-Methods','GET, POST, PATCH, DELETE, OPTIONS');
  }
  if(req.method==='OPTIONS')return res.sendStatus(origin&&allowed.includes(origin)?204:403);
  next();
});
app.use(express.json({ limit: '20kb' }));
const DATA = join(process.cwd(), 'data', 'steps.json');
type GoalType = 'day' | 'week' | 'weekend' | 'month' | 'year';
type Person = { id: string; token: string; name: string; distances: Record<string, number>; activities?: Walk[]; lastMovementAt?: number; oauthSubject?: string; };
type GoalScope = 'each' | 'together';
type Group = { mode?: 'connected'; id: string; code: string; pairCode?: string; name: string; ownerId: string; members: string[]; memberNames?: Record<string, string>; banned: string[]; bannedNames?: string[]; goals: { id: string; type: GoalType; target: number; scope?: GoalScope }[]; partners?: string[]; likes: Record<string, string[]>; createdAt: number };
type DB = { people: Person[]; groups: Group[] };
// A goal is either for each walker ('each') or for the whole group together ('together').
const goalKey = (g: { type: GoalType; scope?: GoalScope }) => `${g.type}:${g.scope === 'each' ? 'each' : 'together'}`;
// Two teams pair up with a short code. Only their together goals are shared, never members or personal goals.
const PAIR_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const nextPairCode = () => { for (;;) { let out = ''; const bytes = randomBytes(6); for (let i = 0; i < 6; i++) out += PAIR_ALPHABET[bytes[i] % PAIR_ALPHABET.length]; if (!db.groups.some(g => g.pairCode === out)) return out; } };
const METERS_PER_STEP = 0.75;
let db: DB = { people: [], groups: [] };
// With a service key the data lives in Supabase and survives restarts; only the server holds this key, never the browser.
const store = process.env.SUPABASE_URL && process.env.SUPABASE_SERVICE_KEY
  ? createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY, {auth:{persistSession:false}}) : null;
// A failed read must stop the server, otherwise the next save would overwrite every account and group with an empty database.
if (store) {
  const { data, error } = await store.from('app_state').select('data').eq('id', 1).maybeSingle();
  if (error) { console.error('Kunde inte läsa databasen från Supabase. Servern startar inte för att data inte ska skrivas över.', error.message); process.exit(1); }
  if (data) db = data.data as DB;
} else if (existsSync(DATA)) {
  try { db = JSON.parse(readFileSync(DATA, 'utf8')); } catch (err) { console.error(`${DATA} kunde inte läsas. Servern startar inte för att data inte ska skrivas över.`, err); process.exit(1); }
}
// Saves are coalesced: one write at a time, and a change made mid-write triggers another write right after.
// shortcut: the whole database is one row, fine for a small user base, move to one row per group when it grows.
let pending: Promise<void> | null = null; let dirty = false;
const writeRemote = async () => {
  while (dirty) {
    dirty = false;
    const { error } = await store!.from('app_state').upsert({ id: 1, data: db, updated_at: new Date().toISOString() });
    if (error) { dirty = true; console.error('Kunde inte spara till Supabase, försöker igen:', error.message); await new Promise(r => setTimeout(r, 2000)); }
  }
};
// Write to a temp file first, so a crash mid-write never leaves a half-written database behind.
const save = () => {
  if (store) { dirty = true; pending ??= writeRemote().finally(() => { pending = null; }); return; }
  mkdirSync(dirname(DATA), { recursive: true }); const tmp = `${DATA}.tmp`; writeFileSync(tmp, JSON.stringify(db)); renameSync(tmp, DATA);
};
// Render stops the server with SIGTERM on every deploy; finish the last write first.
for (const sig of ['SIGTERM', 'SIGINT'] as const) process.on(sig, () => { void (pending ?? Promise.resolve()).finally(() => process.exit(0)); });
// Migrate old records: the server must never retain historic GPS coordinates.
if (db.people.some(p => 'lastFix' in p)) {
  for (const p of db.people) delete (p as Person & {lastFix?:unknown}).lastFix;
  save();
}
const id = () => randomBytes(12).toString('hex');
// JWTs are verified server-side for every connected request. Never trust a client-supplied user id.
app.use('/api', async (req, res, next) => {
  const bearer = req.headers.authorization?.replace(/^Bearer /, '');
  if (!bearer || !bearer.includes('.')) return next(); // Existing anonymous prototype identities only.
  if (!authClient) return fail(res, 503, 'Inloggning är inte konfigurerad på servern.');
  try {
    const { data, error } = await authClient.auth.getUser(bearer);
    if (error || !data.user || !['email','google','apple'].includes(data.user.app_metadata?.provider)) return fail(res, 401, 'Logga in med e-post, Google eller Apple.');
    let p = db.people.find(item => item.oauthSubject === data.user.id);
    if (!p) { p = makePerson('Vandrare'); p.oauthSubject = data.user.id; save(); }
    (req as express.Request & { verifiedPerson?: Person }).verifiedPerson = p;
    next();
  } catch { return fail(res, 401, 'Inloggningen kunde inte verifieras.'); }
});
const dateKey = (time = Date.now()) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Stockholm', year: 'numeric', month: '2-digit', day: '2-digit' }).format(time);
// Same period rules as the app, so a partner card never disagrees with the group's own goal card.
const metersFor = (distances: Record<string, number>, period: GoalType) => {
  const today = dateKey();
  const weekday = (new Date(`${today}T12:00:00Z`).getUTCDay() + 6) % 7;
  const now = new Date(`${today}T12:00:00Z`).getTime();
  return Object.entries(distances || {}).reduce((sum, [day, meters]) => {
    const at = new Date(`${day}T12:00:00Z`);
    if (Number.isNaN(at.getTime())) return sum;
    const daysAgo = Math.round((now - at.getTime()) / 86400000);
    let valid = false;
    if (period === 'day') valid = day === today;
    if (period === 'week') valid = daysAgo >= 0 && daysAgo < weekday + 1;
    if (period === 'weekend') valid = daysAgo >= 0 && daysAgo < weekday + 1 && (at.getUTCDay() === 0 || at.getUTCDay() === 6);
    if (period === 'month') valid = day.slice(0, 7) === today.slice(0, 7);
    if (period === 'year') valid = day.slice(0, 4) === today.slice(0, 4);
    return valid ? sum + (Number(meters) || 0) : sum;
  }, 0);
};
const groupSteps = (g: Group, period: GoalType) => Math.floor(g.members.reduce((sum, mid) => sum + metersFor(db.people.find(p => p.id === mid)?.distances || {}, period), 0) / METERS_PER_STEP);
// Only the shared picture leaves a group: its name, size and together goals. No names, no personal goals.
const partnerSummary = (g: Group) => ({ id: g.id, name: g.name, memberCount: g.members.length,
  goals: g.goals.filter(goal => goalKey(goal) !== `${goal.type}:each`).map(goal => ({ type: goal.type, target: goal.target, steps: groupSteps(g, goal.type) })),
  steps: { day: groupSteps(g, 'day'), week: groupSteps(g, 'week'), month: groupSteps(g, 'month') } });
const person = (req: express.Request) => (req as express.Request & {verifiedPerson?:Person}).verifiedPerson || db.people.find(p => !p.oauthSubject && (p.token === req.body?.token || p.token === req.headers.authorization?.replace(/^Bearer /, '')));
const fail = (res: express.Response, status: number, message: string) => res.status(status).json({ error: message });
// Simple in-memory limit per client and route. Login-related requests and profile creation are the main brute-force and spam targets.
const hits = new Map<string, number[]>();
const limit = (max: number, windowMs: number): express.RequestHandler => (req, res, next) => {
  const now = Date.now();
  if (hits.size > 10000) hits.clear();
  const key = `${req.ip}|${req.path}`;
  const recent = (hits.get(key) || []).filter(at => now - at < windowMs);
  if (recent.length >= max) return fail(res, 429, 'För många försök. Vänta en stund och försök igen.');
  recent.push(now);
  hits.set(key, recent);
  next();
};
const view = (g: Group, viewer: Person) => ({ id: g.id, viewerId: viewer.id, name: g.name, code: g.code, isAdmin: g.ownerId === viewer.id,
  partners: (g.partners || []).map(pid => db.groups.find(x => x.id === pid)).filter((x): x is Group => !!x).map(partnerSummary),
  ...(g.ownerId === viewer.id ? { pairCode: g.pairCode || null } : {}),
  goals: g.goals, members: g.members.map(mid => { const p = db.people.find(x => x.id === mid)!; return { id: p.id, name: g.memberNames?.[p.id] || p.name, distances: p.distances, activityCounts: countActivities(p.activities || []), isAdmin: p.id === g.ownerId, liked: (g.likes[`${dateKey()}:${p.id}`] || []).includes(viewer.id), likes: (g.likes[`${dateKey()}:${p.id}`] || []).length, active: !!p.lastMovementAt && Date.now() - p.lastMovementAt < 90000 }; }) });
app.get('/api/state', (req, res) => { const p = person(req); if (!p) return fail(res, 401, 'Sessionen hittades inte. Ange ett namn igen.'); res.json({ me: { id: p.id, name: p.name, distances: p.distances }, groups: db.groups.filter(g => g.members.includes(p.id)).map(g => view(g,p)) }); });
const validName = (input: unknown) => typeof input === 'string' ? input.trim().replace(/\s+/g, ' ') : '';
const makePerson = (name: string): Person => { const p: Person = { id: id(), token: id() + id(), name, distances: {} }; db.people.push(p); return p; };
// Removes a person, every membership and every like on them. Used by account deletion and the legacy cleanup below.
const removePerson = (p: Person) => {
  for (const g of db.groups) {
    if (!g.members.includes(p.id)) continue;
    g.members = g.members.filter(mid => mid !== p.id);
    if (g.memberNames) delete g.memberNames[p.id];
    for (const key of Object.keys(g.likes)) {
      if (key.endsWith(`:${p.id}`)) delete g.likes[key];
      else g.likes[key] = g.likes[key].filter(mid => mid !== p.id);
    }
    if (g.ownerId === p.id) g.ownerId = g.members[0] || '';
  }
  db.groups = db.groups.filter(g => g.members.length > 0);
  db.people = db.people.filter(x => x.id !== p.id);
  save();
};
// Nickname accounts (smeknamn + kod) were removed. Their people are deleted once, so no old login can remain.
for (const p of db.people.filter(item => 'account' in item)) removePerson(p);
// Deleting the account erases the person, every membership and every like in one go.
app.delete('/api/accounts', (req, res) => {
  const p = person(req);
  if (!p) return fail(res, 401, 'Sessionen hittades inte.');
  removePerson(p);
  res.json({ deleted: true });
});
app.post('/api/profile', limit(20, 60 * 60 * 1000), (req, res) => { const name = validName(req.body.name); if (name.length < 3 || name.length > 32) return fail(res, 400, 'Namnet måste vara 3 till 32 tecken.'); const p = makePerson(name); save(); res.json({ token: p.token }); });
app.get('/api/invite/:code', (req, res) => { const g = db.groups.find(g => g.code === req.params.code); if (!g) return fail(res, 404, 'Länken är ogiltig.'); res.json({ id: g.id, name: g.name, members: g.members.length }); });
app.post('/api/groups/:id/pair-code', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); if (!p || !g || g.ownerId !== p.id) return fail(res, 403, 'Bara gruppens skapare kan hämta koden.'); if (!g.pairCode) { g.pairCode = nextPairCode(); save(); } res.json({ code: g.pairCode }); });
app.get('/api/pair/:code', (req, res) => { const p = person(req); if (!p) return fail(res, 401, 'Sessionen hittades inte. Ange ett namn igen.'); const code = String(req.params.code || '').trim().toUpperCase(); const g = db.groups.find(g => !!g.pairCode && g.pairCode === code); if (!g) return fail(res, 404, 'Koden hittades inte. Be det andra teamet om en ny kod.'); res.json({ id: g.id, name: g.name, memberCount: g.members.length }); });
app.post('/api/groups/:id/partners', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); if (!p || !g || g.ownerId !== p.id) return fail(res, 403, 'Bara gruppens skapare kan para ihop grupper.'); const code = String(req.body.code || '').trim().toUpperCase(); if (!code) return fail(res, 400, 'Ange koden från det andra teamet.'); const other = db.groups.find(x => !!x.pairCode && x.pairCode === code); if (!other) return fail(res, 404, 'Koden hittades inte. Be det andra teamet om en ny kod.'); if (other.id === g.id) return fail(res, 400, 'Det är er egen kod. Använd koden från det andra teamet.'); g.partners ||= []; other.partners ||= []; if (g.partners.includes(other.id)) return fail(res, 409, 'De grupperna är redan ihopkopplade.'); if (g.partners.length >= 5) return fail(res, 409, 'En grupp kan vara ihopkopplad med högst fem grupper.'); g.partners.push(other.id); if (!other.partners.includes(g.id)) other.partners.push(g.id); save(); res.json(view(g, p)); });
app.delete('/api/groups/:id/partners/:partnerId', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); if (!p || !g || g.ownerId !== p.id) return fail(res, 403, 'Bara gruppens skapare kan koppla bort grupper.'); g.partners = (g.partners || []).filter(pid => pid !== req.params.partnerId); const other = db.groups.find(x => x.id === req.params.partnerId); if (other) other.partners = (other.partners || []).filter(pid => pid !== g.id); save(); res.json(view(g, p)); });
app.post('/api/groups', (req, res) => {
  const name = validName(req.body.name);
  const memberName = validName(req.body.memberName);
  if (name.length < 3 || name.length > 50) return fail(res, 400, 'Gruppnamnet måste vara 3 till 50 tecken.');
  if (req.body.memberName === undefined || req.body.goals === undefined) return fail(res, 400, 'Du använder ett gammalt formulär. Ladda om sidan för att välja namn och mål.');
  if (memberName.length < 3 || memberName.length > 32) return fail(res, 400, 'Välj ett namn i gruppen på 3 till 32 tecken.');
  const requested = req.body.goals;
  if (!Array.isArray(requested) || requested.length < 1 || requested.length > 10 || new Set(requested.map(g => goalKey(g))).size !== requested.length || requested.some(g => !['day','week','weekend','month','year'].includes(g?.type) || !Number.isInteger(g.target) || g.target < 100 || g.target > 10000000 || (g.scope !== undefined && !['each','together'].includes(g.scope)))) return fail(res, 400, 'Välj minst ett giltigt gruppmål.');
  let p = person(req);
  let recoveredToken: string | undefined;
  if (!p) { p = makePerson(memberName); recoveredToken = p.token; }
  const g: Group = { ...(p.oauthSubject ? {mode:'connected' as const} : {}), id: id(), code: randomBytes(8).toString('hex'), pairCode: nextPairCode(), name, ownerId: p.id, members: [p.id], memberNames: { [p.id]: memberName }, banned: [], goals: requested.map((goal: { type: GoalType; target: number; scope?: GoalScope }) => ({ id: id(), type: goal.type, target: goal.target, ...(goal.scope ? { scope: goal.scope } : {}) })), likes: {}, createdAt: Date.now() };
  db.groups.push(g); save(); res.json({ ...view(g,p), ...(recoveredToken ? { token: recoveredToken } : {}) });
});
app.post('/api/groups/:id/join', (req, res) => {
  const g = db.groups.find(g => g.id === req.params.id && g.code === req.body.code);
  if (!g) return fail(res, 404, 'Inbjudan hittades inte.');
  if (g.mode === 'connected' && !(req as express.Request & {verifiedPerson?:Person}).verifiedPerson) return fail(res, 401, 'Logga in för att gå med i den här gruppen.');
  if (g.mode !== 'connected' && (req as express.Request & {verifiedPerson?:Person}).verifiedPerson) return fail(res, 403, 'Den här gruppen använder det äldre gruppsystemet.');
  const memberName = validName(req.body.memberName);
  if (memberName.length < 3 || memberName.length > 32) return fail(res, 400, 'Välj ett namn i gruppen på 3 till 32 tecken.');
  let p = person(req);
  if (p && g.banned.includes(p.id)) return fail(res, 403, 'Du kan inte gå med i den här gruppen igen.');
  // A new anonymous session gets a new id, so the banned display name is blocked as well.
  if (g.bannedNames?.includes(memberName.toLowerCase())) return fail(res, 403, 'Du kan inte gå med i den här gruppen igen.');
  let recoveredToken: string | undefined;
  if (!p) { p = makePerson(memberName); recoveredToken = p.token; }
  if (!g.members.includes(p.id)) { g.members.push(p.id); g.memberNames ||= {}; g.memberNames[p.id] = memberName; }
  save(); res.json({ ...view(g,p), ...(recoveredToken ? { token: recoveredToken } : {}) });
});
app.patch('/api/groups/:id/name', (req, res) => {
  const p = person(req);
  const g = db.groups.find(g => g.id === req.params.id);
  if (!p || !g || !g.members.includes(p.id)) return fail(res, 403, 'Du tillhör inte den här gruppen.');
  const memberName = validName(req.body.memberName);
  if (memberName.length < 3 || memberName.length > 32) return fail(res, 400, 'Välj ett namn i gruppen på 3 till 32 tecken.');
  g.memberNames ||= {};
  g.memberNames[p.id] = memberName;
  save(); res.json(view(g,p));
});
app.post('/api/groups/:id/goals', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); if (!p || !g || g.ownerId !== p.id) return fail(res, 403, 'Endast gruppens skapare kan ändra mål.'); const type = req.body.type as GoalType; const target = Number(req.body.target); const scope: GoalScope | undefined = req.body.scope === 'each' ? 'each' : req.body.scope === 'together' ? 'together' : undefined; if (!['day','week','weekend','month','year'].includes(type) || !Number.isInteger(target) || target < 100 || target > 10000000) return fail(res,400,'Ogiltigt mål.'); if (g.goals.some(goal => goalKey(goal) === goalKey({ type, scope }))) return fail(res,409,'Det målet finns redan för den perioden.'); g.goals.push({ id: id(), type, target, ...(scope ? { scope } : {}) }); save(); res.json(view(g,p)); });
app.patch('/api/groups/:id/goals/:goalId', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); if (!p || !g || g.ownerId !== p.id) return fail(res,403,'Endast gruppens skapare kan ändra mål.'); const goal = g.goals.find(goal => goal.id === req.params.goalId); const target = Number(req.body.target); if (!goal || !Number.isInteger(target) || target < 100 || target > 10000000) return fail(res,400,'Ogiltigt mål.'); goal.target = target; save(); res.json(view(g,p)); });
app.delete('/api/groups/:id/goals/:goalId', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); if (!p || !g || g.ownerId !== p.id) return fail(res,403,'Endast gruppens skapare kan ändra mål.'); g.goals = g.goals.filter(goal => goal.id !== req.params.goalId); save(); res.json(view(g,p)); });
app.post('/api/groups/:id/leave', (req, res) => {
  const p = person(req);
  const g = db.groups.find(g => g.id === req.params.id);
  if (!p || !g || !g.members.includes(p.id)) return fail(res, 403, 'Du är inte med i den här gruppen.');
  g.members = g.members.filter(mid => mid !== p.id);
  if (g.memberNames) delete g.memberNames[p.id];
  for (const key of Object.keys(g.likes)) {
    if (key.endsWith(`:${p.id}`)) delete g.likes[key];
    else g.likes[key] = g.likes[key].filter(mid => mid !== p.id);
  }
  if (g.members.length === 0) db.groups = db.groups.filter(group => group.id !== g.id);
  else if (g.ownerId === p.id) g.ownerId = g.members[0];
  save(); res.json({ left: true, deleted: g.members.length === 0 });
});
app.post('/api/groups/:id/remove', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); const mid = String(req.body.memberId); if (!p || !g || g.ownerId !== p.id || mid === p.id || !g.members.includes(mid)) return fail(res,403,'Du kan inte ta bort den här personen.'); const removedName = g.memberNames?.[mid]?.toLowerCase(); g.members = g.members.filter(x => x !== mid); g.banned.push(mid); if (removedName) (g.bannedNames ||= []).push(removedName); if (g.memberNames) delete g.memberNames[mid]; for (const key of Object.keys(g.likes)) { if (key.endsWith(`:${mid}`)) delete g.likes[key]; else g.likes[key] = g.likes[key].filter(x => x !== mid); } save(); res.json(view(g,p)); });
app.post('/api/groups/:id/like', (req, res) => { const p = person(req); const g = db.groups.find(g => g.id === req.params.id); const mid = String(req.body.memberId); if (!p || !g || !g.members.includes(p.id) || !g.members.includes(mid) || p.id === mid) return fail(res,403,'Det gick inte att gilla.'); const key = `${dateKey()}:${mid}`; const likes = g.likes[key] || []; g.likes[key] = likes.includes(p.id) ? likes.filter(x => x !== p.id) : [...likes,p.id]; save(); res.json(view(g,p)); });
// Only distance and quality metadata reach this server. No lat/lon or routes are accepted.
app.post('/api/track', (req, res) => {
  const p = person(req);
  if (!p) return fail(res,401,'Sessionen hittades inte.');
  if ('lat' in req.body || 'lon' in req.body) return fail(res,400,'GPS-positioner får inte skickas till servern.');
  const { meters, seconds, accuracy } = req.body;
  if (![meters,seconds,accuracy].every((n:unknown)=>typeof n==='number'&&Number.isFinite(n)) || seconds<2 || seconds>300 || meters<8 || meters/seconds>3.2 || accuracy<0 || accuracy>40)
    return fail(res,400,'Ogiltig GPS-sträcka.');
  const now=Date.now();
  if (p.lastMovementAt && now-p.lastMovementAt<1800) return fail(res,429,'Vänta på nästa mätning.');
  const day=dateKey();
  p.distances[day]=Math.round((p.distances[day]||0)+meters);
  p.activities ||= [];
  recordWalk(p.activities,day,now,meters);
  p.lastMovementAt=now;
  save();
  res.json({distance:p.distances[day]});
});
if (existsSync(join(process.cwd(), 'dist', 'index.html'))) { app.use(express.static('dist', { setHeaders(res, file) { if (file.endsWith('index.html')) res.setHeader('Cache-Control', 'no-store'); } })); app.get('*', (_req,res) => { res.setHeader('Cache-Control', 'no-store'); res.sendFile(join(process.cwd(),'dist','index.html')); }); }
// Keeps stack traces and server paths out of error responses; the full error is logged server-side.
app.use((err: Error & { status?: number }, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  if (!err.status || err.status >= 500) console.error(err);
  res.status(err.status && err.status < 500 ? err.status : 500).json({ error: err.status && err.status < 500 ? 'Ogiltigt meddelande.' : 'Serverfel. Försök igen.' });
});
const port = Number(process.env.API_PORT || process.env.PORT || 3001);
app.listen(port, '0.0.0.0', () => console.log(`API listening on ${port}`));
