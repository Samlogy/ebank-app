import { impact } from '@/lib/site';
import { Reveal } from './Reveal';

export function ImpactStrip() {
  return (
    <section
      aria-label="Impact metrics"
      className="border-t border-line/60 bg-bg-soft/40 surface-soft"
    >
      <div className="container-content py-14">
        <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-line bg-line lg:grid-cols-4">
          {impact.map((stat, i) => (
            <Reveal
              key={stat.label}
              delay={i * 70}
              className="bg-bg-card surface-soft p-6"
            >
              <dt className="font-mono text-3xl font-semibold tracking-tight text-accent sm:text-4xl">
                {stat.value}
              </dt>
              <dd className="mt-2 text-sm font-medium text-ink">{stat.label}</dd>
              {stat.note ? (
                <dd className="mt-1 text-xs leading-relaxed text-ink-faint">{stat.note}</dd>
              ) : null}
            </Reveal>
          ))}
        </dl>
      </div>
    </section>
  );
}
