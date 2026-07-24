import { Reveal } from './Reveal';

export function Section({
  id,
  eyebrow,
  title,
  intro,
  children,
}: {
  id: string;
  eyebrow: string;
  title: string;
  intro?: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-20 border-t border-line/60 py-20 sm:py-24">
      <div className="container-content">
        <Reveal>
          <p className="eyebrow">{eyebrow}</p>
          <h2 className="mt-3 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
            {title}
          </h2>
          {intro ? (
            <p className="mt-3 max-w-2xl text-ink-soft">{intro}</p>
          ) : null}
        </Reveal>
        <div className="mt-10">{children}</div>
      </div>
    </section>
  );
}
