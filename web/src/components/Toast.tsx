import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

export type ToastVariant = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: string;
  message: string;
  variant: ToastVariant;
  duration?: number;
}

type ToastInput = Omit<ToastMessage, 'id'>;

const palette: Record<ToastVariant, { background: string; border: string; foreground: string }> = {
  success: { background: '#f3faf3', border: '#d6e9d7', foreground: '#396e45' },
  error: { background: '#fff5f3', border: '#f1d8d2', foreground: '#a04440' },
  info: { background: '#f5f7fa', border: '#dce4ed', foreground: '#45657d' },
};

function ToastItem({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: string) => void }) {
  useEffect(() => {
    if (toast.duration === 0) return;
    const timeout = window.setTimeout(() => onDismiss(toast.id), toast.duration ?? 4500);
    return () => window.clearTimeout(timeout);
  }, [toast.id, toast.duration, onDismiss]);

  const colors = palette[toast.variant];
  const Icon = toast.variant === 'success' ? CheckCircle2 : toast.variant === 'error' ? AlertCircle : Info;

  return (
    <div
      role={toast.variant === 'error' ? 'alert' : 'status'}
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 12, width: 'min(100%, 390px)',
        padding: '14px 15px', borderRadius: 12, background: colors.background,
        border: `1px solid ${colors.border}`, boxShadow: '0 10px 30px rgba(31, 49, 36, .09)',
        color: '#27362b', fontSize: 13, fontWeight: 600, lineHeight: 1.55,
      }}
    >
      <Icon size={19} color={colors.foreground} style={{ flexShrink: 0, marginTop: 1 }} aria-hidden="true" />
      <span style={{ flex: 1, overflowWrap: 'anywhere' }}>{toast.message}</span>
      <button
        type="button"
        aria-label="Dismiss notification"
        onClick={() => onDismiss(toast.id)}
        style={{ background: 'transparent', border: 0, padding: 2, color: '#758278', display: 'grid', placeItems: 'center' }}
      >
        <X size={17} aria-hidden="true" />
      </button>
    </div>
  );
}

/** A controlled notification viewport; render once near the root of the app. */
export function ToastViewport({ toasts, onDismiss }: {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}) {
  if (toasts.length === 0) return null;

  return (
    <div
      aria-label="Notifications"
      style={{
        position: 'fixed', zIndex: 1000, bottom: 'max(20px, env(safe-area-inset-bottom))',
        right: 20, left: 20, display: 'flex', flexDirection: 'column',
        alignItems: 'flex-end', gap: 10, pointerEvents: 'none',
      }}
    >
      {toasts.map((toast) => (
        <div key={toast.id} style={{ width: 'min(100%, 390px)', pointerEvents: 'auto' }}>
          <ToastItem toast={toast} onDismiss={onDismiss} />
        </div>
      ))}
    </div>
  );
}

/** Local toast state; add() returns an id for optional later dismissal. */
export function useToast() {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const dismiss = useCallback((id: string) => {
    setToasts((items) => items.filter((item) => item.id !== id));
  }, []);
  const add = useCallback((input: ToastInput) => {
    const id = crypto.randomUUID();
    setToasts((items) => [...items.slice(-3), { ...input, id }]);
    return id;
  }, []);

  return { toasts, addToast: add, dismissToast: dismiss };
}

export default ToastViewport;
