import { useEffect, useState, type FormEvent } from 'react';
import { Crown, LoaderCircle, Settings2 } from 'lucide-react';
import type { TimerSettings, TimerState } from '../lib/api';

type Props = {
  timer: TimerState | null;
  isOwner: boolean;
  busy: boolean;
  onSave: (settings: TimerSettings) => void;
};

const LIMITS = { focus: 180, shortBreak: 60, longBreak: 120 } as const;

const PRESETS: { label: string; focus: number; shortBreak: number; longBreak: number }[] = [
  { label: '25 / 5', focus: 25, shortBreak: 5, longBreak: 15 },
  { label: '50 / 10', focus: 50, shortBreak: 10, longBreak: 20 },
  { label: '90 / 20', focus: 90, shortBreak: 20, longBreak: 30 },
];

/** Keeps what the user is typing (digits only, may be empty while editing). */
function digitsOnly(raw: string): string {
  return raw.replace(/\D/g, '').slice(0, 3);
}

/** Minutes for a field's text, or null while it is empty or out of range. */
function parseMinutes(text: string, max: number): number | null {
  if (text === '') return null;
  const value = Number(text);
  return value >= 1 && value <= max ? value : null;
}

/** Applied when the field loses focus: empty falls back, the rest is pulled into range. */
function settle(text: string, max: number, fallback: number): string {
  if (text === '') return String(fallback);
  return String(Math.min(max, Math.max(1, Number(text))));
}

/** Room owner edits the shared lengths; everyone else sees them read-only. */
export default function TimerSettingsPanel({ timer, isOwner, busy, onSave }: Props) {
  // Text state so a field can be cleared and retyped without snapping back to 1.
  const [focusText, setFocusText] = useState('25');
  const [shortBreakText, setShortBreakText] = useState('5');
  const [longBreakText, setLongBreakText] = useState('15');
  const [auto, setAuto] = useState(false);

  const serverFocus = timer ? Math.round(timer.focusSeconds / 60) : 25;
  const serverShortBreak = timer ? Math.round(timer.shortBreakSeconds / 60) : 5;
  const serverLongBreak = timer ? Math.round(timer.longBreakSeconds / 60) : 15;

  // Follow the server's values whenever they change (e.g. another tab saved them).
  useEffect(() => {
    if (!timer) return;
    setFocusText(String(serverFocus));
    setShortBreakText(String(serverShortBreak));
    setLongBreakText(String(serverLongBreak));
    setAuto(timer.autoAdvance);
  }, [timer?.focusSeconds, timer?.shortBreakSeconds, timer?.longBreakSeconds, timer?.autoAdvance]); // eslint-disable-line react-hooks/exhaustive-deps

  const focus = parseMinutes(focusText, LIMITS.focus);
  const shortBreak = parseMinutes(shortBreakText, LIMITS.shortBreak);
  const longBreak = parseMinutes(longBreakText, LIMITS.longBreak);
  const valid = focus !== null && shortBreak !== null && longBreak !== null;

  const dirty = !!timer && valid && (
    focus * 60 !== timer.focusSeconds || shortBreak * 60 !== timer.shortBreakSeconds
    || longBreak * 60 !== timer.longBreakSeconds || auto !== timer.autoAdvance
  );

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (focus === null || shortBreak === null || longBreak === null) return;
    onSave({ focusMinutes: focus, shortBreakMinutes: shortBreak, longBreakMinutes: longBreak, autoAdvance: auto });
  }

  if (!timer) return null;

  if (!isOwner) {
    return (
      <article className="panel">
        <div className="panel-kicker"><Settings2 size={16} /> TIMER SETTINGS</div>
        <p style={{ margin: '14px 0 4px', fontSize: 14 }}>
          Focus <strong>{serverFocus} min</strong> · Short break <strong>{serverShortBreak} min</strong> · Long break <strong>{serverLongBreak} min</strong>
        </p>
        <p className="preview-note">
          {auto ? 'Phases follow each other automatically. ' : ''}Only the person who created this room can change these.
        </p>
      </article>
    );
  }

  const numberStyle = { marginBottom: 0, minWidth: 0, width: '100%' } as const;
  return (
    <article className="panel">
      <div className="panel-kicker">
        <Settings2 size={16} /> TIMER SETTINGS
        <span style={{ marginLeft: 'auto', letterSpacing: 0, color: '#8a6d2f', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <Crown size={13} /> you own this room
        </span>
      </div>
      <div role="group" aria-label="Presets" style={{ display: 'flex', gap: 7, flexWrap: 'wrap', marginTop: 16 }}>
        {PRESETS.map((preset) => (
          <button
            key={preset.label}
            type="button"
            className="text-button"
            style={{ padding: '7px 12px', borderRadius: 30, background: '#f7f8f5' }}
            onClick={() => {
              setFocusText(String(preset.focus));
              setShortBreakText(String(preset.shortBreak));
              setLongBreakText(String(preset.longBreak));
            }}
          >
            {preset.label}
          </button>
        ))}
      </div>
      <form onSubmit={submit} style={{ marginTop: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 10 }}>
          <label style={{ fontSize: 12 }}>Focus (min)
            <input className="form-input" style={numberStyle} type="text" inputMode="numeric" pattern="[0-9]*" maxLength={3}
              title={`1–${LIMITS.focus} minutes`} value={focusText}
              onChange={(event) => setFocusText(digitsOnly(event.target.value))}
              onBlur={() => setFocusText(settle(focusText, LIMITS.focus, serverFocus))} />
          </label>
          <label style={{ fontSize: 12 }}>Short break
            <input className="form-input" style={numberStyle} type="text" inputMode="numeric" pattern="[0-9]*" maxLength={3}
              title={`1–${LIMITS.shortBreak} minutes`} value={shortBreakText}
              onChange={(event) => setShortBreakText(digitsOnly(event.target.value))}
              onBlur={() => setShortBreakText(settle(shortBreakText, LIMITS.shortBreak, serverShortBreak))} />
          </label>
          <label style={{ fontSize: 12 }}>Long break
            <input className="form-input" style={numberStyle} type="text" inputMode="numeric" pattern="[0-9]*" maxLength={3}
              title={`1–${LIMITS.longBreak} minutes`} value={longBreakText}
              onChange={(event) => setLongBreakText(digitsOnly(event.target.value))}
              onBlur={() => setLongBreakText(settle(longBreakText, LIMITS.longBreak, serverLongBreak))} />
          </label>
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: 9, marginTop: 14, fontSize: 13 }}>
          <input type="checkbox" checked={auto} onChange={(event) => setAuto(event.target.checked)} style={{ accentColor: '#4f8058', width: 16, height: 16 }} />
          Start the next phase automatically (a long break follows every 4th focus)
        </label>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 14, flexWrap: 'wrap' }}>
          <button type="submit" className="button button-dark" style={{ whiteSpace: 'nowrap' }} disabled={busy || !dirty}>
            {busy ? <LoaderCircle size={16} className="spin" /> : null} Save settings
          </button>
          <span className="preview-note" style={{ margin: 0, flex: '1 1 200px' }}>
            A running timer keeps its time; new lengths apply to the next phase or after Reset.
          </span>
        </div>
      </form>
    </article>
  );
}
