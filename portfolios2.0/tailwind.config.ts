import type { Config } from 'tailwindcss';

const config: Config = {
  darkMode: 'class',
  content: [
    './src/**/*.{ts,tsx,mdx}',
    './content/**/*.{md,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        // Near-black, not pure black. Off-white text. One accent.
        bg: {
          DEFAULT: '#0a0a0b',
          soft: '#111114',
          card: '#141417',
        },
        line: '#26262b',
        ink: {
          DEFAULT: '#ededf0',
          soft: '#a1a1aa',
          faint: '#6b6b74',
        },
        accent: {
          DEFAULT: '#5eead4', // teal — used sparingly
          soft: '#2dd4bf',
          deep: '#0f766e',
        },
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['var(--font-mono)', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      maxWidth: {
        content: '1160px',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'gradient-pan': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.6s cubic-bezier(0.16, 1, 0.3, 1) both',
        'gradient-pan': 'gradient-pan 12s ease infinite',
      },
    },
  },
  plugins: [require('@tailwindcss/typography')],
};

export default config;
