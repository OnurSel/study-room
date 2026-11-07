import { Users } from 'lucide-react';
import type { Participant, PresenceStatus } from '../lib/api';

type PresenceListProps = {
  participants: Participant[];
  participantId: string;
  loading?: boolean;
  updating?: boolean;
  onStatusChange?: (status: PresenceStatus) => void;
};

const STATUS_LABELS: Record<PresenceStatus, string> = {
  JOINED: 'In the room',
  WORKING: 'Focusing',
  ON_BREAK: 'On a break',
};

const STATUS_OPTIONS: { value: PresenceStatus; label: string }[] = [
  { value: 'JOINED', label: 'Available' },
  { value: 'WORKING', label: 'Focusing' },
  { value: 'ON_BREAK', label: 'On a break' },
];

/** A presentational member list; live updates are supplied by its parent. */
export default function PresenceList({
  participants,
  participantId,
  loading = false,
  updating = false,
  onStatusChange,
}: PresenceListProps) {
  const currentParticipant = participants.find((person) => person.id === participantId);

  return (
    <article className="panel members-panel" aria-labelledby="presence-heading">
      <div className="panel-kicker" id="presence-heading">
        <Users size={16} aria-hidden="true" />
        PEOPLE HERE
        <span style={{ marginLeft: 'auto' }} aria-label={`${participants.length} participants`}>
          {participants.length}
        </span>
      </div>

      {onStatusChange && currentParticipant && (
        <div style={{ marginTop: 22 }}>
          <label htmlFor="my-presence-status" style={{ display: 'block', marginBottom: 8, fontSize: 12, color: '#647568', fontWeight: 700 }}>
            Your status
          </label>
          <select
            id="my-presence-status"
            className="form-input"
            style={{ width: '100%', marginBottom: 0 }}
            value={currentParticipant.status}
            onChange={(event) => onStatusChange(event.target.value as PresenceStatus)}
            disabled={updating}
          >
            {STATUS_OPTIONS.map((option) => (
              <option value={option.value} key={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      )}

      {participants.length === 0 ? (
        <p className="preview-note" role="status">
          {loading ? 'Loading participants…' : 'Waiting for participants…'}
        </p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }} aria-label="Room participants">
          {participants.map((person) => (
            <li className="member" key={person.id}>
              <span className="member-avatar" aria-hidden="true">
                {person.displayName.trim().charAt(0).toLocaleUpperCase() || '?'}
              </span>
              <div>
                <strong title={person.displayName}>{person.displayName}</strong>
                <small>{STATUS_LABELS[person.status] ?? 'In the room'}</small>
              </div>
              {person.id === participantId && <span className="member-you">You</span>}
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}
