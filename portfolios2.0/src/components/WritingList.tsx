import Link from 'next/link';
import { getWriting } from '@/lib/content';
import { Section } from './Section';
import { Reveal } from './Reveal';

export function WritingList() {
  const posts = getWriting();
  if (posts.length === 0) return null;

  return (
    <Section
      id="writing"
      eyebrow="Writing & talks"
      title="Notes on building and leading"
      intro="Postmortems, RFC-style decisions and process pieces — the reasoning behind the systems."
    >
      <ul className="divide-y divide-line/70">
        {posts.map((post, i) => (
          <Reveal as="li" key={post.meta.slug} delay={i * 50}>
            <Link
              href={`/writing/${post.meta.slug}`}
              className="group flex flex-col gap-1 py-5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-6"
            >
              <div className="min-w-0">
                <h3 className="text-base font-medium text-ink transition-colors group-hover:text-accent">
                  {post.meta.title}
                </h3>
                <p className="mt-1 text-sm text-ink-soft">{post.meta.summary}</p>
              </div>
              <time className="flex-none font-mono text-xs text-ink-faint">
                {post.meta.date}
              </time>
            </Link>
          </Reveal>
        ))}
      </ul>
    </Section>
  );
}
