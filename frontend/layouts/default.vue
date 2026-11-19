<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { fetchUnreadCount } from '~/lib/forum'

const auth = useAuthStore()
const route = useRoute()
const menuOpen = ref(false)
const unread = ref(0)
let unreadTimer: ReturnType<typeof setInterval> | null = null

watch(() => route.fullPath, () => {
  menuOpen.value = false
})

watch(() => auth.isAuthenticated, () => {
  void loadUnread()
})

onMounted(() => {
  void loadUnread()
  unreadTimer = setInterval(() => void loadUnread(), 60_000)
})

onBeforeUnmount(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})

async function loadUnread() {
  if (!auth.isAuthenticated) {
    unread.value = 0
    return
  }
  try {
    unread.value = await fetchUnreadCount()
  } catch {
    unread.value = 0
  }
}

async function logout() {
  await auth.logout()
  await navigateTo('/login')
}
</script>

<template>
  <div class="relative min-h-screen">
    <SiteBackdrop />

    <header class="sticky top-0 z-40 border-b border-border/80 bg-card/80 backdrop-blur">
      <div class="relative mx-auto flex h-14 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <div class="flex min-w-0 items-center gap-6">
          <NuxtLink to="/" class="flex min-w-0 items-center gap-2" aria-label="Galgame 社区 首页">
            <span class="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-accent text-accent-foreground">
              <svg viewBox="0 0 24 24" class="h-4 w-4" fill="currentColor" aria-hidden="true">
                <path d="M12 2.4l2.1 7.5 7.5 2.1-7.5 2.1L12 21.6l-2.1-7.5L2.4 12l7.5-2.1L12 2.4Z" />
              </svg>
            </span>
            <span class="truncate text-base font-bold tracking-tight text-foreground">Galgame 社区</span>
          </NuxtLink>

          <nav class="hidden items-center gap-1 sm:flex" aria-label="主导航">
            <NuxtLink to="/forum" class="nav-link">论坛</NuxtLink>
            <NuxtLink to="/latest" class="nav-link">最新</NuxtLink>
          </nav>
        </div>

        <div class="flex items-center gap-2">
          <ClientOnly>
            <nav v-if="auth.isAuthenticated" class="hidden items-center gap-1 sm:flex" aria-label="账号">
              <NuxtLink to="/notifications" class="relative grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-neutral-100 hover:text-foreground" aria-label="通知">
                <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
                  <path d="M13.7 21a2 2 0 0 1-3.4 0" />
                </svg>
                <span
                  v-if="unread > 0"
                  class="absolute right-1 top-1 min-w-[16px] rounded-full bg-sakura-500 px-1 text-center text-[10px] font-semibold leading-4 text-white"
                >
                  {{ unread > 99 ? '99+' : unread }}
                </span>
              </NuxtLink>
              <NuxtLink to="/me" class="nav-link max-w-[12rem] truncate">{{ auth.user?.nickname }}</NuxtLink>
              <button class="btn-ghost px-3 py-1.5" type="button" @click="logout">退出</button>
            </nav>
            <nav v-else class="hidden items-center gap-2 sm:flex" aria-label="账号">
              <NuxtLink to="/login" class="nav-link">登录</NuxtLink>
              <NuxtLink to="/register" class="btn px-3 py-1.5">注册</NuxtLink>
            </nav>
          </ClientOnly>

          <button
            type="button"
            class="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-card text-foreground transition hover:bg-neutral-50 sm:hidden"
            aria-label="菜单"
            aria-controls="mobile-menu"
            :aria-expanded="menuOpen ? 'true' : 'false'"
            @click="menuOpen = !menuOpen"
          >
            <svg v-if="!menuOpen" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
            <svg v-else viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div
          v-if="menuOpen"
          id="mobile-menu"
          class="absolute left-4 right-4 top-full mt-2 rounded-xl border border-border bg-card p-2 shadow-hover sm:hidden"
        >
          <nav class="flex flex-col gap-1" aria-label="移动导航">
            <NuxtLink to="/forum" class="menu-link">论坛</NuxtLink>
            <NuxtLink to="/latest" class="menu-link">最新</NuxtLink>
            <ClientOnly>
              <template v-if="auth.isAuthenticated">
                <NuxtLink to="/notifications" class="menu-link mt-1">
                  通知<span v-if="unread > 0" class="ml-1 text-sakura-600">({{ unread }})</span>
                </NuxtLink>
                <NuxtLink to="/me" class="menu-link truncate">{{ auth.user?.nickname }}</NuxtLink>
                <button class="btn-ghost mt-2 w-full" type="button" @click="logout">退出登录</button>
              </template>
              <template v-else>
                <NuxtLink to="/login" class="menu-link mt-1">登录</NuxtLink>
                <NuxtLink to="/register" class="btn mt-2 w-full">注册</NuxtLink>
              </template>
            </ClientOnly>
          </nav>
        </div>
      </div>
    </header>

    <main class="relative z-10 mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-12">
      <div class="page-enter">
        <slot />
      </div>
    </main>

    <footer class="relative z-10 mx-auto w-full max-w-6xl px-4 pb-10 pt-2 text-center text-xs text-muted sm:px-6">
      Galgame 社区 · 交流补丁、语音、设定集、攻略与官方商店信息
    </footer>
  </div>
</template>
