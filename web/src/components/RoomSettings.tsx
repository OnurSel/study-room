import { useEffect, useRef, useState } from 'react';
import { Check, Copy, ExternalLink, Settings2, Users, X } from 'lucide-react';

type RoomSettingsProps = {
  roomCode: string;
  participantCount?: number;
  onClose: () => void;
  onLeave?: () => void;
};

/**
 * Lightweight, accessible room information panel.
 * This component intentionally does not offer server-side settings that the
 * Study Room API does not currently support.
 */
export default function RoomSettings({
  roomCode,
  participantCount,
  onClose,
  onLeave,
}: RoomSettingsProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState('');
  const inviteLink = `${window.location.origin}/room/${encodeURIComponent(roomCode)}`;

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab') return;
      const focusables = Array.from(
        dialogRef.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]), a[href], input:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      );
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previousFocus?.focus();
    };
  }, [onClose]);

  async function copyInviteLink() {
    setCopyError('');
    try {
      await navigator.clipboard.writeText(inviteLink);
      setCopied(true);
    } catch {
      setCopied(false);
      setCopyError('Could not copy the link. Select and copy it manually.');
    }
  }

  return (
    <div
      className="room-settings-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      style={{
        position: 'fixed', inset: 0, zIndex: 1000, display: 'grid', placeItems: 'center',
        padding: 20, background: 'rgba(24, 37, 28, 0.48)', backdropFilter: 'blur(5px)',
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="room-settings-title"
        aria-describedby="room-settings-description"
        style={{
          width: '100%', maxWidth: 460, padding: 28, borderRadius: 20,
          background: '#ffffff', boxShadow: '0 24px 80px rgba(19, 37, 23, .18)',
          color: '#26392b',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
          <div>
            <div className="panel-kicker" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Settings2 size={16} /> ROOM DETAILS
            </div>
            <h2 id="room-settings-title" style={{ fontSize: 24, margin: '15px 0 5px', lineHeight: 1.2 }}>
              Your study space
            </h2>
            <p id="room-settings-description" style={{ margin: 0, color: '#748476', fontSize: 14 }}>
              Everything you need to invite your study partners.
            </p>
          </div>
          <button
            type="button" ref={closeRef} onClick={onClose}
            aria-label="Close room details"
            style={{ border: 0, background: '#f0f3ef', borderRadius: 10, padding: 9, cursor: 'pointer', display: 'flex' }}
          >
            <X size={18} />
          </button>
        </div>

        <div style={{ marginTop: 26, padding: 18, background: '#f6f8f4', borderRadius: 14 }}>
          <div style={{ color: '#748476', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em' }}>ROOM CODE</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 7, gap: 12 }}>
            <strong style={{ fontSize: 27, letterSpacing: '.14em', overflowWrap: 'anywhere' }}>{roomCode}</strong>
            {participantCount !== undefined && (
              <span style={{ display: 'inline-flex', gap: 5, alignItems: 'center', fontSize: 12, color: '#647767', whiteSpace: 'nowrap' }}>
                <Users size={15} /> {participantCount} here
              </span>
            )}
          </div>
        </div>

        <label htmlFor="room-settings-invite" style={{ display: 'block', fontWeight: 600, fontSize: 13, margin: '22px 0 8px' }}>
          Invite link
        </label>
        <input
          id="room-settings-invite" className="form-input" readOnly value={inviteLink}
          onFocus={(event) => event.target.select()}
          style={{ width: '100%', boxSizing: 'border-box', margin: 0 }}
        />
        <button
          className="button button-dark" type="button" onClick={() => void copyInviteLink()}
          style={{ width: '100%', justifyContent: 'center', marginTop: 12 }}
        >
          {copied ? <Check size={17} /> : <Copy size={17} />}
          {copied ? 'Link copied' : 'Copy invite link'}
        </button>
        {copyError && <p role="alert" className="form-error" style={{ marginTop: 10 }}>{copyError}</p>}

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginTop: 25, paddingTop: 18, borderTop: '1px solid #e8ece6' }}>
          <span style={{ color: '#829083', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <ExternalLink size={14} /> Share this code with friends
          </span>
          {onLeave && (
            <button type="button" className="text-button" onClick={onLeave} style={{ color: '#9b4941' }}>
              Leave room
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
