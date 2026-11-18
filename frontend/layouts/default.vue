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
