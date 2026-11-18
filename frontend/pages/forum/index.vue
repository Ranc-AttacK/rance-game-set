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
  <section class="space-y-8">
    <header class="flex flex-col items-center gap-2 text-center">
      <p class="text-[11px] font-medium uppercase tracking-[0.35em] text-muted">COMMUNITY</p>
      <h1 class="page-title">论坛</h1>
      <p class="text-sm text-muted">全部分区 · 登录并验证邮箱后可发帖</p>
      <NuxtLink v-if="auth.isAuthenticated" to="/topics/new" class="btn mt-3">发主题</NuxtLink>
    </header>

    <p v-if="error" class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
      分区加载失败，请稍后重试。
    </p>

    <div v-else class="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
      <CategoryCard v-for="category in categories" :key="category.id" :category="category" />
    </div>
  </section>
</template>
