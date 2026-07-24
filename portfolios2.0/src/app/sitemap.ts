import type { MetadataRoute } from 'next';
import { site } from '@/lib/site';
import { getProjects, getWriting } from '@/lib/content';

export default function sitemap(): MetadataRoute.Sitemap {
  const base = site.baseUrl.replace(/\/$/, '');
  const now = new Date();

  const staticRoutes = ['', '/resume', '/uses'].map((path) => ({
    url: `${base}${path}`,
    lastModified: now,
    changeFrequency: 'monthly' as const,
    priority: path === '' ? 1 : 0.6,
  }));

  const projectRoutes = getProjects().map((p) => ({
    url: `${base}/projects/${p.meta.slug}`,
    lastModified: now,
    changeFrequency: 'monthly' as const,
    priority: 0.8,
  }));

  const writingRoutes = getWriting().map((p) => ({
    url: `${base}/writing/${p.meta.slug}`,
    lastModified: now,
    changeFrequency: 'monthly' as const,
    priority: 0.5,
  }));

  return [...staticRoutes, ...projectRoutes, ...writingRoutes];
}
