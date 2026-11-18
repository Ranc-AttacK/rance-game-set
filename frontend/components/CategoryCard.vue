<script setup lang="ts">
import type { CategoryNode } from '~/lib/forum'

defineProps<{ category: CategoryNode }>()
</script>

<template>
  <article
    class="panel group relative overflow-hidden p-5 transition duration-200 hover:-translate-y-0.5 hover:border-gold-500/50 hover:shadow-lift"
  >
    <span class="rivet left-3 top-3" aria-hidden="true" />
    <span class="rivet bottom-3 right-3" aria-hidden="true" />
    <span
      class="pointer-events-none absolute -right-10 top-3.5 rotate-45 bg-gradient-to-r from-wine-700 to-wine-900 px-10 py-0.5 text-[10px] font-semibold tracking-[0.2em] text-gold-100 shadow"
      aria-hidden="true"
    >
      委托
    </span>

    <div class="flex items-start gap-4">
      <div class="medallion h-12 w-12 shrink-0">
        {{ category.icon || '💬' }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1 pr-6">
          <NuxtLink
            :to="`/forum/${category.slug}`"
            class="font-display text-lg font-semibold text-parchment-50 transition hover:text-gold-200"
          >
            {{ category.name }}
          </NuxtLink>
          <span class="badge">
            {{ category.topicCount }} 主题 · {{ category.postCount }} 帖子
          </span>
        </div>
        <p v-if="category.description" class="mt-1.5 text-sm leading-relaxed text-parchment-100/70">
          {{ category.description }}
        </p>
        <ul v-if="category.children.length" class="mt-3 flex flex-wrap gap-2">
          <li v-for="child in category.children" :key="child.id">
            <NuxtLink
              :to="`/forum/${child.slug}`"
              class="inline-flex items-center rounded-md border border-gold-700/30 bg-ink-950/40 px-2 py-1 text-xs text-parchment-100/75 transition hover:border-gold-500/50 hover:text-gold-100"
            >
              {{ child.name }}
            </NuxtLink>
          </li>
        </ul>
        <p v-if="category.lastPost" class="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-parchment-100/60">
          <span>最后回复：</span>
          <NuxtLink :to="`/topics/${category.lastPost.topicId}`" class="text-gold-300 hover:text-gold-100">
            {{ category.lastPost.topicTitle }}
          </NuxtLink>
          <span aria-hidden="true">·</span>
          <span>{{ category.lastPost.authorNickname }}</span>
          <span aria-hidden="true">·</span>
          <LocalTime :value="category.lastPost.at" />
        </p>
        <p v-else class="mt-3 text-xs text-parchment-100/55">暂无讨论</p>
      </div>
    </div>
  </article>
</template>
