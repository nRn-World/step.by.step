// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import RaceGoalTrack from './RaceGoal';

const copy = { title: 'Gemensamt mål', first: 'Först till', set: 'Sätt ett mål', change: 'Ändra', save: 'Spara', remove: 'Ta bort', saved: 'Sparat', reached: 'I mål!', remaining: 'kvar', none: 'Inget mål', won: 'nådde målet först!', leads: 'leder med', even: 'Lika', hint: '', steps: 'steg', day: 'Dag', week: 'Vecka', month: 'Månad' };
const fmt = (n: number) => n.toLocaleString('sv-SE');

describe('RaceGoalTrack', () => {
  it('shows who leads and how far the trailing team is from the goal', () => {
    render(<RaceGoalTrack copy={copy} ours={{ name: 'Sverige', steps: { day: 1, week: 42000, month: 0 } }} theirs={{ name: 'Danmark', steps: { day: 1, week: 61200, month: 0 } }} race={{ period: 'week', target: 100000 }} isAdmin={false} format={fmt} onSave={() => {}} onClear={() => {}} />);
    expect(screen.getByText('Danmark leder med 19 200 steg')).toBeTruthy();
    expect(screen.getByText('58 000 kvar')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Spara' })).toBeNull();
  });

  it('lets the creator set a goal when none exists', () => {
    render(<RaceGoalTrack copy={copy} ours={{ name: 'Sverige', steps: { day: 0, week: 0, month: 0 } }} theirs={{ name: 'Danmark', steps: { day: 0, week: 0, month: 0 } }} race={null} isAdmin={true} format={fmt} onSave={() => {}} onClear={() => {}} />);
    expect(screen.getByText('Inget mål')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sätt ett mål' })).toBeTruthy();
  });

  it('marks a team that has reached the goal', () => {
    render(<RaceGoalTrack copy={copy} ours={{ name: 'Sverige', steps: { day: 0, week: 9000, month: 0 } }} theirs={{ name: 'Danmark', steps: { day: 0, week: 120000, month: 0 } }} race={{ period: 'week', target: 100000 }} isAdmin={false} format={fmt} onSave={() => {}} onClear={() => {}} />);
    expect(screen.getByText('I mål!')).toBeTruthy();
    expect(screen.getByText('Danmark nådde målet först!')).toBeTruthy();
  });
});
