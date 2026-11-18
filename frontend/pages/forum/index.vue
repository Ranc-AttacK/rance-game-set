<script setup lang="ts">
import { fetchCategories, type CategoryNode } from '~/lib/forum'

const auth = useAuthStore()
const { data, error } = await useAsyncData<CategoryNode[]>(
  'forum-categories',
  () => fetchCategories()
)

const categories = computed(() => data.value ?? [])
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="kicker">GUILD QUEST BOARD</p>
        <h1 class="section-title mt-2">论坛</h1>
        <p class="mt-1.5 text-sm text-parchment-100/70">按分区浏览讨论；登录并验证邮箱后可发帖。</p>
      </div>
      <NuxtLink v-if="auth.isAuthenticated" to="/topics/new" class="btn">发主题</NuxtLink>
    </header>

    <div class="divider-gold" aria-hidden="true" />

    <p v-if="error" class="rounded-lg border border-wine-500/50 bg-wine-600/15 p-4 text-sm text-wine-100">
      分区加载失败，请稍后重试。
    </p>

    <div v-else class="space-y-4">
      <CategoryCard v-for="category in categories" :key="category.id" :category="category" />
    </div>
  </section>
</template>
