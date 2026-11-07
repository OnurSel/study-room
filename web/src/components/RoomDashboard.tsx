import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Check, Clock3, Copy, LoaderCircle, Pause, Play, Plus, RotateCcw, Trash2, Users, Wifi, WifiOff, X } from 'lucide-react';
import {
  studyRoomApi,
  type PresenceSnapshot,
  type StudyTask,
  type TimerPhase,
  type TimerSnapshot,
} from '../lib/api';
import { connectRoomLive, type ConnectionState } from '../lib/websocket';

type Props = {
  roomCode: string;
  token: string;
  participantId: string;
  displayName: string;
  onLeave: () => void;
};

const PHASES: { value: TimerPhase; label: string }[] = [
  { value: 'FOCUS', label: 'Focus' },
  { value: 'SHORT_BREAK', label: 'Short break' },
  { value: 'LONG_BREAK', label: 'Long break' },
];

function timerSeconds(snapshot: TimerSnapshot | null, now: number): number {
  if (!snapshot) return 25 * 60;
  if (snapshot.timer.status !== 'RUNNING' || !snapshot.timer.endsAt) {
    return Math.max(0, snapshot.remainingSeconds);
  }
  const serverAt = Date.parse(snapshot.serverTime);
  const endAt = Date.parse(snapshot.timer.endsAt);
  if (!Number.isFinite(serverAt) || !Number.isFinite(endAt)) return snapshot.remainingSeconds;
  // Estimate server time using the time elapsed on this client since the snapshot arrived.
  return Math.max(0, Math.ceil((endAt - (serverAt + now - snapshotReceivedAt(snapshot))) / 1000));
}

// The receipt timestamp is client-local and never changes the server's authority.
const receivedAt = new WeakMap<TimerSnapshot, number>();
function snapshotReceivedAt(snapshot: TimerSnapshot): number {
  return receivedAt.get(snapshot) ?? Date.now();
}
function stamp(snapshot: TimerSnapshot): TimerSnapshot {
  receivedAt.set(snapshot, Date.now());
  return snapshot;
}
function formattedTime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
function asMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

export default function RoomDashboard({ roomCode, token, participantId, displayName, onLeave }: Props) {
  const [timer, setTimer] = useState<TimerSnapshot | null>(null);
  const [tasks, setTasks] = useState<StudyTask[]>([]);
  const [presence, setPresence] = useState<PresenceSnapshot | null>(null);
  const [connection, setConnection] = useState<ConnectionState>('connecting');
  const [now, setNow] = useState(Date.now());
  const [taskTitle, setTaskTitle] = useState('');
  const [busy, setBusy] = useState(false);
  const [initializing, setInitializing] = useState(true);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const mounted = useRef(true);
  const refreshVersion = useRef(0);

  const refresh = useCallback(async () => {
    const version = ++refreshVersion.current;
    try {
      const [nextTimer, nextTasks, nextPresence] = await Promise.all([
        studyRoomApi.getTimer(roomCode, token),
        studyRoomApi.listTasks(roomCode, token),
        studyRoomApi.getPresence(roomCode, token),
      ]);
      if (!mounted.current || version !== refreshVersion.current) return;
      setTimer(stamp(nextTimer));
      setTasks(nextTasks);
      setPresence(nextPresence);
      setError('');
    } catch (caught) {
      if (mounted.current && version === refreshVersion.current) setError(asMessage(caught));
    } finally {
      if (mounted.current && version === refreshVersion.current) setInitializing(false);
    }
  }, [roomCode, token]);

  useEffect(() => {
    mounted.current = true;
    const interval = window.setInterval(() => setNow(Date.now()), 250);
    const live = connectRoomLive(roomCode, token, {
      onConnectionChange: setConnection,
      onResync: refresh,
      onTimer: () => { void refresh(); },
      onTasks: () => { void refresh(); },
      onPresence: () => { void refresh(); },
      onError: (caught) => setError(caught.message),
    });
    void studyRoomApi.joinRoom(roomCode, token).then(refresh).catch((caught: unknown) => {
      if (mounted.current) setError(asMessage(caught));
      void refresh();
    });
    return () => {
      mounted.current = false;
      refreshVersion.current++;
      window.clearInterval(interval);
      void live.disconnect();
      void studyRoomApi.leaveRoom(roomCode, token).catch(() => {});
    };
  }, [roomCode, token, refresh]);

  async function mutate<T>(operation: () => Promise<T>, onSuccess?: (value: T) => void) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const result = await operation();
      if (mounted.current) onSuccess?.(result);
      await refresh();
    } catch (caught) {
      if (mounted.current) {
        setError(asMessage(caught));
        await refresh(); // Resolve version conflicts with a fresh server snapshot.
      }
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  function submitTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const title = taskTitle.trim();
    if (!title || title.length > 200) return;
    void mutate(() => studyRoomApi.addTask(roomCode, title, token), () => setTaskTitle(''));
  }

  async function copyInvite() {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/room/${encodeURIComponent(roomCode)}`);
      setCopied(true);
      window.setTimeout(() => { if (mounted.current) setCopied(false); }, 1800);
    } catch {
      setError('Unable to copy the invite link. You can share the room code instead.');
    }
  }

  const remaining = timerSeconds(timer, now);
  const isRunning = timer?.timer.status === 'RUNNING';
  const total = timer?.timer.durationSeconds ?? 1500;
  const progress = total > 0 ? Math.min(100, Math.max(0, (1 - remaining / total) * 100)) : 0;
  const participants = presence?.participants ?? [];
  const completed = tasks.filter((task) => task.completed).length;

  return (
    <section className="room-layout" aria-labelledby="room-title">
      <div className="room-topline">
        <span className="eyebrow">YOUR STUDY SPACE</span>
        <button type="button" className="text-button" onClick={onLeave}><X size={16} /> Leave room</button>
      </div>
      <div className="room-heading">
        <div><h1 id="room-title">Let's make progress<span className="accent-period">.</span></h1><p>One room, one shared focus. Welcome, {displayName}.</p></div>
        <span className="room-live">{connection === 'connected' ? <Wifi size={15} /> : <WifiOff size={15} />}{connection === 'connected' ? 'Live & synced' : 'Reconnecting'}</span>
      </div>
      {error && <p className="form-error" role="alert" style={{ marginBottom: 18 }}>{error} <button type="button" className="text-button" onClick={() => void refresh()}>Retry</button></p>}
      <div className="room-content">
        <div style={{ display: 'grid', gap: 21, alignContent: 'start' }}>
          <article className="panel focus-panel">
            <div className="panel-kicker"><Clock3 size={16} /> SHARED TIMER</div>
            <div role="group" aria-label="Timer mode" style={{ display: 'flex', gap: 7, flexWrap: 'wrap', justifyContent: 'center', marginTop: 24 }}>
              {PHASES.map(({ value, label }) => <button key={value} type="button" disabled={busy || initializing} onClick={() => void mutate(() => studyRoomApi.setTimerPhase(roomCode, value, token))} className="text-button" aria-pressed={timer?.timer.phase === value} style={{ padding: '9px 13px', borderRadius: 30, background: timer?.timer.phase === value ? '#e8f1e6' : '#f7f8f5', color: timer?.timer.phase === value ? '#315c3a' : '#8b978d' }}>{label}</button>)}
            </div>
            <div className="timer-visual" style={{ background: `conic-gradient(#719a77 ${progress}%, #edf3eb ${progress}%)`, border: '12px solid #edf3eb', boxShadow: 'inset 0 0 0 8px white', minHeight: 240 }} aria-live="off">
              <span>{formattedTime(remaining)}</span>
              <small>{isRunning ? 'Session in progress' : timer?.timer.status === 'PAUSED' ? 'Paused' : 'Ready when you are'}</small>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 23 }}>
              <button type="button" className="button button-dark" disabled={busy || initializing} onClick={() => void mutate(() => isRunning ? studyRoomApi.pauseTimer(roomCode, token) : studyRoomApi.startTimer(roomCode, token))}>{busy ? <LoaderCircle size={17} className="spin" /> : isRunning ? <Pause size={17} /> : <Play size={17} />}{isRunning ? 'Pause' : 'Start focus'}</button>
              <button type="button" className="button" style={{ background: '#f1f5ef', color: '#385c41' }} disabled={busy || initializing} onClick={() => void mutate(() => studyRoomApi.resetTimer(roomCode, token))}><RotateCcw size={17} /> Reset</button>
            </div>
            <p className="preview-note" style={{ textAlign: 'center', marginTop: 20 }}>The timer is shared with everyone in this room.</p>
          </article>
          <article className="panel">
            <div className="panel-kicker"><Check size={16} /> SHARED TASKS <span style={{ marginLeft: 'auto', letterSpacing: 0 }}>{completed}/{tasks.length} done</span></div>
            <form onSubmit={submitTask} style={{ display: 'flex', gap: 9, marginTop: 22 }}>
              <input className="form-input" style={{ marginBottom: 0, minWidth: 0, flex: 1 }} placeholder="Add something to work on…" aria-label="New task" value={taskTitle} maxLength={200} onChange={(event) => setTaskTitle(event.target.value)} />
              <button type="submit" className="button button-dark" disabled={busy || !taskTitle.trim()} aria-label="Add task"><Plus size={18} /></button>
            </form>
            {tasks.length === 0 && <p className="preview-note" style={{ paddingTop: 14 }}>No tasks yet. Add your first goal to get started.</p>}
            <ul style={{ padding: 0, margin: '15px 0 0', listStyle: 'none' }}>
              {tasks.map((task) => <li key={task.id} style={{ borderTop: '1px solid #eef0ea', display: 'flex', alignItems: 'center', gap: 10, padding: '14px 0' }}>
                <input type="checkbox" aria-label={`Mark ${task.title} as ${task.completed ? 'incomplete' : 'complete'}`} checked={task.completed} disabled={busy} onChange={(event) => void mutate(() => studyRoomApi.setTaskCompleted(roomCode, task.id, event.target.checked, task.version, token))} style={{ accentColor: '#4f8058', width: 17, height: 17 }} />
                <span style={{ flex: 1, fontSize: 13, color: task.completed ? '#9da99d' : '#314235', textDecoration: task.completed ? 'line-through' : 'none', overflowWrap: 'anywhere' }}>{task.title}</span>
                <button type="button" className="text-button" aria-label={`Delete ${task.title}`} disabled={busy} onClick={() => void mutate(() => studyRoomApi.deleteTask(roomCode, task.id, task.version, token))}><Trash2 size={16} /></button>
              </li>)}
            </ul>
          </article>
        </div>
        <aside className="room-side">
          <article className="panel invite-panel">
            <div className="panel-kicker"><Copy size={16} /> INVITE FRIENDS</div>
            <h2>Better together.</h2><p>Send your room link and focus alongside your friends.</p>
            <div className="invite-code"><span>{roomCode}</span><button type="button" onClick={() => void copyInvite()} aria-label="Copy invite link">{copied ? <Check size={19} /> : <Copy size={19} />}</button></div>
            <button className="button button-dark" type="button" onClick={() => void copyInvite()}>{copied ? 'Copied!' : 'Copy invite link'} {copied ? <Check size={17} /> : <Copy size={17} />}</button>
          </article>
          <article className="panel members-panel">
            <div className="panel-kicker"><Users size={16} /> PEOPLE HERE <span style={{ marginLeft: 'auto' }}>{participants.length}</span></div>
            {participants.length === 0 && <p className="preview-note">{initializing ? 'Loading participants…' : 'Waiting for participants…'}</p>}
            {participants.map((person) => <div className="member" key={person.id}>
              <span className="member-avatar">{person.displayName.charAt(0).toUpperCase()}</span>
              <div><strong>{person.displayName}</strong><small>{person.status === 'ON_BREAK' ? 'On a break' : person.status === 'WORKING' ? 'Focusing' : 'In the room'}</small></div>
              {person.id === participantId && <span className="member-you">You</span>}
            </div>)}
          </article>
        </aside>
      </div>
    </section>
  );
}
