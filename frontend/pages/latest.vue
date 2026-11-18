<script setup lang="ts">
import { fetchLatestTopics, type TopicListItem } from '~/lib/forum'

const auth = useAuthStore()
const { data, error } = await useAsyncData('latest-topics', () => fetchLatestTopics())

const items = ref<TopicListItem[]>(data.value?.items ?? [])
const nextCursor = ref<string | null>(data.value?.nextCursor ?? null)
const loading = ref(false)

async function loadMore() {
  if (!nextCursor.value || loading.value) return
  loading.value = true
  try {
    const page = await fetchLatestTopics(nextCursor.value)
    items.value = [...items.value, ...page.items]
    nextCursor.value = page.nextCursor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="kicker">LATEST REPORTS</p>
        <h1 class="section-title mt-1.5">最新讨论</h1>
        <p class="mt-1.5 text-sm text-parchment-100/70">全站最新回复的主题</p>
      </div>
      <NuxtLink v-if="auth.isAuthenticated" to="/topics/new" class="btn">发主题</NuxtLink>
    </header>

    <div class="divider-gold" aria-hidden="true" />

    <p v-if="error" class="rounded-lg border border-wine-500/50 bg-wine-600/15 p-4 text-sm text-wine-100">
      主题列表加载失败，请稍后重试。
    </p>
    <TopicFeed
      v-else
      :items="items"
      :next-cursor="nextCursor"
      :loading="loading"
      empty-text="还没有任何主题。"
      @load-more="loadMore"
    />
  </section>
</template>
