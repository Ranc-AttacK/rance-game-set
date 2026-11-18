<script setup lang="ts">
import type { PostView } from '~/lib/forum'

defineProps<{ post: PostView }>()
</script>

<template>
  <li class="panel relative overflow-hidden p-4">
    <header class="flex flex-wrap items-center justify-between gap-2 text-xs">
      <div class="flex flex-wrap items-center gap-2">
        <span class="floor-plate">#{{ post.floor }}</span>
        <span class="font-medium text-parchment-50">{{ post.author.nickname }}</span>
        <span v-if="post.replyToPostId" class="badge">引用楼层</span>
      </div>
      <span class="text-parchment-100/55">
        <LocalTime :value="post.createdAt" />
      </span>
    </header>
    <div class="divider-gold my-3" aria-hidden="true" />
    <p class="whitespace-pre-wrap break-words text-[15px] leading-7 text-parchment-100/90">{{ post.content }}</p>
  </li>
</template>
