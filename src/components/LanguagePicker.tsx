import { useEffect, useState } from 'react';
import type { ModeLanguage } from '../modeText';
function Flag({code}:{code:ModeLanguage}) {
  if(code==='sv')return <svg viewBox="0 0 24 16" aria-hidden="true" className="flag-art"><rect width="24" height="16" rx="2" fill="#2165ab"/><path d="M0 6h24v4H0zm7-6h4v16H7z" fill="#f4cf44"/></svg>;
  if(code==='da')return <svg viewBox="0 0 24 16" aria-hidden="true" className="flag-art"><rect width="24" height="16" rx="2" fill="#c42d3b"/><path d="M0 6h24v4H0zM7 0h4v16H7z" fill="white"/></svg>;
  return <svg viewBox="0 0 24 16" aria-hidden="true" className="flag-art"><rect width="24" height="16" rx="2" fill="#233a78"/><path d="M0 0 24 16M24 0 0 16" stroke="white" strokeWidth="4"/><path d="M0 0 24 16M24 0 0 16" stroke="#ce3446" strokeWidth="1.7"/><path d="M0 6h24v4H0zM10 0h4v16h-4z" fill="white"/><path d="M0 7h24v2H0zM11 0h2v16h-2z" fill="#ce3446"/></svg>;
}
// Wide screens show all three languages. Phones show only the chosen one; tapping it opens the list (see mode.css).
export default function LanguagePicker({value,onChange}:{value:ModeLanguage;onChange:(next:ModeLanguage)=>void}){
  const [open,setOpen]=useState(false);
  useEffect(()=>{if(!open)return;const close=()=>setOpen(false);document.addEventListener('click',close);return()=>document.removeEventListener('click',close)},[open]);
  return <div className={'language-choices'+(open?' open':'')} role="group" aria-label="Språk / Sprog / Language">{(['sv','da','en'] as const).map(code=><button type="button" key={code} lang={code} aria-pressed={value===code} onClick={e=>{if(value===code){e.stopPropagation();setOpen(!open);}else{onChange(code);setOpen(false);}}}><Flag code={code}/><span>{code==='sv'?'Svenska':code==='da'?'Dansk':'English'}</span></button>)}</div>;
}
