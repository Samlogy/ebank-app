import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="container-content flex min-h-[60vh] flex-col items-center justify-center py-24 text-center">
      <p className="font-mono text-sm text-accent">404</p>
      <h1 className="mt-3 text-2xl font-semibold text-ink">This page took an unplanned outage.</h1>
      <p className="mt-2 text-ink-soft">The route you asked for isn&apos;t in the routing table.</p>
      <Link
        href="/"
        className="mt-8 inline-flex items-center gap-2 rounded-md border border-line px-5 py-2.5 text-sm font-medium text-ink transition-colors hover:border-accent hover:text-accent"
      >
        ← Back to safety
      </Link>
    </div>
  );
}
