import { useState, type FormEvent } from 'react';
import { Check, ClipboardList, LoaderCircle, Plus, Trash2 } from 'lucide-react';
import type { StudyTask } from '../lib/api';

type TaskListProps = {
  tasks: StudyTask[];
  isBusy?: boolean;
  disabled?: boolean;
  onAdd: (title: string) => void | Promise<void>;
  onToggle: (task: StudyTask) => void | Promise<void>;
  onDelete: (task: StudyTask) => void | Promise<void>;
};

const MAX_TASK_LENGTH = 200;

/** Presentational shared-task component. The parent owns server state and version-conflict handling. */
export default function TaskList({
  tasks,
  isBusy = false,
  disabled = false,
  onAdd,
  onToggle,
  onDelete,
}: TaskListProps) {
  const [title, setTitle] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const locked = disabled || isBusy || submitting;
  const completed = tasks.filter((task) => task.completed).length;

  async function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (locked) return;
    const nextTitle = title.trim();
    if (!nextTitle) {
      setError('Enter a task to add it.');
      return;
    }
    if (nextTitle.length > MAX_TASK_LENGTH) {
      setError(`Use ${MAX_TASK_LENGTH} characters or fewer.`);
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await onAdd(nextTitle);
      setTitle('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not add the task. Try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <article className="panel" aria-labelledby="shared-task-heading">
      <div className="panel-kicker">
        <ClipboardList size={16} aria-hidden="true" />
        <span id="shared-task-heading">SHARED TASKS</span>
        <span style={{ marginLeft: 'auto', letterSpacing: 0 }} aria-label={`${completed} of ${tasks.length} tasks completed`}>
          {completed}/{tasks.length} done
        </span>
      </div>

      <form onSubmit={(event) => void handleAdd(event)} style={{ display: 'flex', gap: 9, marginTop: 22 }}>
        <label htmlFor="studyroom-new-task" className="sr-only">New task</label>
        <input
          id="studyroom-new-task"
          className="form-input"
          style={{ flex: 1, minWidth: 0, marginBottom: 0 }}
          placeholder="What needs to get done?"
          value={title}
          maxLength={MAX_TASK_LENGTH}
          disabled={locked}
          onChange={(event) => {
            setTitle(event.target.value);
            if (error) setError('');
          }}
        />
        <button
          type="submit"
          className="button button-dark"
          disabled={locked || !title.trim()}
          aria-label="Add task"
          style={{ minWidth: 52, padding: '0 15px' }}
        >
          {submitting ? <LoaderCircle className="spin" size={18} aria-hidden="true" /> : <Plus size={19} aria-hidden="true" />}
        </button>
      </form>

      {error && <p className="form-error" role="alert" style={{ marginTop: 12 }}>{error}</p>}

      {tasks.length === 0 ? (
        <div style={{ padding: '28px 8px 14px', textAlign: 'center' }}>
          <ClipboardList size={25} color="#9ba99d" aria-hidden="true" />
          <p className="preview-note">No tasks yet. Add the first one to get started.</p>
        </div>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0, margin: '14px 0 0' }} aria-label="Room tasks">
          {tasks.map((task) => (
            <li key={task.id} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '13px 0', borderBottom: '1px solid #edf0eb' }}>
              <button
                type="button"
                className="text-button"
                disabled={locked}
                aria-label={`${task.completed ? 'Mark incomplete' : 'Complete'}: ${task.title}`}
                aria-pressed={task.completed}
                onClick={() => void onToggle(task)}
                style={{ flexShrink: 0, color: task.completed ? '#528461' : '#9faea0' }}
              >
                <span style={{ width: 23, height: 23, display: 'grid', placeItems: 'center', border: `1.5px solid ${task.completed ? '#66916c' : '#cbd7ca'}`, background: task.completed ? '#e7f2e5' : 'white', borderRadius: 7 }}>
                  {task.completed && <Check size={14} aria-hidden="true" />}
                </span>
              </button>
              <span style={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere', fontSize: 13, color: task.completed ? '#98a59a' : '#34453a', textDecoration: task.completed ? 'line-through' : 'none' }}>
                {task.title}
              </span>
              <button
                type="button"
                className="text-button"
                disabled={locked}
                aria-label={`Delete task: ${task.title}`}
                onClick={() => void onDelete(task)}
                style={{ flexShrink: 0 }}
              >
                <Trash2 size={16} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}
