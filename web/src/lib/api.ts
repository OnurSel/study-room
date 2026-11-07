/** Typed HTTP client for the Study Room REST API. */
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '');

export type AnonymousIdentity = {
  participantId: string;
  displayName: string;
  token: string;
  tokenType: 'Bearer';
  expiresAt: string;
};

export type StudyRoom = {
  code: string;
  createdAt: string;
  expiresAt: string;
  /** True when the requesting participant created the room. */
  owner: boolean;
};

export type TimerPhase = 'FOCUS' | 'SHORT_BREAK' | 'LONG_BREAK';
export type TimerStatus = 'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED';

export type TimerState = {
  roomCode: string;
  phase: TimerPhase;
  status: TimerStatus;
  durationSeconds: number;
  remainingSeconds: number;
  startedAt: string | null;
  endsAt: string | null;
  version: number;
  focusSeconds: number;
  shortBreakSeconds: number;
  longBreakSeconds: number;
  autoAdvance: boolean;
  /** Completed focus sessions in this room (a long break follows every 4th). */
  completedFocus: number;
  /** Total completed focus time in this room, in seconds. */
  totalFocusSeconds: number;
};

export type TimerSettings = {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  autoAdvance: boolean;
};

export type TimerSnapshot = {
  timer: TimerState;
  serverTime: string;
  remainingSeconds: number;
};

export type StudyTask = {
  id: string;
  roomCode: string;
  title: string;
  completed: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type PresenceStatus = 'JOINED' | 'WORKING' | 'ON_BREAK';
export type Participant = {
  id: string;
  displayName: string;
  status: PresenceStatus;
  updatedAt: string;
};
export type PresenceSnapshot = {
  roomCode: string;
  participants: Participant[];
  serverTime: string;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown; token?: string };

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, token, headers, ...init } = options;
  const requestHeaders = new Headers(headers);
  if (body !== undefined) requestHeaders.set('Content-Type', 'application/json');
  if (token) requestHeaders.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: requestHeaders,
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }

  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Please check the information and try again.',
      401: 'Your session has expired. Please join again.',
      403: 'Only the room owner can do that.',
      404: 'This room or item could not be found.',
      409: 'This item changed. Refresh and try again.',
      429: 'Too many requests. Please try again shortly.',
    };
    throw new ApiError(response.status, messages[response.status] ?? 'Something went wrong. Please try again.');
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const roomPath = (code: string) => `/rooms/${encodeURIComponent(code.trim().toUpperCase())}`;
const taskPath = (code: string, id: string) => `${roomPath(code)}/tasks/${encodeURIComponent(id)}`;

export const studyRoomApi = {
  createIdentity: (displayName: string) =>
    apiRequest<AnonymousIdentity>('/auth/anonymous', { method: 'POST', body: { displayName } }),

  createRoom: (token: string) =>
    apiRequest<StudyRoom>('/rooms', { method: 'POST', token }),
  getRoom: (code: string, token: string) =>
    apiRequest<StudyRoom>(roomPath(code), { token }),

  getTimer: (code: string, token: string) =>
    apiRequest<TimerSnapshot>(`${roomPath(code)}/timer`, { token }),
  startTimer: (code: string, token: string) =>
    apiRequest<TimerSnapshot>(`${roomPath(code)}/timer/start`, { method: 'POST', token }),
  pauseTimer: (code: string, token: string) =>
    apiRequest<TimerSnapshot>(`${roomPath(code)}/timer/pause`, { method: 'POST', token }),
  resetTimer: (code: string, token: string) =>
    apiRequest<TimerSnapshot>(`${roomPath(code)}/timer/reset`, { method: 'POST', token }),
  updateTimerSettings: (code: string, settings: TimerSettings, token: string) =>
    apiRequest<TimerSnapshot>(`${roomPath(code)}/timer/settings`, {
      method: 'PUT', body: settings, token,
    }),
  setTimerPhase: (code: string, phase: TimerPhase, token: string) =>
    apiRequest<TimerSnapshot>(`${roomPath(code)}/timer/phase`, {
      method: 'POST', body: { phase }, token,
    }),

  listTasks: (code: string, token: string) =>
    apiRequest<StudyTask[]>(`${roomPath(code)}/tasks`, { token }),
  addTask: (code: string, title: string, token: string) =>
    apiRequest<StudyTask>(`${roomPath(code)}/tasks`, { method: 'POST', body: { title }, token }),
  setTaskCompleted: (code: string, id: string, completed: boolean, expectedVersion: number, token: string) =>
    apiRequest<StudyTask>(`${taskPath(code, id)}/completion`, {
      method: 'PATCH', body: { completed, expectedVersion }, token,
    }),
  renameTask: (code: string, id: string, title: string, expectedVersion: number, token: string) =>
    apiRequest<StudyTask>(`${taskPath(code, id)}/title`, {
      method: 'PATCH', body: { title, expectedVersion }, token,
    }),
  deleteTask: (code: string, id: string, expectedVersion: number, token: string) =>
    apiRequest<void>(`${taskPath(code, id)}?expectedVersion=${expectedVersion}`, {
      method: 'DELETE', token,
    }),

  joinRoom: (code: string, token: string) =>
    apiRequest<PresenceSnapshot>(`${roomPath(code)}/presence/join`, { method: 'POST', token }),
  getPresence: (code: string, token: string) =>
    apiRequest<PresenceSnapshot>(`${roomPath(code)}/presence`, { token }),
  setPresenceStatus: (code: string, status: PresenceStatus, token: string) =>
    apiRequest<PresenceSnapshot>(`${roomPath(code)}/presence/status`, {
      method: 'PUT', body: { status }, token,
    }),
  leaveRoom: (code: string, token: string) =>
    apiRequest<PresenceSnapshot>(`${roomPath(code)}/presence/leave`, { method: 'DELETE', token }),
};
