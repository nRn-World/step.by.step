import { describe, expect, it } from 'vitest';
import { cleanUsername, usernameToEmail } from './username';

describe('username accounts', () => {
  it('accepts names of 3 to 20 letters, digits, dot, underscore and hyphen', () => {
    for (const ok of ['Anna', 'bo_1', 'a.b-c', 'Åsa', 'Örjan9', 'x'.repeat(20)]) expect(cleanUsername(ok)).toBe(ok);
    expect(cleanUsername('  Anna ')).toBe('Anna');
  });

  it('rejects names that are too short or long, or cannot be part of an address', () => {
    for (const bad of ['ab', 'x'.repeat(21), '.anna', 'anna.', 'a..b', 'an na', 'a@b.se', 'ann<a>', '-anna', 'anna_', '']) expect(cleanUsername(bad)).toBeNull();
  });

  it('treats the same name in another case as the same account', () => {
    expect(usernameToEmail('Anna')).toBe(usernameToEmail('aNNA'));
    expect(usernameToEmail('ÅSA')).toBe(usernameToEmail('åsa'));
  });

  it('gives different names different addresses', () => {
    expect(usernameToEmail('anna')).toBe('anna@users.nrnworld.one');
    expect(usernameToEmail('åsa')).toBe('+asa@users.nrnworld.one');
    expect(new Set(['asa', 'åsa', 'äsa', 'ösa', 'a.sa', 'a-sa', 'a_sa'].map(usernameToEmail)).size).toBe(7);
  });
});
