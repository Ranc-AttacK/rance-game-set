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
