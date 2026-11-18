<script setup lang="ts">
import type { CategoryNode } from '~/lib/forum'

defineProps<{ category: CategoryNode }>()
</script>

<template>
  <article class="rounded-lg border border-white/10 bg-white/5 p-5">
    <div class="flex items-start gap-4">
      <div class="text-2xl leading-none">{{ category.icon || '💬' }}</div>
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
          <NuxtLink :to="`/forum/${category.slug}`" class="text-lg font-semibold hover:text-brand-300">
            {{ category.name }}
          </NuxtLink>
          <span class="text-xs text-slate-400">
            {{ category.topicCount }} 主题 · {{ category.postCount }} 帖子
          </span>
        </div>
        <p v-if="category.description" class="mt-1 text-sm text-slate-400">{{ category.description }}</p>
        <ul v-if="category.children.length" class="mt-3 flex flex-wrap gap-2">
          <li v-for="child in category.children" :key="child.id">
            <NuxtLink
              :to="`/forum/${child.slug}`"
              class="rounded bg-white/10 px-2 py-1 text-xs text-slate-200 hover:bg-white/20"
            >
              {{ child.name }}
            </NuxtLink>
          </li>
        </ul>
        <p v-if="category.lastPost" class="mt-3 text-xs text-slate-400">
          最后回复：
          <NuxtLink :to="`/topics/${category.lastPost.topicId}`" class="text-brand-300">
            {{ category.lastPost.topicTitle }}
          </NuxtLink>
          · {{ category.lastPost.authorNickname }} · <LocalTime :value="category.lastPost.at" />
        </p>
        <p v-else class="mt-3 text-xs text-slate-500">暂无讨论</p>
      </div>
    </div>
  </article>
</template>
