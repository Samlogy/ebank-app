import type { Metadata } from 'next';
import Link from 'next/link';
import { site, experience, skills, impact } from '@/lib/site';

export const metadata: Metadata = {
  title: 'Resume',
  description: `Resume of ${site.name}, ${site.role}.`,
};

export default function ResumePage() {
  return (
    <div className="container-content max-w-3xl py-16 sm:py-24">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight text-ink">{site.name}</h1>
          <p className="mt-1 font-mono text-sm text-accent">{site.role}</p>
        </div>
        <a
          href={site.resumePath}
          className="inline-flex items-center gap-2 rounded-md border border-line px-4 py-2 text-sm font-medium text-ink transition-colors hover:border-accent hover:text-accent print:hidden"
        >
          Download PDF ↓
        </a>
      </div>

      <p className="mt-4 text-sm text-ink-soft">
        {site.location} ·{' '}
        <a href={`mailto:${site.email}`} className="text-accent hover:underline">
          {site.email}
        </a>{' '}
        ·{' '}
        <a href={site.socials.github} className="text-accent hover:underline">
          github.com/{site.handle}
        </a>
      </p>

      <p className="mt-6 leading-relaxed text-ink-soft">{site.tagline}</p>

      <ResumeSection title="Highlights">
        <ul className="grid gap-2 sm:grid-cols-2">
          {impact.map((s) => (
            <li key={s.label} className="text-sm text-ink-soft">
              <span className="font-mono text-accent">{s.value}</span> — {s.note ?? s.label}
            </li>
          ))}
        </ul>
      </ResumeSection>

      <ResumeSection title="Experience">
        <div className="space-y-6">
          {experience.map((role) => (
            <div key={role.company}>
              <div className="flex flex-wrap items-baseline justify-between gap-x-4">
                <h3 className="font-medium text-ink">
                  {role.title} · {role.company}
                </h3>
                <span className="font-mono text-xs text-ink-faint">{role.period}</span>
              </div>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-ink-soft">
                {role.highlights.map((h) => (
                  <li key={h}>{h}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </ResumeSection>

      <ResumeSection title="Skills">
        <dl className="grid gap-4 sm:grid-cols-2">
          {skills.map((g) => (
            <div key={g.group}>
              <dt className="font-mono text-xs uppercase tracking-wide text-accent">{g.group}</dt>
              <dd className="mt-1 text-sm text-ink-soft">{g.items.join(' · ')}</dd>
            </div>
          ))}
        </dl>
      </ResumeSection>

      <p className="mt-12 print:hidden">
        <Link href="/" className="font-mono text-xs text-ink-soft hover:text-accent">
          ← Back home
        </Link>
      </p>
    </div>
  );
}

function ResumeSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-10 border-t border-line/60 pt-6">
      <h2 className="mb-4 font-mono text-xs uppercase tracking-[0.2em] text-ink-faint">{title}</h2>
      {children}
    </section>
  );
}
