import type { Config } from 'tailwindcss'

export default {
  content: [
    './app.vue',
    './components/**/*.{vue,js,ts}',
    './layouts/**/*.vue',
    './pages/**/*.vue'
  ],
  theme: {
    extend: {
      colors: {
        // 深墨蓝 / 近黑：公会大厅的夜色底
        ink: {
          950: '#070910',
          900: '#0b0e18',
          850: '#10131f',
          800: '#151a2b',
          700: '#1d2337',
          600: '#283049',
          500: '#39415c'
        },
        // 酒红 / 猩红：旗帜与蜡封主色
        wine: {
          50: '#fdf2f4',
          100: '#fbe3e7',
          200: '#f6c7cf',
          300: '#ee9aa9',
          400: '#e06a80',
          500: '#c93a56',
          600: '#a82541',
          700: '#871b34',
          800: '#651426',
          900: '#4a0f1d'
        },
        // 古金：纹章描边与高光
        gold: {
          50: '#fbf6e6',
          100: '#f5ebc4',
          200: '#ecd998',
          300: '#e2c46a',
          400: '#d4ac45',
          500: '#c1962f',
          600: '#a17a25',
          700: '#7d5c1f',
          800: '#5c431b',
          900: '#3f2e15'
        },
        // 羊皮纸：告示与记录纸
        parchment: {
          50: '#faf3e3',
          100: '#f3e7cc',
          200: '#e9d6ac',
          300: '#dcc28a',
          400: '#c9a967'
        }
      },
      fontFamily: {
        display: ['Georgia', '"Noto Serif SC"', '"Songti SC"', 'SimSun', 'serif'],
        body: ['"Segoe UI"', 'system-ui', '-apple-system', '"Noto Sans SC"', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace']
      },
      borderRadius: {
        guild: '0.9rem'
      },
      boxShadow: {
        panel: '0 18px 40px -22px rgba(0,0,0,0.95), inset 0 1px 0 rgba(255,255,255,0.05)',
        lift: '0 24px 52px -20px rgba(0,0,0,0.98), 0 0 0 1px rgba(212,172,69,0.18)',
        emboss:
          'inset 0 1px 0 rgba(255,255,255,0.16), inset 0 -2px 4px rgba(0,0,0,0.4), 0 4px 10px -6px rgba(0,0,0,0.8)',
        glowGold: '0 0 0 1px rgba(212,172,69,0.42), 0 10px 28px -10px rgba(212,172,69,0.35)',
        glowWine: '0 0 0 1px rgba(201,58,86,0.45), 0 12px 30px -12px rgba(201,58,86,0.55)'
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        }
      },
      animation: {
        'fade-up': 'fade-up 0.5s cubic-bezier(0.2,0.7,0.3,1) both'
      }
    }
  },
  plugins: []
} satisfies Config
