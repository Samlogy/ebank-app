import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getProject, getProjects } from '@/lib/content';
import { Mdx } from '@/components/Mdx';

export function generateStaticParams() {
  return getProjects().map((p) => ({ slug: p.meta.slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const project = getProject(slug);
  if (!project) return {};
  return {
    title: project.meta.title,
    description: project.meta.summary,
    openGraph: {
      title: project.meta.title,
      description: project.meta.summary,
      type: 'article',
    },
  };
}

export default async function ProjectPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const project = getProject(slug);
  if (!project) notFound();

  const { meta } = project;

  return (
    <article className="container-content py-16 sm:py-24">
      <Link href="/#work" className="font-mono text-xs text-ink-soft transition-colors hover:text-accent">
        ← Back to work
      </Link>

      <header className="mt-6 border-b border-line/60 pb-8">
        <div className="flex flex-wrap items-center gap-3 font-mono text-xs text-ink-faint">
          <span>{meta.year}</span>
          {meta.role ? <span>· {meta.role}</span> : null}
        </div>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
          {meta.title}
        </h1>
        <p className="mt-3 max-w-2xl text-lg text-ink-soft">{meta.summary}</p>

        <div className="mt-5 flex flex-wrap gap-2">
          {meta.tags.map((t) => (
            <span key={t} className="rounded border border-line px-2 py-0.5 font-mono text-[11px] text-ink-soft">
              {t}
            </span>
          ))}
        </div>

        {(meta.repo || meta.demo) && (
          <div className="mt-6 flex flex-wrap gap-3">
            {meta.repo ? (
              <a href={meta.repo} target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-accent hover:underline">
                Repository ↗
              </a>
            ) : null}
            {meta.demo ? (
              <a href={meta.demo} target="_blank" rel="noopener noreferrer" className="text-sm font-medium text-accent hover:underline">
                Live demo ↗
              </a>
            ) : null}
          </div>
        )}
      </header>

      <div className="mt-10">
        <Mdx source={project.content} />
      </div>
    </article>
  );
}
