/**
 * Single source of truth for identity, impact numbers, experience, skills,
 * and contact links. Edit the values here — every section reads from this file.
 *
 * Anything marked TODO is a placeholder: replace with your real data before launch.
 */

export const site = {
  name: 'Sam Senani',
  handle: 'samlogy',
  role: 'Senior DevOps Engineer & Tech Lead',
  // The <5s promise: who you are, what you build/lead, one proof point.
  tagline:
    'I design and lead the platform and infrastructure behind distributed banking systems — from zero-downtime deploys and observability to on-call culture.',
  location: 'Remote · EU',
  email: 'senanisammy@gmail.com',
  calendly: 'https://calendly.com/', // TODO: your booking link
  resumePath: '/resume.pdf',
  baseUrl: 'https://portfolios2.example.com', // TODO: your production domain

  socials: {
    github: 'https://github.com/samlogy',
    linkedin: 'https://www.linkedin.com/in/', // TODO
  },

  // Live status widget in the hero. Keep it honest.
  status: {
    label: 'Currently building',
    value: 'a multi-cluster GitOps rollout for eBank',
  },
} as const;

/** §3 Impact strip — 3-4 hard numbers. Replace with your real, defensible data. */
export const impact: { value: string; label: string; note?: string }[] = [
  {
    value: '45m → 4m',
    label: 'Deploy time',
    note: 'Reworked CI/CD and image caching across the eBank service fleet',
  },
  {
    value: '99.98%',
    label: 'Platform uptime',
    note: 'SLO for a payments platform serving 2M+ requests/day',
  },
  {
    value: '−40%',
    label: 'Infra cost',
    note: 'Right-sizing, spot capacity and autoscaling on Kubernetes',
  },
  {
    value: '6',
    label: 'Engineers led',
    note: 'Grew and mentored a platform team; owned on-call design',
  },
];

/** §5 Experience timeline — condensed. Impact-phrased bullets, not tasks. */
export const experience: {
  company: string;
  title: string;
  period: string;
  highlights: string[];
}[] = [
  {
    company: 'eBank (personal platform / lead project)',
    title: 'Platform & DevOps Lead',
    period: '2023 — Present',
    highlights: [
      'Architected a 12-service Spring Boot microservices platform with Docker Compose and Kubernetes (Minikube → managed), Spring Cloud Gateway and Spring Admin.',
      'Built full observability: centralized logging, metrics and tracing, with health-based routing and zero-downtime rolling deploys.',
      'Added a Spring AI chatbot service with tool-calling and RAG over pgvector, shipped behind the same CI/CD and gateway.',
    ],
  },
  {
    company: 'Company B', // TODO
    title: 'Senior DevOps Engineer',
    period: '2020 — 2023',
    highlights: [
      'Sole infra owner for 3 production services; introduced infrastructure-as-code and cut manual deploy steps to zero.',
      'Designed the on-call rotation and incident-response runbooks adopted across two teams.',
    ],
  },
  {
    company: 'Company C', // TODO
    title: 'Software / Cloud Engineer',
    period: '2017 — 2020',
    highlights: [
      'Migrated a legacy monolith toward microservices, coordinating rollout across two product teams.',
      'Established the team’s first automated pipeline (lint → test → build → deploy).',
    ],
  },
];

/** §6 Skills — grouped, proven by usage in the projects above. No star ratings. */
export const skills: { group: string; items: string[] }[] = [
  {
    group: 'Languages',
    items: ['Java', 'TypeScript', 'Go', 'Python', 'Bash', 'SQL'],
  },
  {
    group: 'Infra / Cloud',
    items: ['Kubernetes', 'Docker', 'Terraform', 'AWS', 'Helm', 'Nginx / Gateway'],
  },
  {
    group: 'CI/CD & Tooling',
    items: ['GitHub Actions', 'ArgoCD / GitOps', 'Prometheus', 'Grafana', 'OpenTelemetry', 'pgvector'],
  },
  {
    group: 'Leadership / Process',
    items: [
      'Tech lead & mentoring',
      'On-call & incident response',
      'Architecture review',
      'RFC / design docs',
    ],
  },
];
