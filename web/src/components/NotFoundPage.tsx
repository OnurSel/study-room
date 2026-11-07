import { ArrowLeft, BookOpen, Compass } from 'lucide-react';

/** Friendly fallback for unknown routes. The parent router decides when to render it. */
export default function NotFoundPage() {
  return (
    <main
      style={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        padding: '32px 20px',
        background: '#f8f8f4',
        color: '#26302a',
        fontFamily: "'DM Sans', system-ui, sans-serif",
      }}
    >
      <section style={{ width: '100%', maxWidth: 490, textAlign: 'center' }}>
        <a
          href="/"
          aria-label="Study Room home"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            marginBottom: 45,
            fontSize: 19,
            fontWeight: 800,
            letterSpacing: '-0.05em',
          }}
        >
          <span style={{ display: 'grid', placeItems: 'center', width: 39, height: 39, borderRadius: 12, background: '#e8eee5', color: '#466e4f' }}>
            <BookOpen size={20} strokeWidth={2} />
          </span>
          studyroom<span style={{ color: '#6a9a70', marginLeft: -10 }}>.</span>
        </a>

        <div
          aria-hidden="true"
          style={{
            width: 76,
            height: 76,
            margin: '0 auto 25px',
            display: 'grid',
            placeItems: 'center',
            borderRadius: 22,
            background: '#eaf0e7',
            color: '#50775b',
          }}
        >
          <Compass size={35} strokeWidth={1.6} />
        </div>

        <div style={{ fontSize: 12, fontWeight: 800, color: '#739477', letterSpacing: '0.18em', marginBottom: 13 }}>404 — PAGE NOT FOUND</div>
        <h1 style={{ fontFamily: "'Manrope', system-ui, sans-serif", fontSize: 'clamp(32px, 7vw, 47px)', lineHeight: 1.15, letterSpacing: '-0.06em', margin: '0 0 16px' }}>
          Looks like you lost your place.
        </h1>
        <p style={{ color: '#7b887e', fontSize: 15, lineHeight: 1.8, margin: '0 auto 30px', maxWidth: 390 }}>
          This page doesn’t exist, or the link may have changed. Your next focus session is just a click away.
        </p>
        <a
          href="/"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 10,
            minHeight: 49,
            padding: '0 22px',
            borderRadius: 10,
            background: '#294a35',
            color: '#fff',
            fontSize: 13,
            fontWeight: 700,
          }}
        >
          <ArrowLeft size={17} /> Back to home
        </a>
      </section>
    </main>
  );
}
