# portfolios2.0

Personal portfolio for a **Senior DevOps Engineer / Tech Lead**. Built to convince a
technical audience with evidence of impact and systems thinking — not visual flair.

Deployed on **Vercel**, infra-as-code and CI in this repo.

## Stack

- **Next.js 15** (App Router, React 19) — static-first, fast
- **Tailwind CSS** — dark-mode-first, one accent color, design tokens in `tailwind.config.ts`
- **MDX** (`next-mdx-remote`) — case studies and writing live in `content/`
- **Mermaid** — architecture diagrams render from text, client-side only
- **Inter** (UI) + **JetBrains Mono** (code/metrics) via `next/font`

## Structure

```
content/
  projects/*.mdx     Case studies (Context → Role → Decision → Build → Tradeoffs → Outcome)
  writing/*.mdx      Notes, postmortems, RFC-style posts
public/
  resume.pdf         Downloadable, ATS-friendly resume
  og.svg             OpenGraph / Twitter card image
src/
  app/               Routes: /, /projects/[slug], /writing/[slug], /resume, /uses
  components/        Hero, ImpactStrip, SelectedWork, Timeline, Skills, ...
  lib/
    site.ts          ← EDIT ME: identity, impact numbers, experience, skills, links
    content.ts       MDX loader
```

## Make it yours

Everything personal is centralized. Start here:

1. **`src/lib/site.ts`** — name, role, tagline, impact stats, experience, skills, socials.
   Replace every value marked `TODO` and swap the placeholder numbers for real, defensible ones.
2. **`content/projects/*.mdx`** — your 3–4 case studies. Keep the fixed section structure.
3. **`content/writing/*.mdx`** — optional posts; delete the folder's files to hide the section.
4. **`public/resume.pdf`** — replace with your real resume export.
5. **`public/og.svg`** — update the preview text.
6. Set your production domain in `site.baseUrl` so metadata, sitemap and robots are correct.

## Develop

```bash
npm install
npm run dev        # http://localhost:3000
npm run typecheck
npm run lint
npm run build
```

## Deploy

Push to a GitHub repo and import it into Vercel — zero config. The included
`.github/workflows/ci.yml` type-checks, lints and builds on every push/PR.

## Design direction

Dark-mode-first with a light toggle (respects `prefers-color-scheme`), near-black
background, off-white text, a single teal accent used sparingly, subtle fade/slide-in
on scroll, and generous whitespace at ~1160px max width. Motion honors
`prefers-reduced-motion`. Semantic HTML, OpenGraph tags, sitemap and robots included —
built for Lighthouse 95+.
