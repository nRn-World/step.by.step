import { describe, expect, it } from 'vitest';
import { translateServerError } from './serverErrors';

describe('translateServerError', () => {
  it('keeps Swedish text for Swedish users', () => {
    expect(translateServerError('Logga in med e-post, Google eller Apple.', 'sv')).toBe('Logga in med e-post, Google eller Apple.');
  });
  it('translates known messages for English and Danish users', () => {
    expect(translateServerError('Logga in med e-post, Google eller Apple.', 'en')).toBe('Sign in with email, Google or Apple.');
    expect(translateServerError('Logga in med e-post, Google eller Apple.', 'da')).toBe('Log ind med e-mail, Google eller Apple.');
  });
  it('falls back to the original text for unknown messages and empty input', () => {
    expect(translateServerError('Något nytt.', 'en')).toBe('Något nytt.');
    expect(translateServerError(undefined, 'en')).toBe('');
  });
});
