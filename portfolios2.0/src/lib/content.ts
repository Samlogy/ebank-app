import fs from 'node:fs';
import path from 'node:path';
import matter from 'gray-matter';

const CONTENT_DIR = path.join(process.cwd(), 'content');

export type ProjectMeta = {
  slug: string;
  title: string;
  summary: string;
  tags: string[];
  role: string;
  year: string;
  repo?: string;
  demo?: string;
  order: number;
};

export type WritingMeta = {
  slug: string;
  title: string;
  summary: string;
  date: string;
  tags: string[];
};

export type Doc<T> = { meta: T; content: string };

function readCollection(dir: string): { slug: string; raw: string }[] {
  const full = path.join(CONTENT_DIR, dir);
  if (!fs.existsSync(full)) return [];
  return fs
    .readdirSync(full)
    .filter((f) => f.endsWith('.mdx'))
    .map((f) => ({
      slug: f.replace(/\.mdx$/, ''),
      raw: fs.readFileSync(path.join(full, f), 'utf8'),
    }));
}

export function getProjects(): Doc<ProjectMeta>[] {
  return readCollection('projects')
    .map(({ slug, raw }) => {
      const { data, content } = matter(raw);
      return {
        meta: {
          slug,
          title: String(data.title ?? slug),
          summary: String(data.summary ?? ''),
          tags: (data.tags as string[]) ?? [],
          role: String(data.role ?? ''),
          year: String(data.year ?? ''),
          repo: data.repo ? String(data.repo) : undefined,
          demo: data.demo ? String(data.demo) : undefined,
          order: Number(data.order ?? 99),
        },
        content,
      };
    })
    .sort((a, b) => a.meta.order - b.meta.order);
}

export function getProject(slug: string): Doc<ProjectMeta> | null {
  return getProjects().find((p) => p.meta.slug === slug) ?? null;
}

export function getWriting(): Doc<WritingMeta>[] {
  return readCollection('writing')
    .map(({ slug, raw }) => {
      const { data, content } = matter(raw);
      return {
        meta: {
          slug,
          title: String(data.title ?? slug),
          summary: String(data.summary ?? ''),
          date: String(data.date ?? ''),
          tags: (data.tags as string[]) ?? [],
        },
        content,
      };
    })
    .sort((a, b) => (a.meta.date < b.meta.date ? 1 : -1));
}

export function getWritingPost(slug: string): Doc<WritingMeta> | null {
  return getWriting().find((p) => p.meta.slug === slug) ?? null;
}
