import { useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, BookOpen, Check, DoorOpen, Focus, LoaderCircle, LockKeyhole, Plus, Users } from 'lucide-react';
import RoomDashboard from './components/RoomDashboard';
import MyRooms from './components/MyRooms';
import { forgetRoom, readRecentRooms, rememberRoom, type RecentRoom } from './lib/recentRooms';

type Identity = {
  participantId: string;
  displayName: string;
  token: string;
  expiresAt: string;
};

type Room = { code: string; createdAt: string; expiresAt: string; owner: boolean; name?: string | null };
type Mode = 'create' | 'join';

const API = '/api/v1';
const IDENTITY_KEY = 'studyroom.identity.v1';

function readIdentity(): Identity | null {
  try {
    const raw = localStorage.getItem(IDENTITY_KEY);
    if (!raw) return null;
    const parsed: Identity = JSON.parse(raw);
    return parsed.token && Date.parse(parsed.expiresAt) > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}

async function apiRequest<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API}${path}`, {
      ...init,
      headers: {
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new Error('Unable to reach the server. Check that the backend is running.');
  }

  if (!response.ok) {
    if (response.status === 404) throw new Error('That room could not be found. Double-check the code.');
    if (response.status === 401) throw new Error('Your session expired. Please try again.');
    if (response.status === 429) throw new Error('Too many requests. Please try again shortly.');
    throw new Error('Something went wrong. Please try again.');
  }
  return (await response.json()) as T;
}

function getInitialCode(): string {
  const match = window.location.pathname.match(/^\/room\/([A-Za-z0-9_-]+)\/?$/);
  return match?.[1]?.toUpperCase() ?? '';
}

export default function App() {
  const [mode, setMode] = useState<Mode>(getInitialCode() ? 'join' : 'create');
  const [name, setName] = useState(() => readIdentity()?.displayName ?? '');
  const [code, setCode] = useState(getInitialCode);
  const [roomName, setRoomName] = useState('');
  const [identity, setIdentity] = useState<Identity | null>(readIdentity);
  const [room, setRoom] = useState<Room | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [recent, setRecent] = useState<RecentRoom[]>(() => readRecentRooms());

  useEffect(() => {
    const onPopState = () => {
      const nextCode = getInitialCode();
      setCode(nextCode);
      setMode(nextCode ? 'join' : 'create');
      setRoom(null);
      setError('');
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  async function identify(displayName: string = name.trim()): Promise<Identity> {
    if (identity?.token && Date.parse(identity.expiresAt) > Date.now()) {
      if (identity.displayName === displayName) return identity;
    }
    const fresh = await apiRequest<Identity>('/auth/anonymous', {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    });
    try { localStorage.setItem(IDENTITY_KEY, JSON.stringify(fresh)); } catch { /* storage unavailable */ }
    setIdentity(fresh);
    return fresh;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void enterRoom(mode, code);
  }

  /** Reopens a room remembered on this device; the name comes from the saved identity. */
  function openRecent(roomCode: string) {
    const saved = name.trim() || identity?.displayName || '';
    setMode('join');
    setCode(roomCode);
    if (!saved) {
      setError('Enter your name first, then press Join study room.');
      return;
    }
    setName(saved);
    void enterRoom('join', roomCode, saved);
  }

  function dropRecent(roomCode: string) {
    forgetRoom(roomCode);
    setRecent(readRecentRooms());
  }

  async function enterRoom(entryMode: Mode, entryCode: string, nameOverride?: string) {
    setError('');
    const displayName = (nameOverride ?? name).trim();
    const inviteCode = entryCode.trim().toUpperCase();
    if (!displayName || displayName.length > 40) {
      setError('Please enter a name between 1 and 40 characters.');
      return;
    }
    if (entryMode === 'join' && !/^[A-Z0-9_-]{4,16}$/.test(inviteCode)) {
      setError('Enter a valid room code.');
      return;
    }

    setBusy(true);
    try {
      const user = await identify(displayName);
      const result = entryMode === 'create'
        ? await apiRequest<Room>('/rooms', { method: 'POST', ...(roomName.trim() ? { body: JSON.stringify({ name: roomName.trim() }) } : {}) }, user.token)
        : await apiRequest<Room>(`/rooms/${encodeURIComponent(inviteCode)}`, {}, user.token);
      rememberRoom({ code: result.code, expiresAt: result.expiresAt, owner: result.owner, name: result.name });
      setRecent(readRecentRooms());
      setRoom(result);
      setCode(result.code);
      window.history.pushState({}, '', `/room/${encodeURIComponent(result.code)}`);
    } catch (caught) {
      if (caught instanceof Error && caught.message.includes('session expired')) {
        try { localStorage.removeItem(IDENTITY_KEY); } catch { /* storage unavailable */ }
        setIdentity(null);
      }
      if (entryMode === 'join' && caught instanceof Error && caught.message.includes('could not be found')) {
        dropRecent(inviteCode);
      }
      setError(caught instanceof Error ? caught.message : 'Unexpected error. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  function leaveRoom() {
    setRoom(null);
    setCode('');
    setError('');
    setMode('create');
    window.history.pushState({}, '', '/');
  }


  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="/" onClick={(event) => { event.preventDefault(); leaveRoom(); }} aria-label="Study Room home">
          <span className="brand-mark"><img src="/logo.svg" alt="" width="34" height="34" style={{ display: 'block', borderRadius: 9 }} /></span>
          <span>studyroom<span className="brand-dot">.</span></span>
        </a>
        <span className="header-note"><span className="status-light" /> A little more focus, together.</span>
        <span className="header-pill"><LockKeyhole size={13} /> No account needed</span>
      </header>

      <main className="main-content">
        {room && identity ? (
          <RoomDashboard
            roomCode={room.code}
            roomName={room.name ?? undefined}
            token={identity.token}
            participantId={identity.participantId}
            isOwner={room.owner}
            displayName={identity.displayName}
            onLeave={leaveRoom}
          />
        ) : (
          <div className="landing-layout">
            <section className="hero" aria-labelledby="hero-title">
              <span className="eyebrow"><span className="eyebrow-line" /> YOUR NEW FAVORITE STUDY SPACE</span>
              <h1 id="hero-title">Good work happens <em>together.</em></h1>
              <p className="hero-subtitle">A calmer way to stay focused with friends. One shared timer, a little accountability, and room for your best work.</p>
              <div className="hero-highlights"><div><span className="highlight-icon"><Focus size={18} /></span><span>Stay in your flow</span></div><div><span className="highlight-icon"><Users size={18} /></span><span>Feel the company</span></div><div><span className="highlight-icon"><Check size={18} /></span><span>Make progress</span></div></div>
              <div className="hero-footnote"><span className="footnote-line" /> Just show up, settle in, and get things done.</div>
            </section>
            <section className="entry-panel" aria-label="Enter a study room">
              <div className="entry-top"><span className="entry-symbol"><BookOpen size={21} /></span><span className="entry-tag">START HERE</span></div>
              <h2>Your space to focus.</h2><p className="entry-description">A good session starts with a simple step.</p>
              <div className="mode-tabs" role="tablist" aria-label="Room action"><button role="tab" aria-selected={mode === 'create'} className={mode === 'create' ? 'selected' : ''} type="button" onClick={() => { setMode('create'); setError(''); }}><Plus size={16} /> Create room</button><button role="tab" aria-selected={mode === 'join'} className={mode === 'join' ? 'selected' : ''} type="button" onClick={() => { setMode('join'); setError(''); }}><DoorOpen size={16} /> Join room</button></div>
              <form onSubmit={handleSubmit} noValidate>
                <label className="field-label" htmlFor="display-name">WHAT SHOULD WE CALL YOU?</label>
                <input id="display-name" className="form-input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Your name" maxLength={40} autoComplete="nickname" required />
                {mode === 'create' && <><label className="field-label" htmlFor="room-name">ROOM NAME (OPTIONAL)</label><input id="room-name" className="form-input" value={roomName} onChange={(event) => setRoomName(event.target.value)} placeholder="e.g. Finals prep" maxLength={40} autoComplete="off" /></>}
                {mode === 'join' && <><label className="field-label" htmlFor="room-code">ROOM CODE</label><input id="room-code" className="form-input code-input" value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="e.g. A7BC92" maxLength={16} autoComplete="off" spellCheck={false} required /></>}
                {error && <p className="form-error" role="alert">{error}</p>}
                <button className="button button-dark submit-button" type="submit" disabled={busy}>{busy ? <><LoaderCircle className="spin" size={18} /> Connecting…</> : <>{mode === 'create' ? 'Create my study room' : 'Join study room'} <ArrowRight size={18} /></>}</button>
              </form>
              <p className="entry-bottom"><LockKeyhole size={13} /> Free to use · No sign-up required</p>
              <MyRooms rooms={recent} busy={busy} onOpen={openRecent} onForget={dropRecent} />
            </section>
          </div>
        )}
      </main>
      <footer className="site-footer"><span>© {new Date().getFullYear()} Study Room</span><span>Made for the moments that matter.</span></footer>
    </div>
  );
}
