type Kind = 'dad' | 'mom' | 'duckling';
const colors = {
  dad: { body: '#b8bcc6', wing: '#9096a3', head: '#2f8f5b' },
  mom: { body: '#c9a27a', wing: '#a67c52', head: '#b98f66' },
  duckling: { body: '#ffd23f', wing: '#f3b21f', head: '#ffd23f' },
};
const size = { dad: 28, mom: 27, duckling: 14 };

// A cartoon duck facing right.
function Duck({ kind }: { kind: Kind }) {
  const c = colors[kind];
  return (
    <svg className={'duck duck-' + kind} viewBox="0 0 42 34" height={size[kind]} width={size[kind] * 42 / 34}>
      <path d="M6 19 L1 13 L11 17Z" fill={c.body} />
      <ellipse cx="18" cy="22" rx="14" ry="9" fill={c.body} />
      <ellipse cx="17" cy="22" rx="7" ry="4.5" fill={c.wing} />
      <ellipse cx="14" cy="31" rx="3.2" ry="1.3" fill="#f59e0b" />
      <ellipse cx="23" cy="31" rx="3.2" ry="1.3" fill="#f59e0b" />
      {kind === 'dad' && <rect x="24" y="16" width="10" height="2" rx="1" fill="#fff" />}
      <circle cx="29" cy="11" r="7" fill={c.head} />
      <path d="M35 10.5 L41.5 13 L35 15.5Z" fill="#f59e0b" />
      <circle cx="31" cy="9.3" r="1.4" fill="#29283b" />
    </svg>
  );
}

/** A duck family on its way across the page: fourteen ducklings running after the mother and father. */
export default function FunDucks() {
  return (
    <div className="fun-trail" aria-hidden="true">
      <div className="duck-row">
        {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14].map(i => <Duck key={i} kind="duckling" />)}
        <Duck kind="mom" />
        <Duck kind="dad" />
      </div>
    </div>
  );
}
