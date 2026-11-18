<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const menuOpen = ref(false)

watch(() => route.fullPath, () => {
  menuOpen.value = false
})

async function logout() {
  await auth.logout()
  await navigateTo('/login')
}
</script>

<template>
  <div class="app-shell relative min-h-screen">
    <header class="sticky top-0 z-40 border-b border-gold-700/30 bg-ink-950/85 backdrop-blur-md">
      <div class="h-[3px] w-full bg-gradient-to-r from-wine-800 via-gold-500 to-wine-800 opacity-80" aria-hidden="true" />
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
        <div class="flex items-center gap-5">
          <NuxtLink to="/" class="group flex items-center gap-3" aria-label="冒险者公会 首页">
            <span class="grid h-10 w-10 place-items-center drop-shadow-[0_2px_6px_rgba(0,0,0,0.6)]">
              <Crest class="h-9 w-9" />
            </span>
            <span class="leading-tight">
              <span class="block font-display text-base font-bold tracking-[0.18em] text-gold-200 transition group-hover:text-gold-100 sm:text-lg">
                冒险者公会
              </span>
              <span class="block text-[9px] uppercase tracking-[0.34em] text-parchment-100/55">
                GAL GUILD
              </span>
            </span>
          </NuxtLink>

          <nav class="hidden items-center gap-1 sm:flex" aria-label="主导航">
            <NuxtLink to="/forum" class="nav-link">论坛</NuxtLink>
            <NuxtLink to="/latest" class="nav-link">最新</NuxtLink>
          </nav>
        </div>

        <div class="flex items-center gap-2">
          <ClientOnly>
            <nav v-if="auth.isAuthenticated" class="hidden items-center gap-2 sm:flex" aria-label="账号">
              <NuxtLink to="/me" class="nav-link max-w-[12rem] truncate">{{ auth.user?.nickname }}</NuxtLink>
              <button class="btn-ghost" type="button" @click="logout">退出</button>
            </nav>
            <nav v-else class="hidden items-center gap-2 sm:flex" aria-label="账号">
              <NuxtLink to="/login" class="nav-link">登录</NuxtLink>
              <NuxtLink to="/register" class="btn">注册</NuxtLink>
            </nav>
          </ClientOnly>

          <button
            type="button"
            class="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-gold-700/40 bg-ink-900/60 text-gold-200 transition hover:border-gold-500/60 hover:text-gold-100 sm:hidden"
            aria-label="菜单"
            aria-controls="mobile-menu"
            :aria-expanded="menuOpen ? 'true' : 'false'"
            @click="menuOpen = !menuOpen"
          >
            <svg v-if="!menuOpen" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
            <svg v-else viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
      </div>

      <div v-if="menuOpen" id="mobile-menu" class="border-t border-gold-700/25 bg-ink-950/95 sm:hidden">
        <nav class="mx-auto flex max-w-6xl flex-col gap-1 px-4 py-3" aria-label="移动导航">
          <NuxtLink to="/forum" class="nav-link">论坛</NuxtLink>
          <NuxtLink to="/latest" class="nav-link">最新</NuxtLink>
          <ClientOnly>
            <template v-if="auth.isAuthenticated">
              <NuxtLink to="/me" class="nav-link mt-1 truncate">{{ auth.user?.nickname }}</NuxtLink>
              <button class="btn-ghost mt-2 w-full" type="button" @click="logout">退出登录</button>
            </template>
            <template v-else>
              <NuxtLink to="/login" class="nav-link mt-1">登录</NuxtLink>
              <NuxtLink to="/register" class="btn mt-2 w-full">注册</NuxtLink>
            </template>
          </ClientOnly>
        </nav>
      </div>
    </header>

    <main class="relative mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <div class="page-enter">
        <slot />
      </div>
    </main>

    <footer class="relative mx-auto max-w-6xl px-4 pb-10">
      <div class="divider-gold" />
      <p class="mt-4 text-center text-xs text-parchment-100/55">
        冒险者公会 · 交流补丁、语音、设定集、攻略与官方商店信息
      </p>
    </footer>
  </div>
</template>
