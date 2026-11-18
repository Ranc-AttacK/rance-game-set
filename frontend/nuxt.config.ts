export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: false },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss'],
  css: ['~/assets/css/main.css'],
  // SSR 直连后端的内部地址；生产用 NUXT_API_INTERNAL_BASE=http://backend:8080 覆盖。
  runtimeConfig: {
    apiInternalBase: process.env.NUXT_API_INTERNAL_BASE || 'http://localhost:8080'
  },
  // 关闭 Nitro 自带的 OpenAPI 生成（API 文档由后端 springdoc 提供），
  // 同时规避 Nuxt 3.14 + Nitro 2.13 的 std-env/impound 构建问题。
  nitro: {
    experimental: { openAPI: false }
  },
  app: {
    head: {
      title: 'Galgame 社区',
      htmlAttrs: { lang: 'zh-CN' }
    }
  },
  // 若 Nuxt 3.14 的 routeRules proxy 不生效，改用
  // nitro: { devProxy: { '/api': { target: 'http://localhost:8080/api', changeOrigin: true } } }
  // 生产环境由 Nginx 反代 /api。
  routeRules: {
    '/api/**': { proxy: 'http://localhost:8080/api/**' },
    '/login': { ssr: false },
    '/register': { ssr: false },
    '/verify-email': { ssr: false },
    '/forgot-password': { ssr: false },
    '/reset-password': { ssr: false },
    '/me': { ssr: false },
    '/topics/new': { ssr: false }
  }
})
