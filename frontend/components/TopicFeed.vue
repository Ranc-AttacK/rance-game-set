<script setup lang="ts">
import type { TopicListItem } from '~/lib/forum'

defineProps<{
  items: TopicListItem[]
  nextCursor: string | null
  loading: boolean
  emptyText: string
}>()

const emit = defineEmits<{ loadMore: [] }>()
</script>

<template>
  <div class="space-y-4">
    <div
      v-if="items.length === 0"
      class="rounded-lg border border-white/10 bg-white/5 p-10 text-center text-slate-400"
    >
      {{ emptyText }}
    </div>
    <ul v-else class="divide-y divide-white/10 overflow-hidden rounded-lg border border-white/10 bg-white/5">
      <TopicRow v-for="topic in items" :key="topic.id" :topic="topic" />
    </ul>
    <div v-if="nextCursor" class="flex justify-center">
      <button class="btn-ghost" :disabled="loading" @click="emit('loadMore')">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
    <p v-else-if="items.length > 0" class="text-center text-xs text-slate-500">已经到底了</p>
  </div>
</template>
