import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, X } from 'lucide-react';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Accessible confirmation modal for actions such as leaving a room or deleting a task. */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const cancelCallback = useRef(onCancel);
  cancelCallback.current = onCancel;

  useEffect(() => {
    if (!open) return;

    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cancelRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        cancelCallback.current();
      }
      if (event.key !== 'Tab') return;

      const focusables = Array.from(
        dialogRef.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      );
      if (!focusables.length) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [open]);

  if (!open || typeof document === 'undefined') return null;

  return createPortal(
    <div
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !loading) onCancel();
      }}
      style={{
        position: 'fixed', inset: 0, zIndex: 1100, background: 'rgba(22, 37, 26, .46)',
        backdropFilter: 'blur(5px)', display: 'grid', placeItems: 'center',
        padding: 'max(20px, env(safe-area-inset-top)) 20px max(20px, env(safe-area-inset-bottom))',
      }}
    >
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        style={{
          background: '#fff', color: '#28382d', border: '1px solid #e7ece5', borderRadius: 19,
          boxShadow: '0 30px 85px rgba(17, 35, 21, .21)', width: 'min(100%, 440px)',
          padding: '28px', position: 'relative',
        }}
      >
        <button
          type="button"
          aria-label="Close dialog"
          onClick={onCancel}
          disabled={loading}
          style={{ position: 'absolute', top: 17, right: 17, border: 0, background: 'transparent', color: '#839087', padding: 5, display: 'grid', placeItems: 'center' }}
        >
          <X size={19} aria-hidden="true" />
        </button>
        <div
          aria-hidden="true"
          style={{ width: 44, height: 44, borderRadius: 13, background: destructive ? '#fff1ed' : '#eaf3e9', color: destructive ? '#ae5346' : '#507a59', display: 'grid', placeItems: 'center', marginBottom: 19 }}
        >
          <AlertTriangle size={21} strokeWidth={1.8} />
        </div>
        <h2 id={titleId} style={{ fontFamily: 'Manrope, sans-serif', fontWeight: 800, fontSize: 21, letterSpacing: '-.04em', margin: '0 28px 10px 0' }}>{title}</h2>
        <p id={descriptionId} style={{ color: '#7b8980', fontSize: 13, lineHeight: 1.8, margin: '0 0 27px' }}>{description}</p>
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'flex-end', gap: 10 }}>
          <button ref={cancelRef} type="button" className="button" disabled={loading} onClick={onCancel} style={{ background: '#f1f5ef', color: '#405e46', minHeight: 43 }}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={destructive ? 'button' : 'button button-dark'}
            disabled={loading}
            onClick={onConfirm}
            style={{ minHeight: 43, ...(destructive ? { color: '#fff', background: '#a94840' } : {}) }}
          >
            {loading ? 'Please waitâ€¦' : confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

export default ConfirmDialog;
