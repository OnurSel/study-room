import { LoaderCircle, Pause, Play, RotateCcw } from 'lucide-react';
import type { TimerPhase } from '../lib/api';

type TimerControlsProps = {
  phase: TimerPhase;
  isRunning: boolean;
  isBusy?: boolean;
  disabled?: boolean;
  onStart: () => void | Promise<void>;
  onPause: () => void | Promise<void>;
  onReset: () => void | Promise<void>;
  onPhaseChange: (phase: TimerPhase) => void | Promise<void>;
};

const PHASE_OPTIONS: ReadonlyArray<{ value: TimerPhase; label: string }> = [
  { value: 'FOCUS', label: 'Focus' },
  { value: 'SHORT_BREAK', label: 'Short break' },
  { value: 'LONG_BREAK', label: 'Long break' },
];

/** Controls are stateless: the server-authoritative timer lives in the parent. */
export default function TimerControls({
  phase,
  isRunning,
  isBusy = false,
  disabled = false,
  onStart,
  onPause,
  onReset,
  onPhaseChange,
}: TimerControlsProps) {
  const locked = disabled || isBusy;

  return (
    <div className="timer-controls">
      <div className="timer-mode-switch" role="group" aria-label="Timer mode">
        {PHASE_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={`timer-mode-button${phase === option.value ? ' is-active' : ''}`}
            aria-pressed={phase === option.value}
            disabled={locked}
            onClick={() => void onPhaseChange(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      <div className="timer-action-buttons">
        <button
          type="button"
          className="button button-dark"
          disabled={locked}
          aria-label={isRunning ? 'Pause timer' : 'Start timer'}
          onClick={() => void (isRunning ? onPause() : onStart())}
        >
          {isBusy ? (
            <LoaderCircle size={17} className="spin" aria-hidden="true" />
          ) : isRunning ? (
            <Pause size={17} aria-hidden="true" />
          ) : (
            <Play size={17} aria-hidden="true" />
          )}
          <span>{isRunning ? 'Pause' : 'Start focus'}</span>
        </button>
        <button
          type="button"
          className="button timer-reset-button"
          disabled={locked}
          onClick={() => void onReset()}
        >
          <RotateCcw size={17} aria-hidden="true" />
          <span>Reset</span>
        </button>
      </div>
    </div>
  );
}
