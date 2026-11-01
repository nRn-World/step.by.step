import { registerPlugin } from '@capacitor/core';

// Native plugin (android/.../StepCounterPlugin.java): total steps since boot from the phone's hardware step counter.
const StepCounter = registerPlugin<{ getTotal(): Promise<{ total: number }> }>('StepCounter');

export type SensorState = { day: string; last: number; at: number; steps: number };
const SIX_HOURS = 6 * 3600000;

/** Adds a new since-boot reading to today's total. Steps taken while the app was closed arrive as the difference to the last reading. */
export function addReading(saved: SensorState | null, total: number, day: string, now: number): SensorState {
  if (!saved) return { day, last: total, at: now, steps: 0 };
  const delta = total >= saved.last ? total - saved.last : total; // the counter restarts from 0 after a reboot
  // Steps from an earlier day are only credited when the last reading was a short while ago (around midnight); never a whole gap of days.
  const carried = saved.day === day ? saved.steps + delta : now - saved.at <= SIX_HOURS ? delta : 0;
  return { day, last: total, at: now, steps: Math.min(carried, 100000) };
}

const localDay = (time: number) => new Date(time).toLocaleDateString('en-CA');

/** Today's steps from the hardware counter, or null when the phone has no counter or the permission was refused. */
export async function sensorStepsToday(): Promise<number | null> {
  try {
    const { total } = await StepCounter.getTotal();
    const now = Date.now();
    let saved: SensorState | null = null;
    try { saved = JSON.parse(localStorage.getItem('stride-sensor') || 'null'); } catch { /* start fresh */ }
    const next = addReading(saved, total, localDay(now), now);
    localStorage.setItem('stride-sensor', JSON.stringify(next));
    return next.steps;
  } catch { return null; }
}
