<script setup lang="ts">
import {
  fetchCategories,
  fetchLatestTopics,
  type CategoryNode,
  type TopicListItem
} from '~/lib/forum'

const auth = useAuthStore()

const { data: categories } = await useAsyncData<CategoryNode[]>(
  'forum-categories',
  () => fetchCategories()
)

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
  <section class="space-y-6">
    <header class="flex flex-col items-center gap-2 text-center">
      <p class="text-[11px] font-medium uppercase tracking-[0.35em] text-muted">LATEST</p>
      <h1 class="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">最新讨论</h1>
      <p class="text-sm text-muted">全站最新回复的主题</p>
      <NuxtLink v-if="auth.isAuthenticated" to="/topics/new" class="btn mt-2">发主题</NuxtLink>
    </header>

    <div class="grid gap-6 lg:grid-cols-[220px_minmax(0,1fr)]">
      <ForumMenu :categories="categories ?? []" active="latest" />

      <div class="min-w-0">
        <p v-if="error" class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
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
      </div>
    </div>
  </section>
</template>
