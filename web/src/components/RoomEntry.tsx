import { useState, type FormEvent } from 'react';
import {
  ArrowRight,
  BookOpen,
  DoorOpen,
  LoaderCircle,
  LockKeyhole,
  Plus,
} from 'lucide-react';
import {
  studyRoomApi,
  type AnonymousIdentity,
  type StudyRoom,
} from '../lib/api';

export type RoomEntryResult = {
  identity: AnonymousIdentity;
  room: StudyRoom;
};

type RoomEntryProps = {
  onEntered: (result: RoomEntryResult) => void;
  initialCode?: string;
  initialName?: string;
};

type EntryMode = 'create' | 'join';

/**
 * Account-free room entry. The parent owns navigation and the active room.
 * Keep this component focused on a single, accessible entry form.
 */
export default function RoomEntry({
  onEntered,
  initialCode = '',
  initialName = '',
}: RoomEntryProps) {
  const [mode, setMode] = useState<EntryMode>(initialCode ? 'join' : 'create');
  const [displayName, setDisplayName] = useState(initialName);
  const [roomCode, setRoomCode] = useState(initialCode.toUpperCase());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const name = displayName.trim();
    const code = roomCode.trim().toUpperCase();
    if (!name || name.length > 40) {
      setError('Please enter a name between 1 and 40 characters.');
      return;
    }
    if (mode === 'join' && !/^[A-Z0-9_-]{4,16}$/.test(code)) {
      setError('Enter a valid room code (4–16 characters).');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const identity = await studyRoomApi.createIdentity(name);
      const room = mode === 'create'
        ? await studyRoomApi.createRoom(identity.token)
        : await studyRoomApi.getRoom(code, identity.token);
      onEntered({ identity, room });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not enter the room. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  function changeMode(nextMode: EntryMode) {
    if (submitting) return;
    setMode(nextMode);
    setError('');
  }

  return (
    <section className="entry-panel" aria-labelledby="room-entry-title">
      <div className="entry-top">
        <span className="entry-symbol" aria-hidden="true"><BookOpen size={21} /></span>
        <span className="entry-tag">START HERE</span>
      </div>

      <h2 id="room-entry-title">Your space to focus.</h2>
      <p className="entry-description">A good session starts with a simple step.</p>

      <div className="mode-tabs" role="group" aria-label="Choose a room action">
        <button
          type="button"
          className={mode === 'create' ? 'selected' : ''}
          aria-pressed={mode === 'create'}
          disabled={submitting}
          onClick={() => changeMode('create')}
        >
          <Plus size={16} aria-hidden="true" /> Create room
        </button>
        <button
          type="button"
          className={mode === 'join' ? 'selected' : ''}
          aria-pressed={mode === 'join'}
          disabled={submitting}
          onClick={() => changeMode('join')}
        >
          <DoorOpen size={16} aria-hidden="true" /> Join room
        </button>
      </div>

      <form onSubmit={handleSubmit} noValidate>
        <label className="field-label" htmlFor="entry-display-name">WHAT SHOULD WE CALL YOU?</label>
        <input
          id="entry-display-name"
          className="form-input"
          type="text"
          value={displayName}
          maxLength={40}
          autoComplete="nickname"
          placeholder="Your name"
          disabled={submitting}
          onChange={(event) => { setDisplayName(event.target.value); setError(''); }}
          aria-invalid={Boolean(error) && !displayName.trim()}
          required
        />

        {mode === 'join' && (
          <>
            <label className="field-label" htmlFor="entry-room-code">ROOM CODE</label>
            <input
              id="entry-room-code"
              className="form-input code-input"
              type="text"
              value={roomCode}
              maxLength={16}
              autoCapitalize="characters"
              autoComplete="off"
              spellCheck={false}
              placeholder="e.g. A7BC92"
              disabled={submitting}
              onChange={(event) => { setRoomCode(event.target.value.toUpperCase()); setError(''); }}
              required
            />
          </>
        )}

        {error && <p className="form-error" role="alert">{error}</p>}

        <button className="button button-dark submit-button" type="submit" disabled={submitting}>
          {submitting ? (
            <><LoaderCircle className="spin" size={18} aria-hidden="true" /> Connecting…</>
          ) : (
            <>
              {mode === 'create' ? 'Create my study room' : 'Join study room'}
              <ArrowRight size={18} aria-hidden="true" />
            </>
          )}
        </button>
      </form>

      <p className="entry-bottom"><LockKeyhole size={13} aria-hidden="true" /> Free to use · No sign-up required</p>
    </section>
  );
}
