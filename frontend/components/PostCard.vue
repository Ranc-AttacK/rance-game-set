<script setup lang="ts">
import type { PostView } from '~/lib/forum'

defineProps<{ post: PostView }>()
</script>

<template>
  <li class="rounded-lg border border-white/10 bg-white/5 p-4">
    <header class="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-400">
      <div class="flex items-center gap-2">
        <span class="font-mono text-slate-500">#{{ post.floor }}</span>
        <span class="font-medium text-slate-200">{{ post.author.nickname }}</span>
        <span v-if="post.replyToPostId" class="rounded bg-white/10 px-1.5 py-0.5">引用楼层</span>
      </div>
      <LocalTime :value="post.createdAt" />
    </header>
    <p class="mt-3 whitespace-pre-wrap break-words text-sm leading-7 text-slate-100">{{ post.content }}</p>
  </li>
</template>
