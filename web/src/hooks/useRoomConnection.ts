import { useCallback, useEffect, useRef, useState } from 'react';
import { studyRoomApi, type PresenceSnapshot, type StudyTask, type TimerSnapshot } from '../lib/api';
import { connectRoomLive, type ConnectionState } from '../lib/websocket';

/**
 * Keeps a room's server-backed state synchronized over STOMP.
 * Events trigger a fresh authoritative snapshot; reconnections rehydrate all
 * three resources so missed events cannot permanently desynchronize the UI.
 */
export function useRoomConnection(roomCode: string, token: string) {
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [timer, setTimer] = useState<TimerSnapshot | null>(null);
  const [tasks, setTasks] = useState<StudyTask[]>([]);
  const [presence, setPresence] = useState<PresenceSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const generationRef = useRef(0);
  const mountedRef = useRef(false);

  const refresh = useCallback(async () => {
    if (!roomCode || !token) return;
    const generation = generationRef.current;
    try {
      const [nextTimer, nextTasks, nextPresence] = await Promise.all([
        studyRoomApi.getTimer(roomCode, token),
        studyRoomApi.listTasks(roomCode, token),
        studyRoomApi.getPresence(roomCode, token),
      ]);
      if (!mountedRef.current || generation !== generationRef.current) return;
      setTimer(nextTimer);
      setTasks(nextTasks);
      setPresence(nextPresence);
      setError(null);
    } catch (cause) {
      if (!mountedRef.current || generation !== generationRef.current) return;
      setError(cause instanceof Error ? cause.message : 'Unable to synchronize this room.');
    } finally {
      if (mountedRef.current && generation === generationRef.current) setLoading(false);
    }
  }, [roomCode, token]);

  useEffect(() => {
    mountedRef.current = true;
    generationRef.current += 1;
    setLoading(true);
    setError(null);
    setTimer(null);
    setTasks([]);
    setPresence(null);
    setConnectionState(roomCode && token ? 'connecting' : 'disconnected');

    if (!roomCode || !token) {
      setLoading(false);
      return () => {
        mountedRef.current = false;
        generationRef.current += 1;
      };
    }

    let cancelled = false;
    const connection = connectRoomLive(roomCode, token, {
      onConnectionChange: (state) => {
        if (!cancelled) setConnectionState(state);
      },
      onResync: refresh,
      onTimer: () => { if (!cancelled) void refresh(); },
      onTasks: () => { if (!cancelled) void refresh(); },
      onPresence: () => { if (!cancelled) void refresh(); },
      onError: (cause) => { if (!cancelled) setError(cause.message); },
    });

    // Initial snapshot should not depend on WebSocket availability.
    void refresh();

    return () => {
      cancelled = true;
      mountedRef.current = false;
      generationRef.current += 1;
      void connection.disconnect();
    };
  }, [roomCode, token, refresh]);

  return {
    connectionState,
    connected: connectionState === 'connected',
    timer,
    tasks,
    presence,
    loading,
    error,
    refresh,
  };
}
