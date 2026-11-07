/** Rooms visited on this device, so a closed tab can be reopened with one click. */
const KEY = 'studyroom.rooms.v1';
const MAX_ROOMS = 12;
/** Expired rooms stay visible as history for a while, then are dropped. */
const HISTORY_DAYS = 14;

export type RecentRoom = {
  code: string;
  /** ISO time of the last visit. */
  lastVisited: string;
  /** ISO time the room expires on the server. */
  expiresAt: string;
  owner: boolean;
  /** Optional friendly name given by the creator. */
  name?: string;
};

export function readRecentRooms(now: number = Date.now()): RecentRoom[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as RecentRoom[];
    if (!Array.isArray(parsed)) return [];
    const cutoff = now - HISTORY_DAYS * 24 * 60 * 60 * 1000;
    return parsed
      .filter((room) => typeof room.code === 'string' && Date.parse(room.expiresAt) > cutoff)
      .sort((a, b) => Date.parse(b.lastVisited) - Date.parse(a.lastVisited))
      .slice(0, MAX_ROOMS);
  } catch {
    return [];
  }
}

function write(rooms: RecentRoom[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(rooms.slice(0, MAX_ROOMS)));
  } catch {
    /* storage unavailable: the list is a convenience only */
  }
}

export function rememberRoom(room: { code: string; expiresAt: string; owner: boolean; name?: string | null }): void {
  const existing = readRecentRooms();
  const previous = existing.find((entry) => entry.code === room.code);
  const others = existing.filter((entry) => entry.code !== room.code);
  // A room created on this device stays "yours" even if a later lookup says otherwise.
  write([
    {
      code: room.code,
      expiresAt: room.expiresAt,
      owner: room.owner || !!previous?.owner,
      name: room.name || previous?.name || undefined,
      lastVisited: new Date().toISOString(),
    },
    ...others,
  ]);
}

export function forgetRoom(code: string): void {
  write(readRecentRooms().filter((entry) => entry.code !== code));
}

export function isRoomOpen(room: RecentRoom, now: number = Date.now()): boolean {
  return Date.parse(room.expiresAt) > now;
}
