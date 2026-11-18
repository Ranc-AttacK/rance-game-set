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
        // 近白暖底：大面积留白，衬托半透明装饰背景
        background: '#f7f5f2',
        // 内容卡片：纯白
        card: '#ffffff',
        // 正文近黑
        foreground: '#171717',
        // 次要信息中性灰
        muted: {
          DEFAULT: '#6b655f',
          foreground: '#57534e'
        },
        // 中性浅灰描边
        border: '#e7e2dd',
        // 唯一强调色：纯黑（主按钮 / 当前项）
        accent: {
          DEFAULT: '#171717',
          foreground: '#faf9f7'
        },
        // 极少量粉色点缀：仅用于标题高亮与焦点环
        sakura: {
          200: '#f9d8e3',
          500: '#d96a90',
          600: '#c14e77'
        }
      },
      fontFamily: {
        sans: [
          'Inter',
          '"Segoe UI"',
          'system-ui',
          '-apple-system',
          '"PingFang SC"',
          '"Microsoft YaHei"',
          '"Noto Sans SC"',
          'sans-serif'
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace']
      },
      boxShadow: {
        card: '0 1px 2px rgba(23, 23, 23, 0.05), 0 1px 3px rgba(23, 23, 23, 0.04)',
        hover: '0 12px 30px -18px rgba(23, 23, 23, 0.28)'
      }
    }
  },
  plugins: []
} satisfies Config
