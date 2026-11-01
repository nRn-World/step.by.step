import { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { App as NativeApp } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { createClient, type Session } from '@supabase/supabase-js';
import App from './App';
import { setAccessToken } from './apiToken';
import PrivateMode from './PrivateMode';
import WalkingScene from './components/WalkingScene';
import LanguagePicker from './components/LanguagePicker';
import {modeText,type ModeLanguage} from './modeText';
import { translateServerError } from './serverErrors';
import { cleanUsername, usernameToEmail } from './username';
import { Footprints, Users, MapPin, ArrowRight, Star } from './components/PlayfulIcons';
import BrandMark from './components/BrandMark';
import { GoogleIcon, AppleIcon } from './components/AuthIcons';
import { pendingJoin, rememberJoin } from './pendingJoin';
import './mode.css';
import './landing-test.css';

const url = import.meta.env.VITE_SUPABASE_URL;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY;

/** `initialMode`/`initialLanguage` exist so store screenshots and tests can render a given view
 * without a browser. In the app they are omitted and the stored choice decides. */
export default function ModeGate({ initialMode, initialLanguage }: { initialMode?: 'choose'|'private'|'connected'; initialLanguage?: ModeLanguage } = {}) {
  const [language,setLanguage]=useState<ModeLanguage>(()=>{
    if (initialLanguage) return initialLanguage;
    const stored=sessionStorage.getItem('pending-language')||localStorage.getItem('stride-lang')||'sv';
    return (['sv','da','en'] as const).includes(stored as ModeLanguage)?stored as ModeLanguage:'sv';
  });
  const m=modeText[language]||modeText.sv;
  useEffect(()=>{document.documentElement.lang=language},[language]);
  const [mode, setMode] = useState<'choose'|'private'|'connected'>(() => initialMode ?? (sessionStorage.getItem('pending-connected')==='yes'||localStorage.getItem('stride-connected')==='yes'?'connected':'choose'));
  // An invite link is remembered right away, because signing in sends the person away and back without the ?join= part.
  const [inviteGroup, setInviteGroup] = useState('');
  useEffect(() => {
    rememberJoin(window.location.href);
    const code = pendingJoin();
    if (code) fetch((import.meta.env.VITE_API_URL || '/api') + '/invite/' + encodeURIComponent(code)).then(res => res.ok ? res.json() : null).then(info => { if (info?.name) setInviteGroup(info.name); }).catch(() => undefined);
  }, []);
  const [auth, setAuth] = useState<ReturnType<typeof createClient>|null>(null);
  const [session, setSession] = useState<Session|null>(null);
  const [error, setError] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [accountBusy, setAccountBusy] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [notice, setNotice] = useState('');
  // Set when the person opens the link from the password reset mail.
  const [recovering, setRecovering] = useState(false);
  // A clearly labelled test mode: the connected screens open with sample data, no account and no server calls.
  const [testMode, setTestMode] = useState(() => initialMode === 'connected' ? false : sessionStorage.getItem('stride-test-mode') === 'yes');
  const startTest = () => { localStorage.removeItem('stride-token'); setError(''); sessionStorage.setItem('stride-test-mode','yes'); setTestMode(true); };
  const endTest = () => { sessionStorage.removeItem('stride-test-mode'); setTestMode(false); setMode('choose'); };
  useEffect(() => {
    if((mode==='connected'||mode==='choose') && !auth && url && key) setAuth(createClient(url,key));
  },[mode,auth]);
  useEffect(() => {
    if (!auth) return;
    let listener: {remove:()=>Promise<void>}|undefined;
    // Google sign-in returns to the app through the app.stegvis.mobile://auth/callback link, with the session in the # part (or a code in the ? part).
    const finishNative = async (callback: string) => {
      if (callback.startsWith('http')) { rememberJoin(callback); return; } // an invite link opened in the app
      if (!callback.startsWith('app.stegvis.mobile://auth/callback')) return;
      try { await Browser.close(); } catch { /* the sign-in tab may already be closed */ }
      try {
        const link = new URL(callback);
        const params = new URLSearchParams(link.hash.slice(1));
        const access_token = params.get('access_token'), refresh_token = params.get('refresh_token'), code = link.searchParams.get('code');
        let reason: unknown = null;
        if (access_token && refresh_token) reason = (await auth.auth.setSession({access_token, refresh_token})).error;
        else if (code) reason = (await auth.auth.exchangeCodeForSession(code)).error;
        else { setError(m.loginCancelled); return; }
        if (reason) setError(m.loginFailed);
        else {sessionStorage.removeItem('pending-connected');localStorage.setItem('stride-connected','yes');setMode('connected');}
      } catch { setError(m.loginFailed); }
    };
    if (Capacitor.isNativePlatform()) {
      void NativeApp.addListener('appUrlOpen', ({url:callback}) => void finishNative(callback)).then(handle => {listener=handle});
      // The link can arrive before this listener exists (the app was restarted by it), so read it from the launch too.
      void NativeApp.getLaunchUrl().then(launch => { if (launch?.url) void finishNative(launch.url); }).catch(() => undefined);
    }
    auth.auth.getSession().then(({data})=>{setSession(data.session);if(data.session)sessionStorage.removeItem('pending-connected')}).catch(()=>setError(m.sessionError));
    const {data:{subscription}} = auth.auth.onAuthStateChange((event, next)=>{setSession(next);if(next)localStorage.setItem('stride-connected','yes');if(event==='PASSWORD_RECOVERY'){setRecovering(true);setMode('connected');}});
    return () => {subscription.unsubscribe(); void listener?.remove();};
  }, [auth]);
  useEffect(() => { setAccessToken(mode==='connected' ? session?.access_token || '' : ''); return () => setAccessToken(''); }, [mode,session]);
  const apiBase = import.meta.env.VITE_API_URL || '/api';
  const finishSignIn = () => { localStorage.setItem('stride-connected','yes'); localStorage.setItem('stride-lang', language); sessionStorage.removeItem('stride-test-mode'); setTestMode(false); setAccountError(''); setNotice(''); };
  const emailSignIn = async (kind: 'login'|'create') => {
    if (!auth || accountBusy) return;
    const address = email.trim();
    setAccountBusy(true); setAccountError(''); setNotice(''); setError('');
    try {
      if (!address) throw new Error(m.accountNeedEmail);
      if (password.length < 8) throw new Error(m.accountPasswordShort);
      if (address.includes('@')) {
        if (kind === 'create') {
          const {data, error: reason} = await auth.auth.signUp({email: address, password, options: {emailRedirectTo: window.location.origin + window.location.pathname}});
          if (reason) throw new Error(m.accountFailed);
          if (!data.session) { setPassword(''); setNotice(m.accountCheckMail); return; }
        } else {
          const {error: reason} = await auth.auth.signInWithPassword({email: address, password});
          if (reason) throw new Error(m.accountWrong);
        }
      } else {
        // A name-only account: the server creates it without an email, then the browser signs in with the made-up address.
        const username = cleanUsername(address);
        if (!username) throw new Error(m.usernameInvalid);
        if (kind === 'create') {
          const res = await fetch(apiBase + '/username-signup', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({username, password})}).catch(() => null);
          const body = res ? await res.json().catch(() => ({})) as {error?: string} : {};
          if (!res || !res.ok) throw new Error(translateServerError(body.error, language) || m.accountFailed);
        }
        const {error: reason} = await auth.auth.signInWithPassword({email: usernameToEmail(username), password});
        if (reason) throw new Error(kind === 'create' ? m.accountFailed : m.accountWrong);
      }
      setPassword(''); finishSignIn();
    } catch (e) { setAccountError(e instanceof Error && e.message ? e.message : m.accountFailed); }
    finally { setAccountBusy(false); }
  };
  // The reply is the same whether or not the address exists, so the form cannot be used to find accounts.
  const requestReset = async () => {
    if (!auth || accountBusy) return;
    const address = email.trim();
    setAccountError(''); setNotice('');
    if (!address) { setAccountError(m.accountNeedEmail); return; }
    if (!address.includes('@')) { setNotice(m.usernameNoReset); return; }
    setAccountBusy(true);
    try {
      await auth.auth.resetPasswordForEmail(address, {redirectTo: window.location.origin + window.location.pathname});
      setNotice(m.accountResetSent);
    } catch { setAccountError(m.accountFailed); }
    finally { setAccountBusy(false); }
  };
  const saveNewPassword = async () => {
    if (!auth || accountBusy) return;
    setAccountError('');
    if (newPassword.length < 8) { setAccountError(m.accountPasswordShort); return; }
    setAccountBusy(true);
    try {
      const {error: reason} = await auth.auth.updateUser({password: newPassword});
      if (reason) throw new Error(m.accountFailed);
      setNewPassword(''); setRecovering(false); setNotice(m.accountPasswordSaved);
      window.history.replaceState({}, '', window.location.pathname);
    } catch (e) { setAccountError(e instanceof Error && e.message ? e.message : m.accountFailed); }
    finally { setAccountBusy(false); }
  };
  const signOutAccount = async () => {
    await auth?.auth.signOut().catch(() => undefined);
    localStorage.removeItem('stride-connected');
    localStorage.removeItem('stride-token');
    sessionStorage.removeItem('pending-connected');
    setSession(null); setRecovering(false); setNotice(''); setAccountError(''); setMode('choose');
  };
  // Removes the person and their group data on the server. The sign-in user stays in Supabase until it is deleted there.
  const deleteAccount = async () => {
    if (!session || !window.confirm(m.deleteConfirm)) return;
    setAccountError('');
    try {
      const res = await fetch(apiBase + '/accounts', { method:'DELETE', headers:{ Authorization:'Bearer ' + session.access_token } });
      if (!res.ok) throw new Error(m.accountFailed);
      await signOutAccount();
    } catch (e) { setAccountError(e instanceof Error && e.message ? e.message : m.accountFailed); }
  };
  const signIn = async (provider: 'google'|'apple') => {
    if (!auth) return;
    setError('');
    const native=Capacitor.isNativePlatform();
    sessionStorage.setItem('pending-connected','yes');
    sessionStorage.setItem('pending-language',language);
    const {data,error: reason} = await auth.auth.signInWithOAuth({provider,options:{redirectTo:native?'app.stegvis.mobile://auth/callback':window.location.origin + window.location.pathname,skipBrowserRedirect:native}});
    if (native && data.url && !reason) await Browser.open({url:data.url});
    if (reason) {sessionStorage.removeItem('pending-connected');setError(m.loginFailed);}
  };
  if (recovering) return <div className="mode-page"><div className="mode-inner"><div className="mode-panel"><h2>{m.accountNewPassword}</h2><form className="account-form" onSubmit={e=>{e.preventDefault();void saveNewPassword();}}><label htmlFor="new-password">{m.accountPassword}</label><input id="new-password" type="password" autoComplete="new-password" value={newPassword} onChange={e=>setNewPassword(e.target.value)} minLength={8} maxLength={72} required/><p className="account-hint">{m.accountPasswordHint}</p><div className="account-actions"><button type="submit" disabled={accountBusy}>{m.accountSavePassword}</button></div></form>{accountError&&<p role="alert" className="mode-warning">{accountError}</p>}</div></div></div>;
  if (mode === 'choose' && !testMode) return <div className="landing"><div className="landing-paper">
    <header className="landing-nav">
      <a className="landing-brand" href="/" aria-label="step.by.step"><span className="landing-brand-mark"><BrandMark size={40}/></span><span>step<span className="brand-period">.</span>by<span className="brand-period">.</span>step</span></a>
      <span className="landing-nav-note">{m.nav}</span><LanguagePicker value={language} onChange={setLanguage}/>
      <a className="landing-nav-link" href={"/privacypolicy.html?lang="+language} target="_blank" rel="noopener noreferrer">{m.privacy} <span aria-hidden="true">↗</span></a>
    </header>
    <main>
      <div className="landing-main">
        <div className="landing-copy">
          <p className="landing-kicker"><span className="landing-kicker-star" aria-hidden="true"><Star size={17}/></span> {m.welcome}</p>
          <h1>{m.headlineStart}<br/>{m.headlineEnd} <em>{m.headlineAccent}</em></h1>
          <p className="landing-lead">{m.lead}</p>{inviteGroup&&<p className="invite-note" role="status"><strong>{m.invitedTo} {inviteGroup}</strong><span>{m.invitedHint}</span></p>}
          <div className="test-entry landing-test-entry"><button onClick={startTest}>{m.testEntry}<ArrowRight size={16}/></button><p>{m.testEntryHint}</p></div>
          <a className="landing-jump" href="#val">{m.jump} <span aria-hidden="true">↓</span></a>
        </div>
        <div className="landing-art"><div className="landing-art-sun"/><WalkingScene/><span className="landing-art-stamp"><span>{m.artTop}</span><span className="stamp-star" aria-hidden="true"><Star size={26}/></span><span>{m.artBottom}</span></span><span className="landing-art-caption">{m.artCaption} <span className="caption-star" aria-hidden="true"><Star size={10}/></span></span></div>
      </div>
      <section className="landing-choices" id="val" aria-label={m.choose}>
        <div className="landing-choice-heading"><div><h2>{m.choose}</h2></div><p>{m.chooseSub}</p></div>
        <div className="mode-options">
          <button className="mode-option private-option" onClick={()=>setMode('connected')}><span className="mode-option-top"><span className="mode-option-number">01</span><span className="mode-option-symbol"><Footprints size={27} strokeWidth={2.5}/></span></span><span className="mode-option-title">{m.localTitle}</span><span className="mode-option-desc">{m.localDesc}</span><span className="mode-option-bottom">{m.localTag} <span className="mode-option-arrow"><ArrowRight size={18}/></span></span></button>
          <div className="mode-option together-option social-option"><span className="mode-option-top"><span className="mode-option-number">02</span><span className="mode-option-symbol"><Users size={27} strokeWidth={2.5}/></span></span><h3 className="mode-option-title">{m.liveTitle}</h3><span className="mode-option-desc">{m.liveDesc}</span><span className="social-buttons"><button onClick={()=>void signIn('google')} disabled={!auth}><GoogleIcon/>{m.google}</button><button disabled><AppleIcon/>{m.apple}<span className="soon-badge">{m.soon}</span></button></span></div>
        </div>
      </section>
    </main>
    <footer className="landing-footer"><span className="landing-tag"><span className="landing-tag-star" aria-hidden="true"><Star size={11}/></span><span className="landing-tag-lines"><span>{m.footerLead}</span><span>{m.footerTail}</span></span></span><span className="landing-credit">{m.createdBy} <a href="https://nrnworld.one" target="_blank" rel="noopener noreferrer">nrnworld.one</a><span aria-hidden="true" className="credit-slash">/</span><a href="mailto:bynrnworld@gmail.com">bynrnworld@gmail.com</a></span><span>{m.footerEnd}</span></footer>
  </div></div>;
  if (mode === 'private') return <PrivateMode language={language} onLanguageChange={setLanguage} onBack={()=>setMode('choose')}/>;
  if (session && mode === 'connected') return <div className="connected-shell"><div className="connected-banner"><span><MapPin size={15}/> {m.liveBanner}</span></div>{accountError&&<p role="alert" className="mode-warning">{accountError}</p>}<App key={session.user.id} accessToken={session.access_token} initialLang={language} onSignOut={() => void signOutAccount()} onDeleteAccount={() => void deleteAccount()}/></div>;
  if (testMode && !session) return <div className="connected-shell"><div className="connected-banner test-banner"><span><MapPin size={15}/> {m.testBanner}</span><button onClick={endTest}>{m.testExit}</button></div><App key="test-mode" initialLang={language} onRequireLogin={()=>{sessionStorage.removeItem('stride-test-mode');setTestMode(false);setMode('connected');}}/></div>;
  if (session && auth) return <div className="connected-shell"><div className="connected-banner"><span><MapPin size={15}/> {m.liveBanner}</span></div><App key={session.user.id} accessToken={session.access_token} initialLang={language} onSignOut={() => void signOutAccount()} onDeleteAccount={() => void deleteAccount()}/></div>;
  return <div className="mode-page"><div className="mode-inner"><button className="mode-back" onClick={()=>{sessionStorage.removeItem('pending-connected');setMode('choose')}}>← {m.back}</button><LanguagePicker value={language} onChange={setLanguage}/><div className="mode-mark logo-mark"><BrandMark size={72}/></div><p className="mode-eyebrow">{m.loginEyebrow}</p><h1>{m.loginTitle}</h1><p className="mode-intro">{m.loginIntro}</p>{inviteGroup&&<p className="invite-note" role="status"><strong>{m.invitedTo} {inviteGroup}</strong><span>{m.invitedHint}</span></p>}<div className="mode-panel"><h2>{m.accountTitle}</h2><p>{m.accountNote}</p><form className="account-form" onSubmit={e=>{e.preventDefault();void emailSignIn('login');}}><label htmlFor="account-email">{m.accountEmail}</label><input id="account-email" type="text" autoComplete="username" autoCapitalize="none" spellCheck={false} value={email} onChange={e=>setEmail(e.target.value)} placeholder={m.accountIdPlaceholder} required/><label htmlFor="account-password">{m.accountPassword}</label><input id="account-password" type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} minLength={8} maxLength={72} required/><p className="account-hint">{m.accountPasswordHint}</p><div className="account-actions"><button type="submit" disabled={accountBusy||!auth}>{m.accountLogin}</button><button type="button" className="secondary" disabled={accountBusy||!auth} onClick={()=>void emailSignIn('create')}>{m.accountCreate}</button></div><button type="button" className="account-forgot" disabled={accountBusy||!auth} onClick={()=>void requestReset()}>{m.accountForgot}</button></form>{notice&&<p role="status" className="account-notice">{notice}</p>}{accountError&&<p role="alert" className="mode-warning">{accountError}</p>}<div className="auth-divider">{m.accountDivider}</div><div className="auth-actions"><button onClick={()=>signIn('google')} disabled={!auth}><GoogleIcon/>{m.google}</button><button disabled><AppleIcon/>{m.apple}<span className="soon-badge">{m.soon}</span></button></div>{!auth&&<p className="mode-warning">{m.oauthWait}</p>}{error&&<p role="alert" className="mode-warning">{error}</p>}</div><a className="mode-policy" href={"/privacypolicy.html?lang="+language} target="_blank" rel="noopener noreferrer">{m.dataLink}</a></div></div>;
}
