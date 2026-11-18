<script setup lang="ts">
import type { TopicListItem } from '~/lib/forum'

defineProps<{ topic: TopicListItem }>()
</script>

<template>
  <li class="flex items-start gap-3 px-4 py-3 hover:bg-white/5">
    <div class="mt-0.5 flex flex-col gap-1">
      <span v-if="topic.pinned" class="rounded bg-brand-500/20 px-1.5 py-0.5 text-[10px] text-brand-200">置顶</span>
      <span v-if="topic.locked" class="rounded bg-amber-500/20 px-1.5 py-0.5 text-[10px] text-amber-200">锁定</span>
    </div>
    <div class="min-w-0 flex-1">
      <NuxtLink :to="`/topics/${topic.id}`" class="font-medium hover:text-brand-300">
        {{ topic.title }}
      </NuxtLink>
      <p class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-400">
        <span>{{ topic.author.nickname }}</span>
        <span>{{ topic.postCount }} 回复 · {{ topic.viewCount }} 浏览</span>
        <span>最后回复 <LocalTime :value="topic.lastPostAt" /></span>
      </p>
    </div>
    <NuxtLink :to="`/forum/${topic.categorySlug}`" class="hidden shrink-0 text-xs text-slate-400 hover:text-white sm:block">
      {{ topic.categoryName }}
    </NuxtLink>
  </li>
</template>
