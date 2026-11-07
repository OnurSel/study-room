import { ArrowRight, Crown, History, X } from 'lucide-react';
import { isRoomOpen, type RecentRoom } from '../lib/recentRooms';

type Props = {
  rooms: RecentRoom[];
  busy: boolean;
  onOpen: (code: string) => void;
  onForget: (code: string) => void;
};

function relative(iso: string): string {
  const minutes = Math.round((Date.now() - Date.parse(iso)) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.round(hours / 24)} d ago`;
}

function remaining(iso: string): string {
  const minutes = Math.round((Date.parse(iso) - Date.now()) / 60000);
  if (minutes < 60) return `closes in ${Math.max(1, minutes)} min`;
  return `closes in ${Math.round(minutes / 60)} h`;
}

/** Open rooms first (one-click rejoin), then closed ones as history. */
export default function MyRooms({ rooms, busy, onOpen, onForget }: Props) {
  if (rooms.length === 0) return null;
  const open = rooms.filter((room) => isRoomOpen(room));
  const past = rooms.filter((room) => !isRoomOpen(room));

  return (
    <section style={{ marginTop: 22, paddingTop: 16, borderTop: '1px solid #eef0ea' }} aria-labelledby="my-rooms-title">
      <div className="panel-kicker" id="my-rooms-title"><History size={16} /> YOUR ROOMS ON THIS DEVICE</div>
      {open.length > 0 && (
        <ul style={{ listStyle: 'none', padding: 0, margin: '10px 0 0' }}>
          {open.map((room) => (
            <li key={room.code} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 0', borderTop: '1px solid #eef0ea' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, whiteSpace: 'nowrap' }}>
                  <strong style={{ letterSpacing: 1 }}>{room.code}</strong>
                  {room.owner && (
                    <span title="You created this room" aria-label="You created this room" style={{ color: '#8a6d2f', display: 'inline-flex' }}>
                      <Crown size={14} />
                    </span>
                  )}
                </span>
                <small style={{ display: 'block', color: '#8b978d' }}>
                  visited {relative(room.lastVisited)} · {remaining(room.expiresAt)}
                </small>
              </div>
              <button type="button" className="button button-dark" disabled={busy} onClick={() => onOpen(room.code)}>
                Rejoin <ArrowRight size={16} />
              </button>
              <button type="button" className="text-button" aria-label={`Remove room ${room.code} from this list`} onClick={() => onForget(room.code)}>
                <X size={15} />
              </button>
            </li>
          ))}
        </ul>
      )}
      {past.length > 0 && (
        <>
          <p className="preview-note" style={{ marginTop: 12 }}>Earlier rooms (closed)</p>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {past.map((room) => (
              <li key={room.code} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderTop: '1px solid #eef0ea', color: '#8b978d' }}>
                <span style={{ flex: 1, letterSpacing: 1 }}>
                  {room.code}{room.owner ? ' · yours' : ''} <small>· {relative(room.lastVisited)}</small>
                </span>
                <button type="button" className="text-button" aria-label={`Remove room ${room.code} from this list`} onClick={() => onForget(room.code)}>
                  <X size={15} />
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
