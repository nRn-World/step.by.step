import { describe, it, expect } from 'vitest';
import { addReading } from './stepSensor';

const noon = Date.UTC(2026, 10, 1, 12);
describe('addReading', () => {
  it('starts at zero and then counts the difference, including steps taken while the app was closed', () => {
    const first = addReading(null, 5000, '2026-11-01', noon);
    expect(first.steps).toBe(0);
    expect(addReading(first, 5800, '2026-11-01', noon + 3600000).steps).toBe(800);
  });
  it('keeps counting after a reboot resets the counter', () => {
    const s = { day: '2026-11-01', last: 9000, at: noon, steps: 100 };
    expect(addReading(s, 40, '2026-11-01', noon + 1000).steps).toBe(140);
  });
  it('starts a new day at zero, crediting only a recent overnight difference', () => {
    const s = { day: '2026-11-01', last: 1000, at: noon, steps: 4000 };
    expect(addReading(s, 1030, '2026-11-02', noon + 3600000).steps).toBe(30);
    expect(addReading(s, 9000, '2026-11-04', noon + 3 * 86400000).steps).toBe(0);
  });
});
