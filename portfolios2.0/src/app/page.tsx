import { Hero } from '@/components/Hero';
import { ImpactStrip } from '@/components/ImpactStrip';
import { SelectedWork } from '@/components/SelectedWork';
import { Timeline } from '@/components/Timeline';
import { Skills } from '@/components/Skills';
import { WritingList } from '@/components/WritingList';
import { Contact } from '@/components/Contact';

export default function HomePage() {
  return (
    <>
      <Hero />
      <ImpactStrip />
      <SelectedWork />
      <Timeline />
      <Skills />
      <WritingList />
      <Contact />
    </>
  );
}
