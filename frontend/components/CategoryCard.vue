<script setup lang="ts">
import type { CategoryNode } from '~/lib/forum'

defineProps<{ category: CategoryNode }>()
</script>

<template>
  <article class="card card-hover flex flex-col items-center p-4 text-center sm:p-5">
    <span class="icon-tile h-11 w-11 shrink-0">
      <CategoryIcon :slug="category.slug" class="h-6 w-6" />
    </span>
    <NuxtLink
      :to="`/forum/${category.slug}`"
      class="mt-3 text-sm font-semibold text-foreground transition hover:text-sakura-600"
    >
      {{ category.name }}
    </NuxtLink>
    <p class="mt-1 text-[11px] text-muted">
      {{ category.topicCount }} 主题 · {{ category.postCount }} 帖子
    </p>
    <p v-if="category.description" class="mt-2 hidden text-xs leading-5 text-muted sm:block">
      {{ category.description }}
    </p>
    <ul v-if="category.children.length" class="mt-3 flex flex-wrap justify-center gap-1.5">
      <li v-for="child in category.children" :key="child.id">
        <NuxtLink
          :to="`/forum/${child.slug}`"
          class="chip px-2 py-0.5 text-[11px]"
        >
          {{ child.name }}
        </NuxtLink>
      </li>
    </ul>
    <p v-if="category.lastPost" class="mt-3 hidden w-full truncate text-[11px] text-muted sm:block">
      最后回复
      <NuxtLink :to="`/topics/${category.lastPost.topicId}`" class="text-foreground hover:underline">
        {{ category.lastPost.topicTitle }}
      </NuxtLink>
      · {{ category.lastPost.authorNickname }}
      · <LocalTime :value="category.lastPost.at" />
    </p>
    <p v-else class="mt-3 hidden text-[11px] text-muted sm:block">暂无讨论</p>
  </article>
</template>
