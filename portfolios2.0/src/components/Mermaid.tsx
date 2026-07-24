'use client';

import { useEffect, useId, useRef, useState } from 'react';

/**
 * Renders a Mermaid diagram from text. Loads mermaid lazily on the client so it
 * never touches the server bundle or the initial page payload.
 *
 * Usage in MDX:  <Mermaid chart={`graph LR; A-->B`} />
 */
export function Mermaid({ chart, caption }: { chart: string; caption?: string }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const id = useId().replace(/[^a-zA-Z0-9]/g, '');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const mermaid = (await import('mermaid')).default;
        const dark = document.documentElement.classList.contains('dark');
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: dark ? 'dark' : 'neutral',
          fontFamily: 'var(--font-mono), monospace',
          themeVariables: dark
            ? { background: '#141417', primaryColor: '#141417', lineColor: '#5eead4' }
            : {},
        });
        const { svg } = await mermaid.render(`m-${id}`, chart);
        if (!cancelled && ref.current) ref.current.innerHTML = svg;
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Diagram failed to render');
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [chart, id]);

  return (
    <figure className="my-8 overflow-x-auto rounded-xl border border-line bg-bg-soft surface-soft p-6">
      {error ? (
        <pre className="whitespace-pre-wrap text-xs text-red-400">{error}</pre>
      ) : (
        <div ref={ref} className="mermaid-diagram flex justify-center [&_svg]:h-auto [&_svg]:max-w-full" />
      )}
      {caption ? (
        <figcaption className="mt-3 text-center font-mono text-xs text-ink-faint">
          {caption}
        </figcaption>
      ) : null}
    </figure>
  );
}
