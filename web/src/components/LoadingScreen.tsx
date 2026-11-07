import { BookOpen, LoaderCircle } from 'lucide-react';

type LoadingScreenProps = {
  message?: string;
  fullscreen?: boolean;
};

/**
 * A calm, accessible loading state for initial room and session recovery.
 * The spinner is decorative; screen readers receive the status message.
 */
export default function LoadingScreen({
  message = 'Preparing your study space…',
  fullscreen = true,
}: LoadingScreenProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy="true"
      style={{
        minHeight: fullscreen ? '100dvh' : '260px',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '22px',
        padding: '32px 20px',
        background: fullscreen ? '#f8f8f4' : 'transparent',
        color: '#283b2d',
        textAlign: 'center',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          fontFamily: 'Manrope, sans-serif',
          fontSize: '23px',
          fontWeight: 800,
          letterSpacing: '-0.06em',
        }}
      >
        <span
          aria-hidden="true"
          style={{
            display: 'grid',
            placeItems: 'center',
            height: '42px',
            width: '42px',
            borderRadius: '13px',
            background: '#e8eee5',
            color: '#466e4f',
          }}
        >
          <BookOpen size={22} strokeWidth={2} />
        </span>
        <span>
          studyroom<span style={{ color: '#6a9a70' }}>.</span>
        </span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#77867b' }}>
        <LoaderCircle
          size={17}
          className="spin"
          aria-hidden="true"
          style={{ flexShrink: 0 }}
        />
        <span style={{ fontSize: '13px', fontWeight: 600 }}>{message}</span>
      </div>
    </div>
  );
}
