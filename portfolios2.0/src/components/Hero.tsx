import Link from 'next/link';
import { site } from '@/lib/site';
import { Reveal } from './Reveal';

export function Hero() {
  return (
    <section className="relative overflow-hidden">
      {/* Subtle animated gradient wash behind the hero — no particles, no video. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 opacity-70 [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)]"
      >
        <div className="absolute inset-0 animate-gradient-pan bg-[linear-gradient(110deg,transparent,rgba(94,234,212,0.10),transparent,rgba(45,212,191,0.08),transparent)] bg-[length:200%_200%]" />
      </div>

      <div className="container-content py-24 sm:py-32">
        <Reveal>
          <p className="eyebrow flex items-center gap-2">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-accent" />
            </span>
            {site.status.label}: {site.status.value}
          </p>
        </Reveal>

        <Reveal delay={80}>
          <h1 className="mt-6 max-w-3xl text-4xl font-semibold tracking-tight text-ink sm:text-6xl">
            {site.name}
          </h1>
        </Reveal>

        <Reveal delay={140}>
          <p className="mt-3 font-mono text-sm text-accent sm:text-base">{site.role}</p>
        </Reveal>

        <Reveal delay={200}>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-ink-soft">
            {site.tagline}
          </p>
        </Reveal>

        <Reveal delay={280}>
          <div className="mt-9 flex flex-wrap items-center gap-3">
            <Link
              href="#work"
              className="inline-flex items-center gap-2 rounded-md bg-accent px-5 py-2.5 text-sm font-medium text-bg transition-transform hover:-translate-y-0.5"
            >
              View work
              <span aria-hidden="true">↓</span>
            </Link>
            <Link
              href={site.resumePath}
              className="inline-flex items-center gap-2 rounded-md border border-line px-5 py-2.5 text-sm font-medium text-ink transition-colors hover:border-accent hover:text-accent"
            >
              Resume
              <span aria-hidden="true">↗</span>
            </Link>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
