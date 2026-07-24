import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getWriting, getWritingPost } from '@/lib/content';
import { Mdx } from '@/components/Mdx';

export function generateStaticParams() {
  return getWriting().map((p) => ({ slug: p.meta.slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const post = getWritingPost(slug);
  if (!post) return {};
  return {
    title: post.meta.title,
    description: post.meta.summary,
    openGraph: { title: post.meta.title, description: post.meta.summary, type: 'article' },
  };
}

export default async function WritingPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const post = getWritingPost(slug);
  if (!post) notFound();

  return (
    <article className="container-content py-16 sm:py-24">
      <Link href="/#writing" className="font-mono text-xs text-ink-soft transition-colors hover:text-accent">
        ← Back to writing
      </Link>
      <header className="mt-6 border-b border-line/60 pb-8">
        <time className="font-mono text-xs text-ink-faint">{post.meta.date}</time>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
          {post.meta.title}
        </h1>
        <p className="mt-3 max-w-2xl text-lg text-ink-soft">{post.meta.summary}</p>
      </header>
      <div className="mt-10">
        <Mdx source={post.content} />
      </div>
    </article>
  );
}
