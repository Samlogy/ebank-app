import Link from 'next/link';
import { site } from '@/lib/site';
import { Reveal } from './Reveal';

export function Contact() {
  return (
    <section id="contact" className="scroll-mt-20 border-t border-line/60 py-20 sm:py-24">
      <div className="container-content">
        <Reveal className="card overflow-hidden p-8 sm:p-12">
          <p className="eyebrow">Contact</p>
          <h2 className="mt-3 max-w-xl text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
            Let&apos;s talk.
          </h2>
          <p className="mt-3 max-w-xl text-ink-soft">
            Hiring for a platform, infra or tech-lead role — or want a second opinion on an
            architecture? The fastest way to reach me is email or a quick call.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <a
              href={`mailto:${site.email}`}
              className="inline-flex items-center gap-2 rounded-md bg-accent px-5 py-2.5 text-sm font-medium text-bg transition-transform hover:-translate-y-0.5"
            >
              Email me
            </a>
            <a
              href={site.calendly}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-md border border-line px-5 py-2.5 text-sm font-medium text-ink transition-colors hover:border-accent hover:text-accent"
            >
              Book a call ↗
            </a>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

export function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer className="border-t border-line/60">
      <div className="container-content flex flex-col gap-4 py-10 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-mono text-xs text-ink-faint">
          © {year} {site.name}. Built with Next.js + Tailwind, deployed on Vercel.
          {' '}
          <span className="text-ink-faint/70">Infra-as-code in the repo.</span>
        </p>
        <nav aria-label="Social" className="flex items-center gap-4 text-sm">
          <a href={site.socials.github} target="_blank" rel="noopener noreferrer" className="text-ink-soft transition-colors hover:text-accent">
            GitHub
          </a>
          <a href={site.socials.linkedin} target="_blank" rel="noopener noreferrer" className="text-ink-soft transition-colors hover:text-accent">
            LinkedIn
          </a>
          <a href={`mailto:${site.email}`} className="text-ink-soft transition-colors hover:text-accent">
            Email
          </a>
          <Link href={site.resumePath} className="text-ink-soft transition-colors hover:text-accent">
            Resume
          </Link>
        </nav>
      </div>
    </footer>
  );
}
