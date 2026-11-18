<script setup lang="ts">
import type { TopicListItem } from '~/lib/forum'

defineProps<{ topic: TopicListItem }>()
</script>

<template>
  <li class="group relative flex items-start gap-3 px-4 py-3.5 transition-colors hover:bg-gold-500/[0.06]">
    <span
      class="pointer-events-none absolute inset-y-2 left-0 w-0.5 rounded-full bg-gold-500/0 transition group-hover:bg-gold-500/70"
      aria-hidden="true"
    />
    <div class="mt-0.5 flex flex-col gap-1">
      <span v-if="topic.pinned" class="badge-wine">置顶</span>
      <span v-if="topic.locked" class="badge-amber">锁定</span>
    </div>
    <div class="min-w-0 flex-1">
      <NuxtLink
        :to="`/topics/${topic.id}`"
        class="font-medium text-parchment-50 transition group-hover:text-gold-200"
      >
        {{ topic.title }}
      </NuxtLink>
      <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-parchment-100/60">
        <span class="text-parchment-100/70">{{ topic.author.nickname }}</span>
        <span aria-hidden="true" class="text-gold-600/60">·</span>
        <span>{{ topic.postCount }} 回复 · {{ topic.viewCount }} 浏览</span>
        <span aria-hidden="true" class="text-gold-600/60">·</span>
        <span>最后回复 <LocalTime :value="topic.lastPostAt" /></span>
      </p>
    </div>
    <NuxtLink
      :to="`/forum/${topic.categorySlug}`"
      class="hidden shrink-0 text-xs text-gold-300/75 transition hover:text-gold-100 sm:block"
    >
      {{ topic.categoryName }}
    </NuxtLink>
  </li>
</template>
