import { useEffect, useMemo, useState } from 'react';
import type { TimerSnapshot } from '../types';

/**
 * Displays a server-authoritative timer without decrementing a local counter.
 * A snapshot sets the reference point; elapsed monotonic time only affects the
 * visual countdown. The next server snapshot always replaces that estimate.
 */
export function useRoomTimer(snapshot: TimerSnapshot | null) {
  const [reference, setReference] = useState(() => performance.now());
  const [tick, setTick] = useState(() => performance.now());

  useEffect(() => {
    const received = performance.now();
    setReference(received);
    setTick(received);
  }, [snapshot]);

  useEffect(() => {
    if (snapshot?.timer.status !== 'RUNNING') return;
    const intervalId = window.setInterval(() => setTick(performance.now()), 250);
    return () => window.clearInterval(intervalId);
  }, [snapshot?.timer.status]);

  return useMemo(() => {
    const timer = snapshot?.timer;
    const defaultDuration = 25 * 60;
    const durationSeconds = Math.max(0, timer?.durationSeconds ?? defaultDuration);
    const reportedRemaining = Math.max(0, snapshot?.remainingSeconds ?? durationSeconds);

    let remainingSeconds = reportedRemaining;
    if (timer?.status === 'RUNNING') {
      const serverTime = Date.parse(snapshot?.serverTime ?? '');
      const endsAt = Date.parse(timer.endsAt ?? '');
      if (Number.isFinite(serverTime) && Number.isFinite(endsAt)) {
        const initialRemainingMs = Math.max(0, endsAt - serverTime);
        const elapsedMs = Math.max(0, tick - reference);
        remainingSeconds = Math.ceil(Math.max(0, initialRemainingMs - elapsedMs) / 1000);
      } else {
        remainingSeconds = Math.ceil(
          Math.max(0, reportedRemaining * 1000 - Math.max(0, tick - reference)) / 1000,
        );
      }
    }

    remainingSeconds = Math.min(durationSeconds, remainingSeconds);
    const minutes = Math.floor(remainingSeconds / 60);
    const seconds = remainingSeconds % 60;
    return {
      remainingSeconds,
      durationSeconds,
      formattedTime: `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`,
      progress: durationSeconds > 0 ? (1 - remainingSeconds / durationSeconds) * 100 : 0,
      isRunning: timer?.status === 'RUNNING',
      phase: timer?.phase ?? 'FOCUS',
      status: timer?.status ?? 'IDLE',
    };
  }, [snapshot, reference, tick]);
}
