import { MDXRemote } from 'next-mdx-remote/rsc';
import { Mermaid } from './Mermaid';

// Components made available inside every MDX document.
const components = {
  Mermaid,
};

export function Mdx({ source }: { source: string }) {
  return (
    <div className="prose-portfolio">
      <MDXRemote source={source} components={components} />
    </div>
  );
}
