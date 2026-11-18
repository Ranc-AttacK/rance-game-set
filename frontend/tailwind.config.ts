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
        brand: {
          50: '#fff1f7',
          100: '#ffe4ef',
          200: '#ffc9df',
          300: '#ff9ec5',
          400: '#ff6aa6',
          500: '#ff3d87',
          600: '#e81d68',
          700: '#c20f51',
          800: '#a01045',
          900: '#84123d'
        }
      }
    }
  },
  plugins: []
} satisfies Config
