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
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold">论坛</h1>
        <p class="mt-1 text-sm text-slate-400">按分区浏览讨论；登录并验证邮箱后可发帖。</p>
      </div>
      <NuxtLink v-if="auth.isAuthenticated" to="/topics/new" class="btn">发主题</NuxtLink>
    </header>

    <p v-if="error" class="rounded border border-red-500/40 bg-red-500/10 p-4 text-red-300">
      分区加载失败，请稍后重试。
    </p>

    <div v-else class="space-y-4">
      <CategoryCard v-for="category in categories" :key="category.id" :category="category" />
    </div>
  </section>
</template>
