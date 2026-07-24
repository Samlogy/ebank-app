import Link from 'next/link';
import type { ProjectMeta } from '@/lib/content';

export function ProjectCard({ meta }: { meta: ProjectMeta }) {
  return (
    <Link
      href={`/projects/${meta.slug}`}
      className="card group flex flex-col p-6 hover:border-accent/60"
    >
      <div className="flex items-center justify-between gap-4">
        <h3 className="text-lg font-semibold text-ink transition-colors group-hover:text-accent">
          {meta.title}
        </h3>
        <span className="font-mono text-xs text-ink-faint">{meta.year}</span>
      </div>

      <p className="mt-2 flex-1 text-sm leading-relaxed text-ink-soft">{meta.summary}</p>

      {meta.role ? (
        <p className="mt-3 font-mono text-xs text-ink-faint">Role: {meta.role}</p>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-2">
        {meta.tags.map((t) => (
          <span
            key={t}
            className="rounded border border-line px-2 py-0.5 font-mono text-[11px] text-ink-soft"
          >
            {t}
          </span>
        ))}
      </div>

      <span className="mt-5 inline-flex items-center gap-1 text-sm font-medium text-accent">
        Read case study
        <span aria-hidden="true" className="transition-transform group-hover:translate-x-0.5">
          →
        </span>
      </span>
    </Link>
  );
}
