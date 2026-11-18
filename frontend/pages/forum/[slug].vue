<script setup lang="ts">
import { fetchCategoryTopics, type TopicListItem } from '~/lib/forum'

const route = useRoute()
const auth = useAuthStore()
const slug = computed(() => String(route.params.slug))

const { data, error } = await useAsyncData(
  () => `category-topics-${slug.value}`,
  () => fetchCategoryTopics(slug.value)
)

const items = ref<TopicListItem[]>(data.value?.items ?? [])
const nextCursor = ref<string | null>(data.value?.nextCursor ?? null)
const loading = ref(false)

watch(data, (page) => {
  items.value = page?.items ?? []
  nextCursor.value = page?.nextCursor ?? null
})

async function loadMore() {
  if (!nextCursor.value || loading.value) return
  loading.value = true
  try {
    const page = await fetchCategoryTopics(slug.value, nextCursor.value)
    items.value = [...items.value, ...page.items]
    nextCursor.value = page.nextCursor
  } finally {
    loading.value = false
  }
}

const heading = computed(() => items.value[0]?.categoryName ?? '分区讨论')
</script>

<template>
  <section class="space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold">{{ heading }}</h1>
        <NuxtLink to="/forum" class="text-sm text-slate-400 hover:text-white">← 返回分区列表</NuxtLink>
      </div>
      <NuxtLink v-if="auth.isAuthenticated" to="/topics/new" class="btn">发主题</NuxtLink>
    </header>

    <p v-if="error" class="rounded border border-red-500/40 bg-red-500/10 p-4 text-red-300">
      主题列表加载失败，请稍后重试。
    </p>
    <TopicFeed
      v-else
      :items="items"
      :next-cursor="nextCursor"
      :loading="loading"
      empty-text="这里还没有主题，来发第一帖吧。"
      @load-more="loadMore"
    />
  </section>
</template>
