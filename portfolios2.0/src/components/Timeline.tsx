import { experience } from '@/lib/site';
import { Section } from './Section';
import { Reveal } from './Reveal';

export function Timeline() {
  return (
    <Section
      id="experience"
      eyebrow="Trajectory"
      title="Experience"
      intro="Condensed — the case studies do the convincing. Full detail lives in the resume."
    >
      <ol className="relative border-l border-line pl-6">
        {experience.map((role, i) => (
          <Reveal as="li" key={role.company} delay={i * 60} className="mb-10 last:mb-0">
            <span
              aria-hidden="true"
              className="absolute -left-[5px] mt-1.5 h-2.5 w-2.5 rounded-full border border-accent bg-bg"
            />
            <div className="flex flex-wrap items-baseline justify-between gap-x-4">
              <h3 className="text-base font-semibold text-ink">
                {role.title}
                <span className="text-ink-faint"> · {role.company}</span>
              </h3>
              <span className="font-mono text-xs text-ink-faint">{role.period}</span>
            </div>
            <ul className="mt-3 space-y-1.5">
              {role.highlights.map((h) => (
                <li key={h} className="flex gap-2 text-sm leading-relaxed text-ink-soft">
                  <span aria-hidden="true" className="mt-2 h-1 w-1 flex-none rounded-full bg-accent" />
                  <span>{h}</span>
                </li>
              ))}
            </ul>
          </Reveal>
        ))}
      </ol>
    </Section>
  );
}
