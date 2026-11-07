/** Reconnecting STOMP client for Study Room live updates.
 *
 * The HTTP API remains authoritative for a full room snapshot. After every
 * successful connection (including reconnection), reload the timer, tasks,
 * and presence via `onResync` to close gaps in event delivery.
 */
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import type { PresenceSnapshot, StudyTask, TimerState } from './api';

export type TaskEvent = {
  type: string;
  payload: StudyTask | { id: string } | unknown;
  serverTime: string;
};

export type ConnectionState = 'connecting' | 'connected' | 'disconnected';

export type RoomLiveHandlers = {
  onTimer?: (timer: TimerState) => void;
  onTasks?: (event: TaskEvent) => void;
  onPresence?: (presence: PresenceSnapshot) => void;
  onConnectionChange?: (state: ConnectionState) => void;
  /** Called after subscriptions are registered, including reconnections. */
  onResync?: () => void | Promise<void>;
  onError?: (error: Error) => void;
};

/**
 * An optional VITE_WS_URL may target a separate WS server. Otherwise use the
 * current origin, allowing Vite's /ws proxy in local development.
 */
function websocketUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined;
  if (configured?.trim()) {
    const url = new URL(configured, window.location.href);
    if (url.protocol !== 'ws:' && url.protocol !== 'wss:') {
      throw new Error('VITE_WS_URL must use ws:// or wss://');
    }
    return url.toString();
  }
  const url = new URL('/ws', window.location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}

function parseMessage<T>(message: IMessage, onValue: (value: T) => void, onError?: (error: Error) => void): void {
  try {
    onValue(JSON.parse(message.body) as T);
  } catch (error) {
    onError?.(error instanceof Error ? error : new Error('Invalid WebSocket event'));
  }
}

/** Connect to the three room topics. Call disconnect() on room exit/unmount. */
export function connectRoomLive(roomCode: string, token: string, handlers: RoomLiveHandlers = {}) {
  const code = roomCode.trim().toUpperCase();
  if (!/^[A-Z0-9_-]{4,16}$/.test(code)) throw new Error('Invalid room code');
  if (!token.trim()) throw new Error('A valid session token is required');

  const subscriptions: StompSubscription[] = [];
  let closed = false;
  let state: ConnectionState = 'connecting';

  const setState = (next: ConnectionState) => {
    if (state === next) return;
    state = next;
    handlers.onConnectionChange?.(next);
  };

  const client = new Client({
    brokerURL: websocketUrl(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    connectionTimeout: 10000,
    // Logging may contain sensitive message metadata; keep it off by default.
    debug: () => {},
    onConnect: () => {
      if (closed) return;
      for (const subscription of subscriptions.splice(0)) subscription.unsubscribe();
      const prefix = `/topic/rooms/${code}`;
      subscriptions.push(
        client.subscribe(`${prefix}/timer`, (message) =>
          parseMessage<TimerState>(message, (timer) => handlers.onTimer?.(timer), handlers.onError)),
        client.subscribe(`${prefix}/tasks`, (message) =>
          parseMessage<TaskEvent>(message, (event) => handlers.onTasks?.(event), handlers.onError)),
        client.subscribe(`${prefix}/presence`, (message) =>
          parseMessage<PresenceSnapshot>(message, (presence) => handlers.onPresence?.(presence), handlers.onError)),
      );
      setState('connected');
      // Rehydrate after missed messages during a disconnect or initial join.
      try {
        Promise.resolve(handlers.onResync?.()).catch((error: unknown) =>
          handlers.onError?.(error instanceof Error ? error : new Error('Room resync failed')),
        );
      } catch (error) {
        handlers.onError?.(error instanceof Error ? error : new Error('Room resync failed'));
      }
    },
    onWebSocketClose: () => { if (!closed) setState('disconnected'); },
    onWebSocketError: () => { if (!closed) setState('disconnected'); },
    onStompError: (frame) => {
      handlers.onError?.(new Error(frame.headers.message ?? 'WebSocket broker error'));
    },
  });

  handlers.onConnectionChange?.(state);
  client.activate();

  return {
    get state(): ConnectionState { return state; },
    get connected(): boolean { return client.connected; },
    async disconnect(): Promise<void> {
      if (closed) return;
      closed = true;
      // STOMP deactivation also cancels reconnect attempts.
      await client.deactivate();
      subscriptions.length = 0;
      setState('disconnected');
    },
  };
}

export type RoomLiveConnection = ReturnType<typeof connectRoomLive>;
