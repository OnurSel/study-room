/** Sound and browser notifications for finished phases. Both are opt-out and best-effort. */
const SOUND_KEY = 'studyroom.sound.v1';
const NOTIFY_KEY = 'studyroom.notify.v1';

function getFlag(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    return raw === null ? fallback : raw === '1';
  } catch {
    return fallback;
  }
}
function setFlag(key: string, value: boolean): void {
  try { localStorage.setItem(key, value ? '1' : '0'); } catch { /* ignore */ }
}

export const soundEnabled = () => getFlag(SOUND_KEY, true);
export const setSoundEnabled = (value: boolean) => setFlag(SOUND_KEY, value);
export const notifyEnabled = () => getFlag(NOTIFY_KEY, false)
  && typeof Notification !== 'undefined' && Notification.permission === 'granted';

/** Asks for permission (must be called from a user gesture) and remembers the choice. */
export async function enableNotifications(): Promise<boolean> {
  if (typeof Notification === 'undefined') return false;
  const permission = Notification.permission === 'granted'
    ? 'granted' : await Notification.requestPermission();
  const ok = permission === 'granted';
  setFlag(NOTIFY_KEY, ok);
  return ok;
}
export function disableNotifications(): void { setFlag(NOTIFY_KEY, false); }

let audio: AudioContext | null = null;

/** A short, gentle two-note chime generated with the Web Audio API (no audio files). */
export function playChime(): void {
  if (!soundEnabled()) return;
  try {
    const Ctx = window.AudioContext
      ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    audio = audio ?? new Ctx();
    const ctx = audio;
    void ctx.resume();
    [660, 880].forEach((freq, index) => {
      const start = ctx.currentTime + index * 0.22;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.18, start + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.5);
      osc.connect(gain).connect(ctx.destination);
      osc.start(start);
      osc.stop(start + 0.55);
    });
  } catch {
    /* audio is optional */
  }
}

export function notify(title: string, body: string): void {
  if (!notifyEnabled()) return;
  try {
    new Notification(title, { body, tag: 'studyroom-phase' });
  } catch {
    /* notifications are optional */
  }
}
