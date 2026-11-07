import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RotateCw, House } from 'lucide-react';

type Props = {
  children: ReactNode;
  fallback?: ReactNode;
};

type State = {
  hasError: boolean;
};

/**
 * Catches unexpected rendering errors and offers a way to recover.
 * It does not intercept failed network requests or event-handler errors.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    if (import.meta.env.DEV) {
      console.error('Study Room rendering error:', error, info.componentStack);
    }
  }

  private retry = (): void => {
    this.setState({ hasError: false });
  };

  render() {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback !== undefined) return this.props.fallback;

    return (
      <main
        role="alert"
        style={{
          minHeight: '100dvh',
          display: 'grid',
          placeItems: 'center',
          padding: '24px',
          background: '#f8f8f4',
          color: '#283b2d',
          fontFamily: 'Manrope, system-ui, sans-serif',
        }}
      >
        <section
          style={{
            width: '100%',
            maxWidth: '440px',
            padding: 'clamp(28px, 6vw, 48px)',
            background: '#fff',
            border: '1px solid #e8ece5',
            borderRadius: '20px',
            boxShadow: '0 16px 48px rgba(32, 48, 35, 0.05)',
            textAlign: 'center',
          }}
        >
          <span
            aria-hidden="true"
            style={{
              width: '52px',
              height: '52px',
              margin: '0 auto 22px',
              display: 'grid',
              placeItems: 'center',
              color: '#8a6848',
              background: '#f6eee4',
              borderRadius: '15px',
            }}
          >
            <AlertTriangle size={23} strokeWidth={1.8} />
          </span>
          <h1 style={{ margin: '0 0 10px', fontSize: '25px', letterSpacing: '-0.045em' }}>
            Something went wrong
          </h1>
          <p style={{ margin: '0 0 28px', color: '#738174', fontSize: '14px', lineHeight: 1.7 }}>
            We couldn’t display this study space. Your shared room may still be active.
            Please try again.
          </p>
          <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={this.retry}
              style={{
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                padding: '12px 18px', border: 0, borderRadius: '10px',
                background: '#2f5940', color: 'white', fontWeight: 700, cursor: 'pointer',
              }}
            >
              <RotateCw size={16} /> Try again
            </button>
            <a
              href="/"
              style={{
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                padding: '12px 18px', borderRadius: '10px', border: '1px solid #e2e9e0',
                background: '#fff', color: '#2f5940', textDecoration: 'none', fontWeight: 700,
              }}
            >
              <House size={16} /> Home
            </a>
          </div>
        </section>
      </main>
    );
  }
}
