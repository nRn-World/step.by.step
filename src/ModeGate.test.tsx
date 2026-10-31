// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ModeGate from './ModeGate';
import { estimatedMeters } from './steps';

vi.mock('canvas-confetti', () => ({ default: vi.fn() }));
const reply = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;
describe('privacy-first mode selection', () => {
  beforeEach(()=>{localStorage.clear();sessionStorage.clear();window.history.replaceState({},'','/');vi.spyOn(globalThis,'fetch').mockImplementation(async()=>{throw Error('Private mode must never call server')});});
  afterEach(()=>{cleanup();vi.restoreAllMocks()});
  it('starts with two explicit modes and does not send requests for the private mode',()=>{
    render(<ModeGate />);
    expect(screen.getByText('Offline · utan GPS')).toBeDefined();
    expect(screen.getByText('Tillsammans · live')).toBeDefined();
    fireEvent.click(screen.getByText('Offline · utan GPS')); 
    expect(screen.getByText('Nära varandra. Bara här.')).toBeDefined();
    fireEvent.click(screen.getByText(/Läs steg från telefonen/));
    expect(screen.getByRole('status').textContent).toContain('installerad iPhone- eller Android-app');
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(localStorage.getItem('stride-token')).toBeNull();
  });
  it('offers named flags and translates the landing before a mode is selected',()=>{
    render(<ModeGate />);
    expect(screen.getByRole('button',{name:/Svenska/})).toBeDefined();
    expect(screen.getByRole('button',{name:/Dansk/})).toBeDefined();
    fireEvent.click(screen.getByRole('button',{name:/English/}));
    expect(screen.getByText('How will you walk?')).toBeDefined();
    fireEvent.click(screen.getByRole('button',{name:/Dansk/}));
    expect(screen.getByText('Hvordan vil I gå?')).toBeDefined();
    expect(localStorage.getItem('stride-lang')).toBeNull();
  });
  it('converts steps to an explicitly estimated distance',()=>{
    expect(estimatedMeters(1000)).toBe(750);
  });
  it('does not pretend the OAuth integration works without configuration',async()=>{
    vi.resetModules();
    vi.stubEnv('VITE_SUPABASE_URL','');vi.stubEnv('VITE_SUPABASE_ANON_KEY','');
    const { default: UnconfiguredModeGate } = await import('./ModeGate');
    render(<UnconfiguredModeGate />);fireEvent.click(screen.getByText('Tillsammans · live'));
    expect(screen.getByText('Fortsätt med Google').closest('button')?.disabled).toBe(true);
    expect(screen.getByText('Fortsätt med Apple').closest('button')?.disabled).toBe(true);
    expect(screen.getByText(/Inloggning väntar på konfiguration/)).toBeDefined();
    vi.unstubAllEnvs();
  });
  it('lets the tester open the app without Google or Apple, using sample data only',()=>{
    render(<ModeGate />);fireEvent.click(screen.getByText('Tillsammans · live'));
    fireEvent.click(screen.getByText(/Testa appen utan konto/));
    expect(screen.getByText(/TESTLÄGE/)).toBeDefined();
    expect(screen.getAllByText('FÖRHANDSVISNING').length).toBeGreaterThan(0);
    expect(screen.getByText(/Så här kan din grupp se ut/)).toBeDefined();
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(localStorage.getItem('stride-token')).toBeNull();
    fireEvent.click(screen.getByText('Avsluta test'));
    expect(screen.getByText('Hur vill ni gå?')).toBeDefined();
    expect(sessionStorage.getItem('stride-test-mode')).toBeNull();
  });
  it('signs in with email and password and opens the app with the session',async()=>{
    vi.resetModules();
    vi.stubEnv('VITE_SUPABASE_URL','https://example.supabase.co');
    vi.stubEnv('VITE_SUPABASE_ANON_KEY','anon-key');
    let emit: ((event:string, session:unknown)=>void)|undefined;
    const signInWithPassword = vi.fn(async()=>({error:null}));
    vi.doMock('@supabase/supabase-js',()=>({createClient:()=>({auth:{
      getSession:async()=>({data:{session:null}}),
      onAuthStateChange:(cb:(event:string,session:unknown)=>void)=>{emit=cb;return {data:{subscription:{unsubscribe(){}}}};},
      signInWithPassword,
    }})}));
    vi.spyOn(globalThis,'fetch').mockImplementation(async(input)=>{
      if(String(input)==='/api/state')return reply({me:{id:'person-1',name:'Lina',distances:{}},groups:[]});
      throw Error('Unexpected request: '+String(input));
    });
    const { default: FreshModeGate } = await import('./ModeGate');
    render(<FreshModeGate initialMode="connected" />);
    fireEvent.change(screen.getByLabelText('E-post'),{target:{value:'lina@example.com'}});
    fireEvent.change(screen.getByLabelText('Lösenord'),{target:{value:'hemligt123'}});
    fireEvent.click(screen.getByRole('button',{name:'Logga in'}));
    await waitFor(()=>expect(signInWithPassword).toHaveBeenCalledWith({email:'lina@example.com',password:'hemligt123'}));
    emit?.('SIGNED_IN',{access_token:'session-token',user:{id:'user-1'}});
    await waitFor(()=>expect(screen.getByText(/Radera kontot/)).toBeDefined());
    expect(localStorage.getItem('stride-connected')).toBe('yes');
    vi.doUnmock('@supabase/supabase-js');
    vi.unstubAllEnvs();
  });
  it('rejects a short password before it reaches the auth service',async()=>{
    vi.resetModules();
    vi.stubEnv('VITE_SUPABASE_URL','https://example.supabase.co');
    vi.stubEnv('VITE_SUPABASE_ANON_KEY','anon-key');
    const signUp = vi.fn();
    vi.doMock('@supabase/supabase-js',()=>({createClient:()=>({auth:{getSession:async()=>({data:{session:null}}),onAuthStateChange:()=>({data:{subscription:{unsubscribe(){}}}}),signUp}})}));
    const { default: FreshModeGate } = await import('./ModeGate');
    render(<FreshModeGate initialMode="connected" />);
    fireEvent.change(screen.getByLabelText('E-post'),{target:{value:'lina@example.com'}});
    fireEvent.change(screen.getByLabelText('Lösenord'),{target:{value:'kort'}});
    fireEvent.click(screen.getByRole('button',{name:'Skapa konto'}));
    await waitFor(()=>expect(screen.getByRole('alert').textContent).toContain('minst 8 tecken'));
    expect(signUp).not.toHaveBeenCalled();
    vi.doUnmock('@supabase/supabase-js');
    vi.unstubAllEnvs();
  });
  it('signs out and deletes the account after an explicit confirmation',async()=>{
    vi.resetModules();
    vi.stubEnv('VITE_SUPABASE_URL','https://example.supabase.co');
    vi.stubEnv('VITE_SUPABASE_ANON_KEY','anon-key');
    const signOut = vi.fn(async()=>({error:null}));
    vi.doMock('@supabase/supabase-js',()=>({createClient:()=>({auth:{
      getSession:async()=>({data:{session:{access_token:'session-token',user:{id:'user-1'}}}}),
      onAuthStateChange:()=>({data:{subscription:{unsubscribe(){}}}}),
      signOut,
    }})}));
    const calls: string[] = [];
    vi.spyOn(globalThis,'fetch').mockImplementation(async(input,init)=>{
      const url=String(input);
      calls.push((init?.method||'GET')+' '+url);
      if(url==='/api/accounts'&&init?.method==='DELETE')return reply({deleted:true});
      if(url==='/api/state')return reply({me:{id:'person-1',name:'Lina',distances:{}},groups:[]});
      throw Error('Unexpected request: '+url);
    });
    localStorage.setItem('stride-connected','yes');
    const confirm = vi.spyOn(window,'confirm').mockReturnValue(true);
    const { default: FreshModeGate } = await import('./ModeGate');
    render(<FreshModeGate initialMode="connected" />);
    await waitFor(()=>expect(screen.getByRole('button',{name:'Radera kontot'})).toBeDefined());
    fireEvent.click(screen.getByRole('button',{name:'Radera kontot'}));
    await waitFor(()=>expect(screen.getByText('Hur vill ni gå?')).toBeDefined());
    expect(confirm).toHaveBeenCalled();
    expect(calls).toContain('DELETE /api/accounts');
    expect(signOut).toHaveBeenCalled();
    expect(localStorage.getItem('stride-connected')).toBeNull();
    vi.doUnmock('@supabase/supabase-js');
    vi.unstubAllEnvs();
  });
  it('stacks the footer tag on two centred lines instead of wrapping',()=>{
    render(<ModeGate />);
    const lines=document.querySelector('.landing-tag-lines');
    expect(lines).not.toBeNull();
    expect(Array.from(lines!.children).map(child=>child.textContent)).toEqual(['ETT STEG I TAGET,','TILLSAMMANS.']);
    fireEvent.click(screen.getByRole('button',{name:/English/}));
    expect(Array.from(document.querySelector('.landing-tag-lines')!.children).map(child=>child.textContent)).toEqual(['ONE STEP AT A TIME,','TOGETHER.']);
  });
  it('draws a real star everywhere the landing uses a mark, never a wingding glyph',()=>{
    const { container } = render(<ModeGate />);
    expect(container.textContent).not.toMatch(/[✳✻✱]/);
    for (const selector of ['.landing-kicker-star','.stamp-star','.mode-option-star','.landing-tag-star','.caption-star']) {
      expect(container.querySelector(selector+' svg')).not.toBeNull();
    }
  });
  it('credits the maker in the footer with reachable links',()=>{
    render(<ModeGate />);
    const site=screen.getByRole('link',{name:'nrnworld.one'});
    expect(site.getAttribute('href')).toBe('https://nrnworld.one');
    expect(site.getAttribute('rel')).toContain('noopener');
    const mail=screen.getByRole('link',{name:'bynrnworld@gmail.com'});
    expect(mail.getAttribute('href')).toBe('mailto:bynrnworld@gmail.com');
    expect(screen.getByText(/Skapad av/)).toBeDefined();
    fireEvent.click(screen.getByRole('button',{name:/English/}));
    expect(screen.getByText(/Created by/)).toBeDefined();
  });
});
