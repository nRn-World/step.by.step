// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest';
import { rememberJoin, pendingJoin, clearPendingJoin, inviteLink } from './pendingJoin';

describe('pending join', () => {
  beforeEach(() => localStorage.clear());
  it('remembers the code from an invite link and forgets it again', () => {
    rememberJoin('https://nrnworld.one/step.by.step/?join=a424415873cc4c45');
    expect(pendingJoin()).toBe('a424415873cc4c45');
    clearPendingJoin();
    expect(pendingJoin()).toBe('');
  });
  it('ignores links without a valid code', () => {
    rememberJoin('https://nrnworld.one/step.by.step/');
    rememberJoin('?join=<script>');
    expect(pendingJoin()).toBe('');
  });
  it('builds the shared link from the public address', () => {
    expect(inviteLink('abc123')).toContain('?join=abc123');
  });
});
