import { skills } from '@/lib/site';
import { Section } from './Section';
import { Reveal } from './Reveal';

export function Skills() {
  return (
    <Section
      id="skills"
      eyebrow="Toolkit"
      title="Skills & stack"
      intro="Grouped by role, proven by the projects above — no progress bars, no star ratings."
    >
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {skills.map((group, i) => (
          <Reveal key={group.group} delay={i * 60} className="card p-5">
            <h3 className="font-mono text-xs uppercase tracking-[0.15em] text-accent">
              {group.group}
            </h3>
            <ul className="mt-4 space-y-2">
              {group.items.map((item) => (
                <li key={item} className="text-sm text-ink-soft">
                  {item}
                </li>
              ))}
            </ul>
          </Reveal>
        ))}
      </div>
    </Section>
  );
}
