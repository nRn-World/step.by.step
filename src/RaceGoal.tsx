import { useEffect, useState } from 'react';
import { Footprints, Sparkles, Target, Trophy } from './components/PlayfulIcons';

export type RacePeriod = 'day' | 'week' | 'month';
export type RaceGoal = { period: RacePeriod; target: number };
export type RaceCopy = { title: string; first: string; set: string; change: string; save: string; remove: string; saved: string; reached: string; remaining: string; none: string; won: string; leads: string; even: string; hint: string; steps: string; day: string; week: string; month: string };
type Side = { name: string; steps: Record<RacePeriod, number> };
type Props = { copy: RaceCopy; ours: Side; theirs: Side; race: RaceGoal | null; isAdmin: boolean; format: (n: number) => string; onSave: (race: RaceGoal) => void; onClear: () => void };

// A shared finish line for two paired groups. Both runners start at the line and walk to their totals when the goal appears.
export default function RaceGoalTrack({ copy, ours, theirs, race, isAdmin, format, onSave, onClear }: Props) {
  const [started, setStarted] = useState(false);
  const [editing, setEditing] = useState(false);
  const [period, setPeriod] = useState<RacePeriod>(race?.period || 'week');
  const [target, setTarget] = useState(race ? String(race.target) : '');
  useEffect(() => { const frame = requestAnimationFrame(() => setStarted(true)); return () => cancelAnimationFrame(frame); }, []);
  const periodName = (p: RacePeriod) => ({ day: copy.day, week: copy.week, month: copy.month })[p];
  const save = () => { const value = Math.round(Number(target)); if (!Number.isFinite(value) || value < 1000) return; onSave({ period, target: value }); setEditing(false); };
  const form = isAdmin && (editing || !race) && <form className="rg-form" onSubmit={e => { e.preventDefault(); save(); }}>
    <div className="rg-periods" role="group" aria-label={copy.set}>{(['day', 'week', 'month'] as const).map(p => <button key={p} type="button" className={period === p ? 'selected' : ''} aria-pressed={period === p} onClick={() => setPeriod(p)}>{periodName(p)}</button>)}</div>
    <input type="number" inputMode="numeric" min={1000} max={5000000} step={1000} placeholder="100000" aria-label={copy.set} value={target} onChange={e => setTarget(e.target.value)} />
    <button className="rg-save" type="submit" disabled={Math.round(Number(target)) < 1000}>{copy.save}</button>
    {race && <button className="rg-remove" type="button" onClick={() => { setEditing(false); onClear(); }}>{copy.remove}</button>}
  </form>;

  if (!race) return <section className="rg rg-empty" aria-label={copy.title}>
    <div className="rg-head"><span className="rg-badge"><Target size={14} />{copy.title}</span></div>
    <p className="rg-none">{copy.none}</p>
    {isAdmin && <>
      {!editing && <button className="rg-open" type="button" onClick={() => setEditing(true)}>{copy.set}</button>}
      {editing && form}
    </>}
  </section>;

  const lanes = [{ ...ours, mine: true }, { ...theirs, mine: false }].map(lane => {
    const steps = lane.steps[race.period];
    return { ...lane, steps, pct: Math.min(100, Math.round(steps / race.target * 100)), done: steps >= race.target };
  });
  const leader = [...lanes].sort((a, b) => b.steps - a.steps)[0];
  const trailer = lanes.find(lane => lane !== leader)!;
  const diff = leader.steps - trailer.steps;
  const finished = lanes.some(lane => lane.done);
  const summary = finished ? `${leader.name} ${copy.won}` : diff === 0 ? copy.even : `${leader.name} ${copy.leads} ${format(diff)} ${copy.steps}`;

  return <section className="rg" aria-label={copy.title}>
    <div className="rg-head">
      <span className="rg-badge"><Target size={14} />{copy.title}</span>
      <span className="rg-goal">{copy.first} <strong>{format(race.target)}</strong> {copy.steps} · {periodName(race.period)}</span>
    </div>
    <div className="rg-lanes">{lanes.map(lane => <div className={'rg-lane' + (lane.mine ? ' mine' : '') + (lane.done ? ' done' : '')} key={lane.mine ? 'ours' : 'theirs'}>
      <span className="rg-name"><strong>{lane.name}</strong><small>{lane.done ? copy.reached : `${format(race.target - lane.steps)} ${copy.remaining}`}</small></span>
      <div className="rg-track">
        <div className="rg-fill" style={{ width: (started ? lane.pct : 0) + '%' }} />
        <span className="rg-runner" style={{ left: (started ? lane.pct : 0) + '%' }}><Footprints size={15} /></span>
        <span className="rg-flag" aria-hidden="true"><Trophy size={16} /></span>
        {lane.done && started && <span className="rg-burst" aria-hidden="true">{[0, 1, 2, 3, 4].map(i => <i key={i} style={{ animationDelay: `${i * 90}ms` }} />)}</span>}
      </div>
    </div>)}</div>
    <p className="rg-summary"><Sparkles size={14} /><span>{summary}</span></p>
    {isAdmin && !editing && race && <button className="rg-open" type="button" onClick={() => { setPeriod(race.period); setTarget(String(race.target)); setEditing(true); }}>{copy.change}</button>}
    {form}
  </section>;
}
