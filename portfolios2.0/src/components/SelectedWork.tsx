import { getProjects } from '@/lib/content';
import { Section } from './Section';
import { ProjectCard } from './ProjectCard';
import { Reveal } from './Reveal';

export function SelectedWork() {
  const projects = getProjects();

  return (
    <Section
      id="work"
      eyebrow="Selected work"
      title="Case studies, not screenshots"
      intro="A few systems I designed or led end-to-end. Each page walks the context, the key decision and the tradeoffs — the parts that actually matter for a lead role."
    >
      <div className="grid gap-5 sm:grid-cols-2">
        {projects.map((p, i) => (
          <Reveal key={p.meta.slug} delay={i * 60}>
            <ProjectCard meta={p.meta} />
          </Reveal>
        ))}
      </div>
    </Section>
  );
}
