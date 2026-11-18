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
    <div v-if="items.length === 0" class="card p-10 text-center">
      <div
        class="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-full bg-neutral-100 text-muted"
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 5.5h16v11H9l-5 4v-15Z" />
          <path d="M8 9.5h8M8 12.5h5" />
        </svg>
      </div>
      <p class="text-sm text-muted">{{ emptyText }}</p>
    </div>
    <ul v-else class="card divide-y divide-border overflow-hidden">
      <TopicRow v-for="topic in items" :key="topic.id" :topic="topic" />
    </ul>
    <div v-if="nextCursor" class="flex justify-center">
      <button class="btn-ghost" :disabled="loading" @click="emit('loadMore')">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
    <p
      v-else-if="items.length > 0"
      class="text-center text-xs text-muted"
    >
      已经到底了
    </p>
  </div>
</template>
