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
    <div v-if="items.length === 0" class="panel-soft p-10 text-center">
      <div
        class="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-full border border-gold-700/40 text-xl"
        aria-hidden="true"
      >
        🗒️
      </div>
      <p class="text-parchment-100/70">{{ emptyText }}</p>
    </div>
    <ul v-else class="panel divide-y divide-gold-700/15 overflow-hidden">
      <TopicRow v-for="topic in items" :key="topic.id" :topic="topic" />
    </ul>
    <div v-if="nextCursor" class="flex justify-center">
      <button class="btn-ghost" :disabled="loading" @click="emit('loadMore')">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
    <p
      v-else-if="items.length > 0"
      class="flex items-center justify-center gap-3 text-center text-xs text-parchment-100/55"
    >
      <span class="h-px w-8 bg-gold-700/40" aria-hidden="true" />
      已经到底了
      <span class="h-px w-8 bg-gold-700/40" aria-hidden="true" />
    </p>
  </div>
</template>
