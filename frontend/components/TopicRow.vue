<script setup lang="ts">
import type { TopicListItem } from '~/lib/forum'

defineProps<{ topic: TopicListItem }>()
</script>

<template>
  <li class="group flex items-start gap-3 px-4 py-3.5 transition-colors hover:bg-neutral-50 sm:px-5">
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-2">
        <span v-if="topic.pinned" class="badge">置顶</span>
        <span v-if="topic.locked" class="badge">锁定</span>
        <NuxtLink
          :to="`/topics/${topic.id}`"
          class="min-w-0 truncate font-medium text-foreground transition group-hover:text-sakura-600"
        >
          {{ topic.title }}
        </NuxtLink>
      </div>
      <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
        <span class="text-neutral-600">{{ topic.author.nickname }}</span>
        <span aria-hidden="true" class="text-neutral-300">·</span>
        <span>{{ topic.postCount }} 回复 · {{ topic.viewCount }} 浏览</span>
        <span aria-hidden="true" class="text-neutral-300">·</span>
        <span>最后回复 <LocalTime :value="topic.lastPostAt" /></span>
      </p>
    </div>
    <NuxtLink
      :to="`/forum/${topic.categorySlug}`"
      class="hidden shrink-0 rounded-full border border-border bg-card px-2 py-0.5 text-[11px] text-muted transition hover:border-neutral-300 hover:text-foreground sm:block"
    >
      {{ topic.categoryName }}
    </NuxtLink>
  </li>
</template>
