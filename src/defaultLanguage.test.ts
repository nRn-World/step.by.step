import { describe, expect, it } from 'vitest';
import { languageForPlace } from './defaultLanguage';

describe('languageForPlace', () => {
  it('uses the language of the country the phone is in, English elsewhere', () => {
    expect(languageForPlace('Europe/Stockholm')).toBe('sv');
    expect(languageForPlace('Europe/Copenhagen')).toBe('da');
    expect(languageForPlace('Atlantic/Faroe')).toBe('da');
    expect(languageForPlace('Europe/London')).toBe('en');
    expect(languageForPlace('America/New_York')).toBe('en');
    expect(languageForPlace('')).toBe('en');
  });
});
