import { Wifi, WifiOff, LoaderCircle } from 'lucide-react';
import type { ConnectionState } from '../lib/websocket';

type ConnectionStatusProps = {
  state: ConnectionState;
  /** Optional shorter text for compact headers. */
  compact?: boolean;
  className?: string;
};

/**
 * Accessible, read-only indicator for the room's real-time connection.
 * This component never claims that REST synchronization succeeded: it only
 * reflects the STOMP connection state reported by the connection client.
 */
export default function ConnectionStatus({
  state,
  compact = false,
  className = '',
}: ConnectionStatusProps) {
  const config = {
    connected: {
      label: compact ? 'Live' : 'Live connection',
      description: 'Real-time connection established',
      Icon: Wifi,
      color: 'var(--connection-online, #23795a)',
      background: 'var(--connection-online-bg, rgba(35, 121, 90, 0.09))',
    },
    connecting: {
      label: compact ? 'Connecting' : 'Connecting…',
      description: 'Connecting to the study room',
      Icon: LoaderCircle,
      color: 'var(--connection-pending, #956b26)',
      background: 'var(--connection-pending-bg, rgba(149, 107, 38, 0.09))',
    },
    disconnected: {
      label: compact ? 'Offline' : 'Connection lost',
      description: 'Real-time connection is unavailable',
      Icon: WifiOff,
      color: 'var(--connection-offline, #a64745)',
      background: 'var(--connection-offline-bg, rgba(166, 71, 69, 0.09))',
    },
  }[state];

  const { Icon } = config;

  return (
    <span
      className={className}
      role="status"
      aria-label={config.description}
      title={config.description}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.45rem',
        width: 'fit-content',
        padding: '0.4rem 0.7rem',
        borderRadius: '999px',
        color: config.color,
        background: config.background,
        fontSize: '0.78rem',
        fontWeight: 600,
        lineHeight: 1.2,
        whiteSpace: 'nowrap',
      }}
    >
      <Icon size={14} strokeWidth={2} aria-hidden="true" />
      <span>{config.label}</span>
    </span>
  );
}
