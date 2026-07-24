import Link from 'next/link';
import { site } from '@/lib/site';
import { ThemeToggle } from './ThemeToggle';

const links = [
  { href: '/#work', label: 'Work' },
  { href: '/#experience', label: 'Experience' },
  { href: '/#skills', label: 'Skills' },
  { href: '/#writing', label: 'Writing' },
  { href: '/uses', label: 'Uses' },
];

export function Nav() {
  return (
    <header className="site-nav sticky top-0 z-40 border-b border-line/60 bg-bg/70 backdrop-blur-md supports-[backdrop-filter]:bg-bg/60">
      <nav className="container-content flex h-16 items-center justify-between">
        <Link href="/" className="font-mono text-sm font-medium tracking-tight text-ink">
          <span className="text-accent">~/</span>
          {site.handle}
        </Link>

        <div className="flex items-center gap-1">
          <ul className="mr-2 hidden items-center gap-1 md:flex">
            {links.map((l) => (
              <li key={l.href}>
                <Link
                  href={l.href}
                  className="rounded-md px-3 py-2 text-sm text-ink-soft transition-colors hover:text-ink"
                >
                  {l.label}
                </Link>
              </li>
            ))}
          </ul>
          <Link
            href={site.resumePath}
            className="hidden rounded-md border border-line px-3 py-2 text-sm text-ink-soft transition-colors hover:border-accent hover:text-accent sm:inline-flex"
          >
            Resume
          </Link>
          <ThemeToggle />
        </div>
      </nav>
    </header>
  );
}
