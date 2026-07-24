import type { Metadata } from 'next';
import Link from 'next/link';
import { site } from '@/lib/site';

export const metadata: Metadata = {
  title: 'Uses',
  description: 'Tools, stack and homelab.',
};

const groups: { title: string; items: { name: string; note: string }[] }[] = [
  {
    title: 'Editor & terminal',
    items: [
      { name: 'Neovim / VS Code', note: 'Depends on the day — Neovim for infra, VS Code for app code' },
      { name: 'tmux + zsh', note: 'Split panes for logs, k9s and a shell, always' },
      { name: 'JetBrains Mono', note: 'The monospace you are reading right now' },
    ],
  },
  {
    title: 'Cloud & infra',
    items: [
      { name: 'Kubernetes + Helm', note: 'Managed clusters, GitOps rollouts with Argo' },
      { name: 'Terraform', note: 'Everything that is not on the cluster' },
      { name: 'Prometheus + Grafana', note: 'Metrics, alerting and the dashboards on the wall' },
    ],
  },
  {
    title: 'This site',
    items: [
      { name: 'Next.js (App Router)', note: 'Static-first, MDX case studies' },
      { name: 'Tailwind CSS', note: 'Design tokens in one config file' },
      { name: 'Vercel', note: 'Deployed on Vercel; the whole repo is public as proof-of-work' },
    ],
  },
];

export default function UsesPage() {
  return (
    <div className="container-content max-w-3xl py-16 sm:py-24">
      <p className="eyebrow">/uses</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
        What I use
      </h1>
      <p className="mt-3 text-ink-soft">
        The tools, stack and setup behind the work. This site is deployed on Vercel with its
        infrastructure-as-code and CI pipeline in the same{' '}
        <a href={site.socials.github} className="text-accent hover:underline">
          public repo
        </a>
        .
      </p>

      <div className="mt-10 space-y-10">
        {groups.map((group) => (
          <section key={group.title}>
            <h2 className="font-mono text-xs uppercase tracking-[0.2em] text-accent">
              {group.title}
            </h2>
            <ul className="mt-4 divide-y divide-line/60">
              {group.items.map((item) => (
                <li key={item.name} className="flex flex-col gap-0.5 py-3 sm:flex-row sm:justify-between sm:gap-6">
                  <span className="font-medium text-ink">{item.name}</span>
                  <span className="text-sm text-ink-soft sm:text-right">{item.note}</span>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      <p className="mt-12">
        <Link href="/" className="font-mono text-xs text-ink-soft hover:text-accent">
          ← Back home
        </Link>
      </p>
    </div>
  );
}
