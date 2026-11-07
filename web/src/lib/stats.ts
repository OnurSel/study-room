/**
 * Device-local focus statistics. There are no accounts, so the streak lives in
 * this browser only (localStorage) and is labelled as such in the UI.
 */
const STATS_KEY = 'studyroom.stats.v1';
const LAST_KEY = 'studyroom.stats.last.v1';

type Stats = { days: Record<string, number> };

/** Local calendar day as YYYY-MM-DD. */
export function dayKey(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function read(): Stats {
  try {
    const raw = localStorage.getItem(STATS_KEY);
    if (!raw) return { days: {} };
    const parsed = JSON.parse(raw) as Stats;
    return parsed && typeof parsed.days === 'object' && parsed.days ? parsed : { days: {} };
  } catch {
    return { days: {} };
  }
}

/**
 * Records a finished focus session. `marker` identifies the session
 * (room + count) so two tabs in the same browser never double-count it.
 */
export function recordFocus(seconds: number, marker: string, when: Date = new Date()): void {
  if (!(seconds > 0)) return;
  try {
    if (localStorage.getItem(LAST_KEY) === marker) return;
    localStorage.setItem(LAST_KEY, marker);
    const stats = read();
    const key = dayKey(when);
    stats.days[key] = (stats.days[key] ?? 0) + Math.round(seconds);
    localStorage.setItem(STATS_KEY, JSON.stringify(stats));
  } catch {
    /* storage unavailable: statistics are a convenience only */
  }
}

export function todaySeconds(when: Date = new Date()): number {
  return read().days[dayKey(when)] ?? 0;
}

/**
 * Consecutive days with at least one finished focus session. The streak stays
 * alive through today even if nothing was finished yet, so it only resets once
 * a whole day has been skipped.
 */
export function currentStreak(when: Date = new Date()): number {
  const { days } = read();
  const cursor = new Date(when.getFullYear(), when.getMonth(), when.getDate());
  const at = (key: string): number => days[key] ?? 0;
  if (at(dayKey(cursor)) <= 0) cursor.setDate(cursor.getDate() - 1);
  let streak = 0;
  while (at(dayKey(cursor)) > 0) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}
