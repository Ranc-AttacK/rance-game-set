# P1-B Nuxt 3 认证前端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为已完成的 P1-A 后端提供可用的 Nuxt 3 认证前端：注册、邮箱验证、登录、找回/重置密码、资料查看/编辑/注销，并与现有 JWT + HttpOnly refresh Cookie 契约对齐。

**Architecture:** Nuxt 3 SPA（公共页后续 P2 再开 SSR）；API 通过 Nuxt 同源代理 `/api/**` 访问后端 8080，避免 CORS。access token 只存内存，refresh token 在 HttpOnly Cookie；Axios 401 时自动刷新并重试一次。Pinia 管理用户态，路由中间件做登录/游客门禁。

**Tech Stack:** Nuxt 3、Vue 3、TypeScript、Tailwind CSS 3、Pinia、Axios、Vitest + Vue Test Utils、Playwright。

---

## 前置条件

- Node.js 20+、npm 10+
- 后端与依赖可运行：`docker compose up -d` + `mvn -f backend/pom.xml spring-boot:run`（监听 8080）
- Mailpit UI/API：`http://localhost:8025`
- 前端 dev server：`http://localhost:3000`

## 文件结构（本计划结束后）

```
frontend/
├── package.json
├── nuxt.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── vitest.config.ts
├── playwright.config.ts
├── app.vue
├── assets/css/main.css
├── layouts/default.vue
├── components/{AuthShell,Alert}.vue
├── lib/api.ts
├── stores/auth.ts
├── middleware/{auth,guest}.ts
├── plugins/auth.client.ts
├── pages/{index,login,register,verify-email,forgot-password,reset-password,me}.vue
├── tests/unit/{api,auth-store}.spec.ts
└── e2e/auth.spec.ts
```

---

### Task 1: Nuxt 脚手架、Tailwind 与基础布局

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/nuxt.config.ts`
- Create: `frontend/tailwind.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/app.vue`
- Create: `frontend/assets/css/main.css`
- Create: `frontend/layouts/default.vue`
- Create: `frontend/pages/index.vue`

- [ ] **Step 1: 写 package.json**

`frontend/package.json`
```json
{
  "name": "gal-community-web",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "nuxt dev",
    "build": "nuxt build",
    "preview": "nuxt preview",
    "postinstall": "nuxt prepare",
    "typecheck": "nuxt typecheck",
    "test:unit": "vitest run",
    "test:e2e": "playwright test"
  },
  "dependencies": {
    "@pinia/nuxt": "0.9.0",
    "axios": "1.7.7",
    "nuxt": "3.21.11",
    "pinia": "2.3.0",
    "vue": "3.5.12"
  },
  "devDependencies": {
    "@nuxtjs/tailwindcss": "6.12.2",
    "@playwright/test": "1.48.2",
    "@vue/test-utils": "2.4.6",
    "happy-dom": "15.7.4",
    "typescript": "5.6.3",
    "vitest": "2.1.4",
    "vue-tsc": "2.1.10"
  }
}
```

- [ ] **Step 2: 写 Nuxt/Tailwind/TS 配置**

`frontend/nuxt.config.ts`
```ts
export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: false },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss'],
  css: ['~/assets/css/main.css'],
  // 关闭 Nitro 自带的 OpenAPI 生成（API 文档由后端 springdoc 提供），
  // 同时规避 Nuxt 3.14 + Nitro 2.13 的 std-env/impound 构建问题（Nuxt 已升级到 3.21.11）。
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
    '/me': { ssr: false }
  }
})
```

`frontend/tailwind.config.ts`
```ts
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
```

`frontend/tsconfig.json`
```json
{
  "extends": "./.nuxt/tsconfig.json"
}
```

- [ ] **Step 3: 写应用入口、样式与首页**

`frontend/app.vue`
```vue
<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>
```

`frontend/assets/css/main.css`
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  body {
    @apply min-h-screen bg-slate-950 text-slate-100 antialiased;
  }
}

@layer components {
  .input {
    @apply w-full rounded-lg border border-white/10 bg-slate-900/80 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-400/30;
  }
  .btn {
    @apply inline-flex items-center justify-center rounded-lg bg-brand-500 px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-400 disabled:cursor-not-allowed disabled:opacity-50;
  }
  .btn-ghost {
    @apply inline-flex items-center justify-center rounded-lg border border-white/10 px-4 py-2 text-sm text-slate-200 transition hover:bg-white/5;
  }
}
```

`frontend/layouts/default.vue`
```vue
<template>
  <div class="min-h-screen bg-slate-950">
    <header class="border-b border-white/10">
      <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
        <NuxtLink to="/" class="text-lg font-semibold tracking-wide text-brand-300">
          GalHub
        </NuxtLink>
        <nav class="flex items-center gap-3 text-sm">
          <NuxtLink to="/login" class="text-slate-300 hover:text-white">登录</NuxtLink>
          <NuxtLink to="/register" class="btn">注册</NuxtLink>
        </nav>
      </div>
    </header>
    <main class="mx-auto max-w-5xl px-4 py-10">
      <slot />
    </main>
  </div>
</template>
```

`frontend/pages/index.vue`
```vue
<template>
  <section class="flex min-h-[60vh] flex-col items-center justify-center text-center">
    <h1 class="text-4xl font-bold tracking-tight">
      <span class="text-brand-400">Galgame</span> 社区
    </h1>
    <p class="mt-4 max-w-xl text-slate-400">
      作品词条、评测与资源索引、分区讨论。先注册一个账号，验证邮箱后即可开始。
    </p>
    <div class="mt-8 flex gap-3">
      <NuxtLink to="/register" class="btn">创建账号</NuxtLink>
      <NuxtLink to="/login" class="btn-ghost">已有账号</NuxtLink>
    </div>
  </section>
</template>
```

- [ ] **Step 4: 安装依赖并构建**

Run:
```bash
cd frontend
npm install
npm run build
```
Expected: `npm install` 成功；`npm run build` 输出 `✔ You can preview this build using node .output/server/index.mjs`（或 Nuxt 成功提示），退出码 0。

- [ ] **Step 5: 提交**

```bash
git add frontend/package.json frontend/package-lock.json frontend/nuxt.config.ts frontend/tailwind.config.ts frontend/tsconfig.json frontend/app.vue frontend/assets frontend/layouts frontend/pages/index.vue
git commit -m "feat(web): scaffold Nuxt app with Tailwind"
```

---

### Task 2: API 客户端与错误提取

**Files:**
- Create: `frontend/lib/api.ts`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/tests/unit/api.spec.ts`

- [ ] **Step 1: 写失败的 API 单元测试**

`frontend/vitest.config.ts`
```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'happy-dom',
    include: ['tests/unit/**/*.spec.ts']
  },
  resolve: {
    alias: {
      '~': fileURLToPath(new URL('./', import.meta.url)),
      '@': fileURLToPath(new URL('./', import.meta.url))
    }
  }
})
```

`frontend/tests/unit/api.spec.ts`
```ts
import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { apiErrorMessage, getAccessToken, setAccessToken } from '~/lib/api'

describe('api token memory', () => {
  it('stores and clears the access token in memory', () => {
    setAccessToken('token-1')
    expect(getAccessToken()).toBe('token-1')
    setAccessToken(null)
    expect(getAccessToken()).toBeNull()
  })
})

describe('apiErrorMessage', () => {
  it('returns the server message from an Axios error', () => {
    const error = new AxiosError('failed', 'ERR', undefined, undefined, {
      data: { code: 40001, message: '参数校验失败' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {} as never
    })
    expect(apiErrorMessage(error)).toBe('参数校验失败')
  })

  it('falls back for network errors', () => {
    expect(apiErrorMessage(new Error('boom'))).toBe('网络错误，请稍后重试')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd frontend
npm run test:unit -- tests/unit/api.spec.ts
```
Expected: FAIL，`Cannot find module '~/lib/api'` 或 `apiErrorMessage is not a function`

- [ ] **Step 3: 写 API 客户端**

`frontend/lib/api.ts`
```ts
import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

const API_BASE = '/api/v1'
const SKIP_REFRESH = [
  '/auth/login',
  '/auth/register',
  '/auth/verify-email',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/resend-verification',
  '/auth/refresh'
]

let accessToken: string | null = null
let refreshing: Promise<string | null> | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export const api = axios.create({
  baseURL: API_BASE,
  withCredentials: true
})

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

async function requestRefresh(): Promise<string | null> {
  const response = await api.post('/auth/refresh')
  const token = response.data?.data?.accessToken as string | undefined
  setAccessToken(token ?? null)
  return token ?? null
}

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = requestRefresh()
      .catch(() => {
        setAccessToken(null)
        return null
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined
    const url = original?.url ?? ''
    const shouldRefresh = error.response?.status === 401
      && original
      && !original._retry
      && !SKIP_REFRESH.some((path) => url.includes(path))

    if (shouldRefresh) {
      original._retry = true
      const token = await refreshAccessToken()
      if (token) {
        original.headers = { ...(original.headers ?? {}), Authorization: `Bearer ${token}` }
        return api(original)
      }
    }
    return Promise.reject(error)
  }
)

export function apiErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)?.message
    if (message) return message
    return '请求失败，请稍后重试'
  }
  return '网络错误，请稍后重试'
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd frontend
npm run test:unit -- tests/unit/api.spec.ts
```
Expected: PASS，3 个测试通过

- [ ] **Step 5: 提交**

```bash
git add frontend/lib/api.ts frontend/vitest.config.ts frontend/tests/unit/api.spec.ts
git commit -m "feat(web): add API client with refresh retry and error helper"
```

---

### Task 3: Pinia Auth Store、启动插件与路由守卫

**Files:**
- Create: `frontend/stores/auth.ts`
- Create: `frontend/plugins/auth.client.ts`
- Create: `frontend/middleware/auth.ts`
- Create: `frontend/middleware/guest.ts`
- Create: `frontend/tests/unit/auth-store.spec.ts`

- [ ] **Step 1: 写失败的 Store 测试**

`frontend/tests/unit/auth-store.spec.ts`
```ts
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn()
  },
  setAccessToken: vi.fn(),
  refreshAccessToken: vi.fn()
}))

vi.mock('~/lib/api', () => mocks)

import { useAuthStore } from '~/stores/auth'

const user = {
  id: 1,
  email: 'player@example.com',
  nickname: 'Player',
  avatarUrl: null,
  bio: null,
  emailVerified: true,
  role: 'USER',
  createdAt: '2026-01-01T00:00:00Z'
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('auth store', () => {
  it('bootstrap loads the current user when refresh succeeds', async () => {
    mocks.refreshAccessToken.mockResolvedValue('token')
    mocks.api.get.mockResolvedValue({ data: { data: user } })

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.user).toEqual(user)
    expect(auth.isAuthenticated).toBe(true)
    expect(mocks.api.get).toHaveBeenCalledWith('/users/me')
  })

  it('login stores the token and user', async () => {
    mocks.api.post.mockResolvedValue({
      data: { data: { accessToken: 'access', expiresIn: 1800, user } }
    })
    const auth = useAuthStore()

    await auth.login('player@example.com', 'secret123')

    expect(mocks.setAccessToken).toHaveBeenCalledWith('access')
    expect(auth.user).toEqual(user)
  })

  it('logout posts and clears state even on success', async () => {
    mocks.api.post.mockResolvedValue({ data: { data: null } })
    const auth = useAuthStore()
    auth.user = user

    await auth.logout()

    expect(mocks.api.post).toHaveBeenCalledWith('/auth/logout')
    expect(auth.user).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenCalledWith(null)
  })

  it('updateProfile replaces the user summary', async () => {
    mocks.api.patch.mockResolvedValue({ data: { data: { ...user, nickname: 'New' } } })
    const auth = useAuthStore()
    auth.user = user

    await auth.updateProfile({ nickname: 'New' })

    expect(auth.user?.nickname).toBe('New')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd frontend
npm run test:unit -- tests/unit/auth-store.spec.ts
```
Expected: FAIL，`Cannot find module '~/stores/auth'`

- [ ] **Step 3: 写 Store、插件与中间件**

`frontend/stores/auth.ts`
```ts
import { defineStore } from 'pinia'
import { api, refreshAccessToken, setAccessToken } from '~/lib/api'

export interface AuthUser {
  id: number
  email: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  emailVerified: boolean
  role: string
  createdAt: string
}

interface AuthState {
  user: AuthUser | null
  ready: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    ready: false
  }),
  getters: {
    isAuthenticated: (state) => state.user !== null
  },
  actions: {
    async bootstrap() {
      if (this.ready) return
      this.ready = true
      const token = await refreshAccessToken()
      if (!token) return
      try {
        await this.fetchMe()
      } catch {
        setAccessToken(null)
        this.user = null
      }
    },
    async login(email: string, password: string) {
      const { data } = await api.post('/auth/login', { email, password })
      setAccessToken(data.data.accessToken)
      this.user = data.data.user
    },
    async register(email: string, nickname: string, password: string) {
      const { data } = await api.post('/auth/register', { email, nickname, password })
      return data.data as AuthUser
    },
    async verifyEmail(token: string) {
      await api.post('/auth/verify-email', { token })
    },
    async resendVerification() {
      await api.post('/auth/resend-verification')
    },
    async forgotPassword(email: string) {
      await api.post('/auth/forgot-password', { email })
    },
    async resetPassword(token: string, newPassword: string) {
      await api.post('/auth/reset-password', { token, newPassword })
    },
    async fetchMe() {
      const { data } = await api.get('/users/me')
      this.user = data.data as AuthUser
    },
    async updateProfile(payload: { nickname?: string; avatarUrl?: string; bio?: string }) {
      const { data } = await api.patch('/users/me', payload)
      this.user = data.data as AuthUser
      return this.user
    },
    async deleteAccount() {
      await api.delete('/users/me')
      setAccessToken(null)
      this.user = null
    },
    async logout() {
      try {
        await api.post('/auth/logout')
      } finally {
        setAccessToken(null)
        this.user = null
      }
    }
  }
})
```

`frontend/plugins/auth.client.ts`
```ts
import { useAuthStore } from '~/stores/auth'

export default defineNuxtPlugin(async () => {
  await useAuthStore().bootstrap()
})
```

`frontend/middleware/auth.ts`
```ts
import { useAuthStore } from '~/stores/auth'

export default defineNuxtRouteMiddleware(async () => {
  const auth = useAuthStore()
  await auth.bootstrap()
  if (!auth.isAuthenticated) {
    return navigateTo('/login')
  }
})
```

`frontend/middleware/guest.ts`
```ts
import { useAuthStore } from '~/stores/auth'

export default defineNuxtRouteMiddleware(async () => {
  const auth = useAuthStore()
  await auth.bootstrap()
  if (auth.isAuthenticated) {
    return navigateTo('/me')
  }
})
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd frontend
npm run test:unit
```
Expected: PASS，API 3 + Store 4 = 7 个测试通过

- [ ] **Step 5: 提交**

```bash
git add frontend/stores frontend/plugins frontend/middleware frontend/tests/unit/auth-store.spec.ts
git commit -m "feat(web): add auth store, bootstrap plugin and route guards"
```

---

### Task 4: 通用认证外壳与登录页

**Files:**
- Create: `frontend/components/AuthShell.vue`
- Create: `frontend/components/Alert.vue`
- Create: `frontend/pages/login.vue`

- [ ] **Step 1: 写通用组件**

`frontend/components/AuthShell.vue`
```vue
<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <section class="mx-auto mt-10 w-full max-w-md rounded-2xl border border-white/10 bg-white/5 p-8 shadow-2xl">
    <h1 class="text-2xl font-semibold tracking-tight">{{ title }}</h1>
    <p v-if="subtitle" class="mt-2 text-sm text-slate-400">{{ subtitle }}</p>
    <div class="mt-6">
      <slot />
    </div>
  </section>
</template>
```

`frontend/components/Alert.vue`
```vue
<script setup lang="ts">
withDefaults(defineProps<{ type?: 'error' | 'success' | 'info'; message: string }>(), {
  type: 'info'
})
</script>

<template>
  <p
    class="mt-4 rounded-lg border px-3 py-2 text-sm"
    :class="{
      'border-red-500/30 bg-red-500/10 text-red-300': type === 'error',
      'border-emerald-500/30 bg-emerald-500/10 text-emerald-300': type === 'success',
      'border-sky-500/30 bg-sky-500/10 text-sky-300': type === 'info'
    }"
  >
    {{ message }}
  </p>
</template>
```

- [ ] **Step 2: 写登录页**

`frontend/pages/login.vue`
```vue
<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'guest' })

const auth = useAuthStore()
const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    await navigateTo('/me')
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="登录" subtitle="使用邮箱和密码登录你的账号">
    <form class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm text-slate-300">
        邮箱
        <input v-model="email" type="email" required autocomplete="email" class="input mt-1" placeholder="you@example.com" />
      </label>
      <label class="block text-sm text-slate-300">
        密码
        <input v-model="password" type="password" required autocomplete="current-password" class="input mt-1" placeholder="至少 8 位，含字母和数字" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
    <div class="mt-4 flex justify-between text-sm">
      <NuxtLink to="/forgot-password" class="text-slate-400 hover:text-white">忘记密码</NuxtLink>
      <NuxtLink to="/register" class="text-brand-300 hover:text-brand-200">注册新账号</NuxtLink>
    </div>
  </AuthShell>
</template>
```

- [ ] **Step 3: 构建确认通过**

Run:
```bash
cd frontend
npm run build
```
Expected: BUILD 成功，退出码 0

- [ ] **Step 4: 提交**

```bash
git add frontend/components frontend/pages/login.vue
git commit -m "feat(web): add login page and auth shell"
```

---

### Task 5: 注册页与邮箱验证页

**Files:**
- Create: `frontend/pages/register.vue`
- Create: `frontend/pages/verify-email.vue`

- [ ] **Step 1: 写注册页**

`frontend/pages/register.vue`
```vue
<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'guest' })

const auth = useAuthStore()
const form = reactive({ email: '', nickname: '', password: '', confirm: '' })
const error = ref('')
const loading = ref(false)
const done = ref(false)

async function submit() {
  error.value = ''
  if (form.password !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await auth.register(form.email.trim(), form.nickname.trim(), form.password)
    done.value = true
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="创建账号" subtitle="注册后需要点击邮箱里的链接完成验证">
    <div v-if="done" class="space-y-4">
      <Alert type="success" message="注册成功。验证邮件已发送，请到 Mailpit（http://localhost:8025）查看并点击验证链接。" />
      <NuxtLink to="/login" class="btn w-full">去登录</NuxtLink>
    </div>
    <form v-else class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm text-slate-300">
        邮箱
        <input v-model="form.email" type="email" required autocomplete="email" class="input mt-1" placeholder="you@example.com" />
      </label>
      <label class="block text-sm text-slate-300">
        昵称
        <input v-model="form.nickname" type="text" required minlength="2" maxlength="40" class="input mt-1" placeholder="2-40 个字符" />
      </label>
      <label class="block text-sm text-slate-300">
        密码
        <input v-model="form.password" type="password" required autocomplete="new-password" class="input mt-1" placeholder="8-72 字节，含字母和数字" />
      </label>
      <label class="block text-sm text-slate-300">
        确认密码
        <input v-model="form.confirm" type="password" required autocomplete="new-password" class="input mt-1" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '注册中…' : '注册' }}
      </button>
    </form>
    <p class="mt-4 text-sm text-slate-400">
      已有账号？
      <NuxtLink to="/login" class="text-brand-300 hover:text-brand-200">直接登录</NuxtLink>
    </p>
  </AuthShell>
</template>
```

- [ ] **Step 2: 写邮箱验证页**

`frontend/pages/verify-email.vue`
```vue
<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const status = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('正在验证邮箱…')
const resending = ref(false)

async function resend() {
  resending.value = true
  try {
    await auth.resendVerification()
    message.value = '新的验证邮件已发送，请查收。'
  } catch (e) {
    message.value = apiErrorMessage(e)
  } finally {
    resending.value = false
  }
}

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    status.value = 'error'
    message.value = '缺少验证令牌，请从邮件中的链接重新打开。'
    return
  }
  try {
    await auth.verifyEmail(token)
    status.value = 'success'
    message.value = '邮箱验证成功，现在可以正常使用社区了。'
    if (auth.isAuthenticated) await auth.fetchMe()
  } catch (e) {
    status.value = 'error'
    message.value = apiErrorMessage(e)
  }
})
</script>

<template>
  <AuthShell title="邮箱验证">
    <div class="space-y-4">
      <Alert
        :type="status === 'success' ? 'success' : status === 'error' ? 'error' : 'info'"
        :message="message"
      />
      <div class="flex flex-col gap-3">
        <NuxtLink v-if="status === 'success'" to="/login" class="btn w-full">去登录</NuxtLink>
        <button
          v-if="status === 'error' && auth.isAuthenticated && !auth.user?.emailVerified"
          class="btn-ghost w-full"
          :disabled="resending"
          @click="resend"
        >
          {{ resending ? '发送中…' : '重新发送验证邮件' }}
        </button>
        <NuxtLink to="/" class="text-center text-sm text-slate-400 hover:text-white">返回首页</NuxtLink>
      </div>
    </div>
  </AuthShell>
</template>
```

- [ ] **Step 3: 构建确认通过**

Run:
```bash
cd frontend
npm run build
```
Expected: BUILD 成功

- [ ] **Step 4: 提交**

```bash
git add frontend/pages/register.vue frontend/pages/verify-email.vue
git commit -m "feat(web): add registration and email verification pages"
```

---

### Task 6: 找回密码与重置密码页

**Files:**
- Create: `frontend/pages/forgot-password.vue`
- Create: `frontend/pages/reset-password.vue`

- [ ] **Step 1: 写找回密码页**

`frontend/pages/forgot-password.vue`
```vue
<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'guest' })

const auth = useAuthStore()
const email = ref('')
const error = ref('')
const loading = ref(false)
const sent = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.forgotPassword(email.value.trim())
    sent.value = true
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="找回密码" subtitle="我们会向注册邮箱发送重置链接">
    <div v-if="sent" class="space-y-4">
      <Alert type="success" message="如果该邮箱已注册，重置邮件会发送到你的邮箱（开发环境请查看 Mailpit）。" />
      <NuxtLink to="/login" class="btn w-full">返回登录</NuxtLink>
    </div>
    <form v-else class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm text-slate-300">
        注册邮箱
        <input v-model="email" type="email" required autocomplete="email" class="input mt-1" placeholder="you@example.com" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '发送中…' : '发送重置邮件' }}
      </button>
    </form>
  </AuthShell>
</template>
```

- [ ] **Step 2: 写重置密码页**

`frontend/pages/reset-password.vue`
```vue
<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const token = typeof route.query.token === 'string' ? route.query.token : ''
const password = ref('')
const confirm = ref('')
const error = ref('')
const loading = ref(false)
const done = ref(false)

async function submit() {
  error.value = ''
  if (!token) {
    error.value = '缺少重置令牌，请从邮件中的链接重新打开。'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await auth.resetPassword(token, password.value)
    done.value = true
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="重置密码">
    <div v-if="done" class="space-y-4">
      <Alert type="success" message="密码已重置，所有已登录会话已失效，请重新登录。" />
      <NuxtLink to="/login" class="btn w-full">去登录</NuxtLink>
    </div>
    <form v-else class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm text-slate-300">
        新密码
        <input v-model="password" type="password" required autocomplete="new-password" class="input mt-1" placeholder="8-72 字节，含字母和数字" />
      </label>
      <label class="block text-sm text-slate-300">
        确认新密码
        <input v-model="confirm" type="password" required autocomplete="new-password" class="input mt-1" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '重置中…' : '重置密码' }}
      </button>
    </form>
  </AuthShell>
</template>
```

- [ ] **Step 3: 构建确认通过**

Run:
```bash
cd frontend
npm run build
```
Expected: BUILD 成功

- [ ] **Step 4: 提交**

```bash
git add frontend/pages/forgot-password.vue frontend/pages/reset-password.vue
git commit -m "feat(web): add forgot and reset password pages"
```

---

### Task 7: 个人资料、验证重发、注销与登出

**Files:**
- Create: `frontend/pages/me.vue`

- [ ] **Step 1: 写个人资料页**

`frontend/pages/me.vue`
```vue
<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'auth' })

const auth = useAuthStore()
const form = reactive({ nickname: '', avatarUrl: '', bio: '' })
const message = ref('')
const messageType = ref<'success' | 'error' | 'info'>('info')
const saving = ref(false)
const resending = ref(false)

watch(
  () => auth.user,
  (user) => {
    if (!user) return
    form.nickname = user.nickname
    form.avatarUrl = user.avatarUrl ?? ''
    form.bio = user.bio ?? ''
  },
  { immediate: true }
)

function notify(type: 'success' | 'error' | 'info', text: string) {
  messageType.value = type
  message.value = text
}

async function save() {
  message.value = ''
  saving.value = true
  try {
    await auth.updateProfile({
      nickname: form.nickname.trim(),
      avatarUrl: form.avatarUrl.trim(),
      bio: form.bio.trim()
    })
    notify('success', '资料已保存')
  } catch (e) {
    notify('error', apiErrorMessage(e))
  } finally {
    saving.value = false
  }
}

async function resend() {
  resending.value = true
  try {
    await auth.resendVerification()
    notify('success', '验证邮件已重新发送')
  } catch (e) {
    notify('error', apiErrorMessage(e))
  } finally {
    resending.value = false
  }
}

async function removeAccount() {
  if (!window.confirm('确定要注销账号吗？该操作会匿名化资料并立即退出所有会话。')) return
  try {
    await auth.deleteAccount()
    await navigateTo('/login')
  } catch (e) {
    notify('error', apiErrorMessage(e))
  }
}

async function logout() {
  await auth.logout()
  await navigateTo('/login')
}
</script>

<template>
  <div v-if="auth.user" class="mx-auto max-w-2xl space-y-6">
    <section class="rounded-2xl border border-white/10 bg-white/5 p-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold">{{ auth.user.nickname }}</h1>
          <p class="mt-1 text-sm text-slate-400">{{ auth.user.email }} · {{ auth.user.role }}</p>
        </div>
        <span
          class="rounded-full px-3 py-1 text-xs"
          :class="auth.user.emailVerified ? 'bg-emerald-500/15 text-emerald-300' : 'bg-amber-500/15 text-amber-300'"
        >
          {{ auth.user.emailVerified ? '邮箱已验证' : '邮箱未验证' }}
        </span>
      </div>
      <button
        v-if="!auth.user.emailVerified"
        class="btn-ghost mt-4"
        :disabled="resending"
        @click="resend"
      >
        {{ resending ? '发送中…' : '重新发送验证邮件' }}
      </button>
    </section>

    <section class="rounded-2xl border border-white/10 bg-white/5 p-6">
      <h2 class="text-lg font-medium">编辑资料</h2>
      <form class="mt-4 space-y-4" @submit.prevent="save">
        <label class="block text-sm text-slate-300">
          昵称
          <input v-model="form.nickname" type="text" minlength="2" maxlength="40" required class="input mt-1" />
        </label>
        <label class="block text-sm text-slate-300">
          头像 URL
          <input v-model="form.avatarUrl" type="url" class="input mt-1" placeholder="https://example.com/avatar.png（留空清除）" />
        </label>
        <label class="block text-sm text-slate-300">
          简介
          <textarea v-model="form.bio" rows="3" maxlength="500" class="input mt-1" placeholder="介绍一下自己（留空清除）" />
        </label>
        <Alert v-if="message" :type="messageType" :message="message" />
        <div class="flex gap-3">
          <button class="btn" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存资料' }}</button>
          <button class="btn-ghost" type="button" @click="logout">退出登录</button>
        </div>
      </form>
    </section>

    <section class="rounded-2xl border border-red-500/20 bg-red-500/5 p-6">
      <h2 class="text-lg font-medium text-red-300">危险操作</h2>
      <p class="mt-2 text-sm text-slate-400">注销后邮箱会被匿名化，所有刷新会话立即失效。</p>
      <button class="mt-4 rounded-lg border border-red-500/40 px-4 py-2 text-sm text-red-300 hover:bg-red-500/10" type="button" @click="removeAccount">
        注销账号
      </button>
    </section>
  </div>
</template>
```

- [ ] **Step 2: 构建确认通过**

Run:
```bash
cd frontend
npm run build
```
Expected: BUILD 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/pages/me.vue
git commit -m "feat(web): add profile page with verification, logout and account deletion"
```

---

### Task 8: 顶栏接入认证状态

**Files:**
- Modify: `frontend/layouts/default.vue`

- [ ] **Step 1: 写认证感知的布局**

`frontend/layouts/default.vue`
```vue
<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'

const auth = useAuthStore()

async function logout() {
  await auth.logout()
  await navigateTo('/login')
}
</script>

<template>
  <div class="min-h-screen bg-slate-950">
    <header class="border-b border-white/10">
      <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
        <NuxtLink to="/" class="text-lg font-semibold tracking-wide text-brand-300">
          GalHub
        </NuxtLink>
        <ClientOnly>
          <nav v-if="auth.isAuthenticated" class="flex items-center gap-3 text-sm">
            <NuxtLink to="/me" class="text-slate-200 hover:text-white">{{ auth.user?.nickname }}</NuxtLink>
            <button class="btn-ghost" type="button" @click="logout">退出</button>
          </nav>
          <nav v-else class="flex items-center gap-3 text-sm">
            <NuxtLink to="/login" class="text-slate-300 hover:text-white">登录</NuxtLink>
            <NuxtLink to="/register" class="btn">注册</NuxtLink>
          </nav>
        </ClientOnly>
      </div>
    </header>
    <main class="mx-auto max-w-5xl px-4 py-10">
      <slot />
    </main>
  </div>
</template>
```

- [ ] **Step 2: 构建确认通过**

Run:
```bash
cd frontend
npm run build
```
Expected: BUILD 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/layouts/default.vue
git commit -m "feat(web): wire auth state into header"
```

---

### Task 9: Playwright E2E 认证黄金路径

**Files:**
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/auth.spec.ts`

- [ ] **Step 1: 写 Playwright 配置**

`frontend/playwright.config.ts`
```ts
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 120_000
  }
})
```

- [ ] **Step 2: 写 E2E 测试**

`frontend/e2e/auth.spec.ts`
```ts
import { expect, test, type APIRequestContext } from '@playwright/test'

async function fetchVerificationToken(request: APIRequestContext, email: string): Promise<string> {
  for (let attempt = 0; attempt < 30; attempt++) {
    const listResponse = await request.get('http://localhost:8025/api/v2/messages')
    if (listResponse.ok()) {
      const list = await listResponse.json()
      const messages: Array<any> = list.messages ?? []
      for (const message of messages) {
        const recipients: Array<any> = message.To ?? []
        if (!recipients.some((to) => to.Address === email)) continue
        const detailResponse = await request.get(`http://localhost:8025/api/v2/message/${message.ID}`)
        if (!detailResponse.ok()) continue
        const detail = await detailResponse.json()
        const text = `${detail.Text ?? ''}\n${detail.HTML ?? ''}\n${detail.Snippet ?? ''}`
        const match = text.match(/token=([A-Za-z0-9_-]+)/)
        if (match) return match[1]
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`verification email for ${email} not found in Mailpit`)
}

test('register, verify email, login and logout', async ({ page, request }) => {
  const email = `e2e-${Date.now()}@example.com`

  await page.goto('/register')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('昵称').fill('E2E')
  await page.getByLabel('密码', { exact: true }).fill('secret123')
  await page.getByLabel('确认密码').fill('secret123')
  await page.getByRole('button', { name: '注册' }).click()
  await expect(page.getByText('注册成功')).toBeVisible()

  const token = await fetchVerificationToken(request, email)
  await page.goto(`/verify-email?token=${token}`)
  await expect(page.getByText('邮箱验证成功')).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码').fill('secret123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/me$/)
  await expect(page.getByText('E2E')).toBeVisible()

  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
})
```

- [ ] **Step 3: 启动依赖并安装浏览器**

Run:
```bash
docker compose up -d
mvn -f backend/pom.xml spring-boot:run
```
（后端保持运行；另开终端）
```bash
cd frontend
npx playwright install chromium
```

- [ ] **Step 4: 运行 E2E**

Run:
```bash
cd frontend
npm run test:e2e
```
Expected: 1 passed（`register, verify email, login and logout`）

- [ ] **Step 5: 提交**

```bash
git add frontend/playwright.config.ts frontend/e2e/auth.spec.ts
git commit -m "test(web): add Playwright auth golden path"
```

---

### Task 10: README 前端说明与最终验证

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 补充 README 前端章节**

在 README.md 的 `## 认证接口速查` 之前加入：
```markdown
## 前端开发

```bash
cd frontend
npm install
npm run dev
```

- Web: http://localhost:3000
- API 通过 Nuxt 同源代理到 `http://localhost:8080/api/v1`
- 前端依赖后端运行；验证邮件在 Mailpit（http://localhost:8025）
- 单测：`npm run test:unit`
- E2E：`npm run test:e2e`（需 docker compose、后端、Mailpit 运行）
```

- [ ] **Step 2: 运行前端单测与构建**

Run:
```bash
cd frontend
npm run test:unit
npm run build
```
Expected: 7 个单测通过；BUILD 成功

- [ ] **Step 3: 提交**

```bash
git add README.md
git commit -m "docs: document frontend development"
```

---

## Self-Review

**1. Spec coverage（P1-B 范围）**

| 设计要求 | 覆盖任务 |
|---|---|
| Nuxt 3 + Tailwind 脚手架 | Task 1 |
| API client、access token 内存态、401 刷新重试 | Task 2 |
| Pinia auth store、启动恢复、路由守卫 | Task 3 |
| 登录 | Task 4 |
| 注册 + 邮箱验证 | Task 5 |
| 找回/重置密码 | Task 6 |
| 资料查看/编辑、验证重发、注销、登出 | Task 7 |
| 顶栏认证状态 | Task 8 |
| E2E 黄金路径 | Task 9 |
| 文档与构建验证 | Task 10 |

**2. Placeholder scan**

无 TBD/TODO；每个代码步骤包含完整实现。

**3. Type consistency**

- `AuthUser` 字段与后端 `UserProfileResponse` 对齐（id/email/nickname/avatarUrl/bio/emailVerified/role/createdAt）。
- `api` 实例在 `lib/api.ts` 定义，store 与页面统一从 `~/lib/api` 导入。
- refresh Cookie 由浏览器自动携带，前端不读取也不存储 refresh token。
- 认证相关页面均设置为 CSR（routeRules ssr:false）；P2 公共内容页再启用 SSR。

**说明**：P1-B 表单使用 Tailwind 原生组件；Naive UI 在 P2 复杂组件（表格/日期/弹层）引入，避免过早引入 SSR 样式收集成本。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-11-16-p1b-frontend-auth.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**


