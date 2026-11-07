/**
 * Shared Study Room frontend types.
 *
 * Re-export the canonical API and real-time types instead of maintaining
 * duplicate definitions that can drift apart as the backend evolves.
 */
export type {
  AnonymousIdentity,
  StudyRoom,
  TimerPhase,
  TimerStatus,
  TimerState,
  TimerSnapshot,
  StudyTask,
  PresenceStatus,
  Participant,
  PresenceSnapshot,
} from '../lib/api';

export type {
  TaskEvent,
  ConnectionState,
  RoomLiveHandlers,
  RoomLiveConnection,
} from '../lib/websocket';
